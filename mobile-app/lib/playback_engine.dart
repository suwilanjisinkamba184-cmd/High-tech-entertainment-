import 'dart:async';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Shared playback controller for mini-player, full player and queue.
class HtePlayback {
  final AudioPlayer player = AudioPlayer();
  final ValueNotifier<Map<String, dynamic>?> currentTrack = ValueNotifier(null);
  final ValueNotifier<bool> adPlaying = ValueNotifier(false);
  final ValueNotifier<List<Map<String, dynamic>>> queueView = ValueNotifier([]);
  final ValueNotifier<String?> playbackError = ValueNotifier(null);
  List<Map<String, dynamic>> _queue = [];
  int _index = -1, _tracksSinceAd = 0, _requestId = 0;
  bool autoplay = true, shuffle = false, _handlingCompletion = false;
  static const int _adInterval = 3;

  HtePlayback() {
    player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed && !_handlingCompletion) {
        _handlingCompletion = true;
        _advanceAfterCompletion().catchError((Object e) {
          debugPrint('HTE queue advance failed: $e');
          playbackError.value = 'Could not continue playback. Try another track.';
        }).whenComplete(() => _handlingCompletion = false);
      }
    });
    player.playbackEventStream.listen((_) {}, onError: (Object e, StackTrace st) {
      debugPrint('HTE playback stream error: $e');
      playbackError.value = 'Audio stream interrupted. Check your connection.';
    });
  }

  Future<void> playTrack(Map<String, dynamic> track, [List<Map<String, dynamic>>? source]) async {
    ++_requestId;
    _queue = source == null || source.isEmpty ? [track] : List<Map<String, dynamic>>.from(source);
    _index = _queue.indexWhere((e) => e['id']?.toString() == track['id']?.toString());
    if (_index < 0) { _queue.insert(0, track); _index = 0; }
    queueView.value = List<Map<String, dynamic>>.unmodifiable(_queue);
    _tracksSinceAd = 0;
    adPlaying.value = false;
    playbackError.value = null;
    await _startTrack(track, _requestId);
  }

  Future<void> _startTrack(Map<String, dynamic> track, int request) async {
    final raw = (track['audio_url'] ?? '').toString().trim();
    final uri = Uri.tryParse(raw);
    if (uri == null || !['http', 'https'].contains(uri.scheme)) {
      throw Exception('This track does not have a valid playable audio URL.');
    }
    currentTrack.value = track;
    adPlaying.value = false;
    playbackError.value = null;
    final art = Uri.tryParse((track['cover_url'] ?? '').toString());
    await player.setAudioSource(AudioSource.uri(uri, tag: MediaItem(
      id: (track['id'] ?? raw).toString(),
      title: (track['title'] ?? 'High-Tech Entertainment').toString(),
      artist: (track['artist'] ?? 'High-Tech Entertainment').toString(),
      album: 'High-Tech Entertainment',
      artUri: art != null && art.hasScheme ? art : null,
    )));
    if (request != _requestId) return;
    unawaited(player.play().catchError((Object e) {
      debugPrint('HTE audio playback error: $e');
      playbackError.value = 'This track could not be played. Try another track.';
    }));
    final user = Supabase.instance.client.auth.currentUser;
    if (user != null && track['id'] != null) {
      unawaited(Supabase.instance.client.from('listening_history').insert({
        'user_id': user.id, 'track_id': track['id'],
      }).then((_) {}).catchError((_) {}));
    }
  }

  Future<void> _advanceAfterCompletion() async {
    if (adPlaying.value) { adPlaying.value = false; await _playNextTrack(); return; }
    _tracksSinceAd++;
    // Campaign availability and publication are controlled by the admin system.
    if (_tracksSinceAd >= _adInterval) {
      _tracksSinceAd = 0;
      if (await _playAd()) return;
    }
    await _playNextTrack();
  }

  Future<bool> _playAd() async {
    try {
      final now = DateTime.now().toUtc();
      final result = await Supabase.instance.client.from('audio_ads')
        .select('id,title,audio_url,starts_at,ends_at').eq('is_published', true)
        .or('starts_at.is.null,starts_at.lte.${now.toIso8601String()}')
        .order('priority', ascending: false).limit(10);
      final ads = List<Map<String, dynamic>>.from(result).where((a) {
        final uri = Uri.tryParse((a['audio_url'] ?? '').toString());
        final end = DateTime.tryParse((a['ends_at'] ?? '').toString());
        return uri != null && ['http', 'https'].contains(uri.scheme) && (end == null || end.isAfter(now));
      }).toList();
      if (ads.isEmpty) return false;
      final ad = ads[Random().nextInt(ads.length)];
      final uri = Uri.parse(ad['audio_url'].toString());
      adPlaying.value = true;
      currentTrack.value = {'title': ad['title'] ?? 'Advertisement', 'artist': 'Sponsored • High-Tech Entertainment'};
      await player.setAudioSource(AudioSource.uri(uri, tag: MediaItem(
        id: (ad['id'] ?? uri.toString()).toString(),
        title: (ad['title'] ?? 'Advertisement').toString(),
        artist: 'High-Tech Entertainment', album: 'Sponsored audio',
      )));
      unawaited(player.play().catchError((Object e) {
        debugPrint('HTE promotional audio error: $e');
        adPlaying.value = false;
      }));
      final user = Supabase.instance.client.auth.currentUser;
      if (user != null && ad['id'] != null) {
        unawaited(Supabase.instance.client.from('ad_impressions').insert({
          'ad_id': ad['id'], 'user_id': user.id,
        }).then((_) {}).catchError((_) {}));
      }
      return true;
    } catch (e) {
      debugPrint('HTE promotional audio unavailable: $e');
      adPlaying.value = false;
      return false;
    }
  }

  Future<void> _playNextTrack() async {
    if (_queue.isEmpty) return;
    if (shuffle && _queue.length > 1) {
      final choices = List<int>.generate(_queue.length, (i) => i)..remove(_index);
      _index = choices[Random().nextInt(choices.length)];
    } else { _index++; }
    if (_index >= _queue.length) {
      if (!autoplay) { await player.pause(); return; }
      try {
        final data = await Supabase.instance.client.from('tracks').select()
          .eq('is_published', true).order('created_at', ascending: false).limit(40);
        final tracks = List<Map<String, dynamic>>.from(data).where((t) {
          final uri = Uri.tryParse((t['audio_url'] ?? '').toString());
          return uri != null && ['http', 'https'].contains(uri.scheme);
        }).where((t) => t['id']?.toString() != currentTrack.value?['id']?.toString()).toList();
        if (tracks.isEmpty) { await player.pause(); return; }
        _queue = tracks;
        _index = shuffle ? Random().nextInt(_queue.length) : 0;
        queueView.value = List<Map<String, dynamic>>.unmodifiable(_queue);
      } catch (e) {
        debugPrint('HTE autoplay load failed: $e');
        playbackError.value = 'Autoplay could not load more music.';
        await player.pause(); return;
      }
    }
    final request = ++_requestId;
    await _startTrack(_queue[_index], request);
  }

  Future<void> next() async { ++_requestId; await player.pause(); await _playNextTrack(); }
  Future<void> previous() async {
    if (player.position > const Duration(seconds: 3)) { await player.seek(Duration.zero); return; }
    if (_queue.isEmpty) return;
    _index = (_index - 1).clamp(0, _queue.length - 1).toInt();
    final request = ++_requestId;
    await _startTrack(_queue[_index], request);
  }
  Future<void> toggleShuffle() async { shuffle = !shuffle; await player.setShuffleModeEnabled(shuffle); }
  Future<void> setRepeat(bool value) => player.setLoopMode(value ? LoopMode.one : LoopMode.off);
}
