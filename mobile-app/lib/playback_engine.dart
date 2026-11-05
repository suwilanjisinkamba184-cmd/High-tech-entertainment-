import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

  void restore(int value) {
    // A stored count of eight means an advert was due when the app last
    // stopped. Clamp to seven so the next completed track triggers it.
    _completedTracks = value.clamp(0, 7).toInt();
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
  final List<Map<String, dynamic>> _manualQueue = [];
  int _index = -1;
  bool _playingManualQueueTrack = false;
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
  static const String _adCounterPreferenceKey = 'hte_completed_tracks_since_ad';

  Future<void> restoreAdCounter() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      _adCounter.restore(preferences.getInt(_adCounterPreferenceKey) ?? 0);
    } catch (error) {
      debugPrint('HTE advert counter could not be restored: $error');
    }
  }

  Future<void> _persistAdCounter() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      await preferences.setInt(_adCounterPreferenceKey, _adCounter.completedTracks);
    } catch (error) {
      debugPrint('HTE advert counter could not be saved: $error');
    }
  }

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

  void _publishQueue() {
    final visible = <Map<String, dynamic>>[];
    final current = currentTrack.value;
    if (current != null && !adPlaying.value) visible.add(current);
    visible.addAll(_manualQueue);
    if (_index >= 0 && _index + 1 < _queue.length) {
      visible.addAll(_queue.skip(_index + 1));
    }
    queueView.value = visible;
  }

  Future<void> playTrack(
    Map<String, dynamic> track, [
    List<Map<String, dynamic>>? source,
  ]) async {
    if (adPlaying.value) return;
    _queue = source == null || source.isEmpty
        ? [track]
        : List<Map<String, dynamic>>.from(source);
    _manualQueue.clear();
    _playingManualQueueTrack = false;
    _index = _queue.indexWhere(
      (item) => item['id']?.toString() == track['id']?.toString(),
    );
    if (_index < 0) {
      _queue.insert(0, track);
      _index = 0;
    }
    // Do not reset the advert counter here: users may select tracks manually.
    // Only eight genuinely completed music tracks should trigger an advert.
    adPlaying.value = false;
    await _startTrack(track);
  }

  /// Prioritise a manually selected track ahead of the source playlist/autoplay.
  void playNext(Map<String, dynamic> track) {
    if (adPlaying.value) return;
    final id = track['id']?.toString();
    if (id != null && id == currentTrack.value?['id']?.toString()) return;
    if (_queue.isEmpty || _index < 0) {
      unawaited(playTrack(track));
      return;
    }
    if (id != null) {
      var sourceIndex = -1;
      for (var i = _index + 1; i < _queue.length; i++) {
        if (_queue[i]['id']?.toString() == id) {
          sourceIndex = i;
          break;
        }
      }
      if (sourceIndex >= 0) _queue.removeAt(sourceIndex);
      _manualQueue.removeWhere((item) => item['id']?.toString() == id);
    }
    _manualQueue.add(Map<String, dynamic>.from(track));
    _publishQueue();
  }

  /// Append a track after the current source queue.
  void addToQueue(Map<String, dynamic> track) {
    if (adPlaying.value) return;
    if (_queue.isEmpty || _index < 0) {
      unawaited(playTrack(track));
      return;
    }
    final id = track['id']?.toString();
    if (id != null &&
        (_queue.any((item) => item['id']?.toString() == id) ||
         _manualQueue.any((item) => item['id']?.toString() == id))) {
      return;
    }
    _queue.add(Map<String, dynamic>.from(track));
    _publishQueue();
  }

  void clearQueue() {
    _queue.clear();
    _manualQueue.clear();
    _index = -1;
    _playingManualQueueTrack = false;
    queueView.value = [];
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
    _publishQueue();
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
      // A failed sponsored clip must never leave the listener trapped on an
      // endless advert screen. Recover the queue just as for a bad advert file.
      if (adPlaying.value) {
        adPlaying.value = false;
        _currentSourceIsMusic = false;
        _completionHandledForSource = true;
        _adCounter.reset();
        await _persistAdCounter();
        try {
          await _playNextTrack();
        } catch (recoveryError) {
          playbackError.value = _friendlyError(recoveryError);
          debugPrint('HTE queue recovery after advert failure failed: $recoveryError');
        }
      }
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
      await _persistAdCounter();
      adPlaying.value = false;
      await _playNextTrack();
      return;
    }
    final advertDue = _adCounter.recordCompletion(interval: adEvery, enabled: audioAdsEnabled);
    await _persistAdCounter();
    if (advertDue) {
      final advertStarted = await _playAd();
      if (advertStarted) return;
      // Do not strand the queue when the bundled advert is missing or corrupt.
      _adCounter.reset();
      await _persistAdCounter();
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
      _publishQueue();
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
    if (_manualQueue.isNotEmpty) {
      final nextTrack = _manualQueue.removeAt(0);
      _playingManualQueueTrack = true;
      _publishQueue();
      await _startTrack(nextTrack);
      return;
    }
    if (_playingManualQueueTrack) _playingManualQueueTrack = false;
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
