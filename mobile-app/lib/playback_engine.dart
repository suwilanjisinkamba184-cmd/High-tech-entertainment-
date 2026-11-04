import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Counts completed music tracks independently of track selection.
///
/// Starting, skipping to, or manually selecting a track must not reset this
/// count. The counter resets only after the scheduled advert is due.
class CompletedTrackAdCounter {
  int _completedTracks = 0;

  int get completedTracks => _completedTracks;

  bool recordCompletion({required int interval, required bool enabled}) {
    if (!enabled || interval <= 0) return false;
    _completedTracks++;
    return _completedTracks >= interval;
  }

  void reset() => _completedTracks = 0;
}

/// Native audio playback for High-Tech Entertainment.
///
/// This is the application's single playback service. Always obtain it through
/// [HtePlayback.instance] (or the compatibility factory [HtePlayback()]);
/// never construct an AudioPlayer in a screen, widget, or tab. The one player
/// owns the queue, notification session, and playback state for the app.
/// Spotify URLs are intentionally handled by the Spotify embed screen; this
/// engine only accepts direct audio-file URLs that just_audio can stream.
class HtePlayback {
  /// Process-wide singleton shared by every screen and mini-player.
  static final HtePlayback instance = HtePlayback._();

  /// Preserve existing call sites while ensuring they all receive one service.
  factory HtePlayback() => instance;

  HtePlayback._() {
    player.errorStream.listen((error) {
      playbackError.value = _friendlyError(error);
      debugPrint('HTE audio player error: $error');
    });
    player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed && !_handlingCompletion) {
        _handlingCompletion = true;
        _advanceAfterCompletion()
            .catchError((Object error) {
              playbackError.value = _friendlyError(error);
              debugPrint('HTE queue advance failed: $error');
            })
            .whenComplete(() => _handlingCompletion = false);
      }
    });
  }

  /// The only AudioPlayer instance permitted in the HT Music application.
  final AudioPlayer player = AudioPlayer();
  final ValueNotifier<Map<String, dynamic>?> currentTrack = ValueNotifier(null);
  final ValueNotifier<bool> adPlaying = ValueNotifier(false);
  final ValueNotifier<List<Map<String, dynamic>>> queueView = ValueNotifier([]);
  final ValueNotifier<String?> playbackError = ValueNotifier(null);

  List<Map<String, dynamic>> _queue = [];
  int _index = -1;
  final CompletedTrackAdCounter _adCounter = CompletedTrackAdCounter();
  bool autoplay = true, shuffle = false, audioAdsEnabled = true;
  bool _handlingCompletion = false;
  // Only a successfully loaded music source can contribute one completion.
  // This prevents pause/resume, source replacement and advert completion
  // notifications from being mistaken for completed songs.
  bool _currentSourceIsMusic = false;
  bool _completionHandledForSource = true;
  static const int advertInterval = 8;
  static const String startupAdvertAsset = 'assets/AUD-20261103-WA0002.mp3';
  int adEvery = advertInterval;

  String _friendlyError(Object error) {
    final message = error.toString();
    if (message.contains('SocketException') || message.contains('Failed host lookup')) {
      return 'Cannot reach the audio server. Check your internet connection.';
    }
    if (message.contains('403') || message.contains('401')) {
      return 'This audio file is private or access has expired. Check its storage permissions.';
    }
    if (message.contains('404')) {
      return 'This audio file could not be found. Ask the administrator to upload it again.';
    }
    return 'Playback could not start. Check your connection or try another audio track.';
  }

  static Uri? directAudioUri(Object? value) {
    final raw = (value ?? '').toString().trim();
    final uri = Uri.tryParse(raw);
    if (uri == null ||
        (uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.host.isEmpty ||
        uri.host == 'open.spotify.com' ||
        uri.host.endsWith('.spotify.com')) {
      return null;
    }
    return uri;
  }

  Future<void> playTrack(
    Map<String, dynamic> track, [
    List<Map<String, dynamic>>? source,
  ]) async {
    if (adPlaying.value) return;
    _queue = source == null || source.isEmpty
        ? [track]
        : List<Map<String, dynamic>>.from(source);
    _index = _queue.indexWhere(
      (item) => item['id']?.toString() == track['id']?.toString(),
    );
    if (_index < 0) {
      _queue.insert(0, track);
      _index = 0;
    }
    queueView.value = List<Map<String, dynamic>>.from(_queue);
    // Do not reset the advert counter here: users may select tracks manually.
    // Only eight genuinely completed music tracks should trigger an advert.
    adPlaying.value = false;
    await _startTrack(track);
  }

  Future<void> _startTrack(Map<String, dynamic> track) async {
    final localPath = (track['local_path'] ?? '').toString().trim();
    final uri = localPath.isEmpty ? directAudioUri(track['audio_url']) : null;
    if (localPath.isEmpty && uri == null) {
      const message = 'This item does not contain a playable audio file.';
      playbackError.value = message;
      throw StateError(message);
    }

    playbackError.value = null;
    currentTrack.value = track;
    adPlaying.value = false;
    // Disarm completion handling while replacing the previous audio source.
    _currentSourceIsMusic = false;
    _completionHandledForSource = true;

    final title = (track['title'] ?? 'High-Tech Entertainment').toString();
    final artist = (track['artist'] ?? 'High-Tech Entertainment').toString();
    final artwork = Uri.tryParse((track['cover_url'] ?? '').toString());

    final mediaTag = MediaItem(
      id: (track['id'] ?? localPath).toString(),
      title: title,
      artist: artist,
      album: 'High-Tech Entertainment',
      artUri: artwork != null && artwork.hasScheme ? artwork : null,
    );
    await player.setAudioSource(
      localPath.isNotEmpty
          ? AudioSource.file(localPath, tag: mediaTag)
          : AudioSource.uri(uri!, tag: mediaTag),
      preload: true,
    );
    _currentSourceIsMusic = true;
    _completionHandledForSource = false;
    unawaited(_startPlayback());

    // Local files must remain playable without Supabase or internet access.
    // Listening history is best-effort metadata and must never fail playback.
    if (localPath.isEmpty) {
      try {
        final user = Supabase.instance.client.auth.currentUser;
        if (user != null && track['id'] != null) {
          Supabase.instance.client
              .from('listening_history')
              .insert({'user_id': user.id, 'track_id': track['id']})
              .then((_) {})
              .catchError((_) {});
        }
      } catch (error) {
        debugPrint('Skipping listening history while Supabase is unavailable: $error');
      }
    }
  }

  Future<void> _startPlayback() async {
    try {
      await player.play();
    } catch (error) {
      playbackError.value = _friendlyError(error);
      debugPrint('HTE playback start failed: $error');
    }
  }

  Future<void> _advanceAfterCompletion() async {
    if (_completionHandledForSource) return;
    if (!_currentSourceIsMusic && !adPlaying.value) return;
    _completionHandledForSource = true;
    _currentSourceIsMusic = false;
    if (adPlaying.value) {
      // The interval is consumed only after the advert has actually completed.
      _adCounter.reset();
      adPlaying.value = false;
      await _playNextTrack();
      return;
    }
    if (_adCounter.recordCompletion(interval: adEvery, enabled: audioAdsEnabled)) {
      final advertStarted = await _playAd();
      if (advertStarted) return;
      // Do not strand the queue when the bundled advert is missing or corrupt.
      _adCounter.reset();
      adPlaying.value = false;
      await _playNextTrack();
      return;
    }
    await _playNextTrack();
  }

  Future<bool> _playAd() async {
    try {
      adPlaying.value = true;
      _currentSourceIsMusic = false;
      _completionHandledForSource = false;
      playbackError.value = null;
      currentTrack.value = {
        'title': 'Advertisement',
        'artist': 'High-Tech Entertainment',
      };
      await player.setAudioSource(
        AudioSource.asset(
          startupAdvertAsset,
          tag: MediaItem(
            id: 'hte-audio-advert',
            title: 'Advertisement',
            artist: 'High-Tech Entertainment',
            album: 'High-Tech Entertainment',
          ),
        ),
        preload: true,
      );
      unawaited(_startPlayback());
      return true;
    } catch (error) {
      adPlaying.value = false;
      playbackError.value = _friendlyError(error);
      debugPrint('HTE bundled advert playback failed: $error');
      return false;
    }
  }

  Future<void> _playNextTrack() async {
    if (_queue.isEmpty) return;
    if (shuffle && _queue.length > 1) {
      final choices = List<int>.generate(_queue.length, (i) => i)..remove(_index);
      _index = choices[Random().nextInt(choices.length)];
    } else {
      _index++;
    }
    if (_index >= _queue.length) {
      if (!autoplay) {
        await player.stop();
        return;
      }
      try {
        final data = await Supabase.instance.client
            .from('tracks')
            .select()
            .eq('is_published', true)
            .order('created_at', ascending: false)
            .limit(40);
        final tracks = List<Map<String, dynamic>>.from(data)
            .where((track) => directAudioUri(track['audio_url']) != null)
            .where((track) =>
                track['id']?.toString() != currentTrack.value?['id']?.toString())
            .toList();
        if (tracks.isEmpty) {
          await player.stop();
          return;
        }
        _queue = tracks;
        _index = shuffle ? Random().nextInt(_queue.length) : 0;
        queueView.value = List<Map<String, dynamic>>.from(_queue);
      } catch (error) {
        playbackError.value = _friendlyError(error);
        await player.stop();
        return;
      }
    }
    await _startTrack(_queue[_index]);
  }

  Future<void> next() async {
    if (adPlaying.value) return;
    playbackError.value = null;
    await player.stop();
    await _playNextTrack();
  }

  Future<void> previous() async {
    if (adPlaying.value) return;
    if (player.position > const Duration(seconds: 3)) {
      await player.seek(Duration.zero);
      return;
    }
    if (_queue.isEmpty) return;
    _index = (_index - 1).clamp(0, _queue.length - 1).toInt();
    await _startTrack(_queue[_index]);
  }

  Future<void> toggleShuffle() async {
    shuffle = !shuffle;
    await player.setShuffleModeEnabled(shuffle);
  }

  Future<void> setRepeat(bool value) =>
      player.setLoopMode(value ? LoopMode.one : LoopMode.off);
}
