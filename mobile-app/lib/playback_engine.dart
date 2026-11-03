import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

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
  int _index = -1, _tracksSinceAd = 0;
  bool autoplay = true, shuffle = false, audioAdsEnabled = true;
  bool _handlingCompletion = false;
  bool _playingStartupAd = false;
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
    return 'Playback could not start. Check that this track is a valid MP3, AAC or supported audio file. ($message)';
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
    _tracksSinceAd = 0;
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

    final title = (track['title'] ?? 'High-Tech Entertainment').toString();
    final artist = (track['artist'] ?? 'High-Tech Entertainment').toString();
    final artwork = Uri.tryParse((track['cover_url'] ?? '').toString());

    // setAudioSource waits for the media source to be prepared. Playback itself
    // must remain unawaited here because its Future completes when the track ends.
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
    unawaited(_startPlayback());

    final user = Supabase.instance.client.auth.currentUser;
    if (user != null && track['id'] != null && localPath.isEmpty) {
      Supabase.instance.client
          .from('listening_history')
          .insert({'user_id': user.id, 'track_id': track['id']})
          .then((_) {})
          .catchError((_) {});
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

  /// Plays the bundled advert. Because this is an app asset, it works on the
  /// very first launch without Wi-Fi, mobile data, Supabase or a cached file.
  /// The splash remains visible until the complete audio source finishes.
  Future<void> playStartupAd() async {
    if (!audioAdsEnabled) return;
    _playingStartupAd = true;
    adPlaying.value = true;
    currentTrack.value = {
      'title': 'Advertisement',
      'artist': 'High-Tech Entertainment',
    };
    try {
      await player.setAudioSource(
        AudioSource.asset(startupAdvertAsset),
        preload: true,
      );
      await player.play();
    } catch (error) {
      playbackError.value = _friendlyError(error);
      debugPrint('HTE bundled startup advert failed: $error');
      rethrow; // Never silently bypass the required startup advertisement.
    } finally {
      _playingStartupAd = false;
      adPlaying.value = false;
      currentTrack.value = null;
    }
  }

  Future<void> _advanceAfterCompletion() async {
    // Startup audio is deliberately not part of the music queue.
    if (_playingStartupAd) return;
    if (adPlaying.value) {
      adPlaying.value = false;
      await _playNextTrack();
      return;
    }
    _tracksSinceAd++;
    if (audioAdsEnabled && _tracksSinceAd >= adEvery) {
      _tracksSinceAd = 0;
      final advertStarted = await _playAd();
      if (advertStarted) return;
      // Do not continue to the next song when a mandatory advert fails.
      await player.stop();
      return;
    }
    await _playNextTrack();
  }

  Future<bool> _playAd() async {
    try {
      adPlaying.value = true;
      playbackError.value = null;
      currentTrack.value = {
        'title': 'Advertisement',
        'artist': 'High-Tech Entertainment',
      };
      // Use the same bundled advert after every eight completed songs.
      // This also works offline and never creates another AudioPlayer.
      await player.setAudioSource(
        AudioSource.asset(startupAdvertAsset),
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
