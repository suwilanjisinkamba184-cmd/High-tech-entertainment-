import 'dart:async';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class HtePlayback {
  final AudioPlayer player = AudioPlayer();
  final ValueNotifier<Map<String,dynamic>?> currentTrack = ValueNotifier(null);
  final ValueNotifier<bool> adPlaying = ValueNotifier(false);
  final ValueNotifier<List<Map<String,dynamic>>> queueView = ValueNotifier([]);
  List<Map<String,dynamic>> _queue = [];
  int _index = -1, _tracksSinceAd = 0;
  bool autoplay = true, shuffle = false, audioAdsEnabled = true, _handlingCompletion = false;
  int adEvery = 3;

  HtePlayback() {
    player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed && !_handlingCompletion) {
        _handlingCompletion = true;
        _advanceAfterCompletion().whenComplete(() => _handlingCompletion = false);
      }
    });
  }

  Future<void> playTrack(Map<String,dynamic> track, [List<Map<String,dynamic>>? source]) async {
    _queue = source == null || source.isEmpty ? [track] : List<Map<String,dynamic>>.from(source);
    _index = _queue.indexWhere((e) => e['id']?.toString() == track['id']?.toString());
    if (_index < 0) { _queue.insert(0, track); _index = 0; }
    queueView.value = List<Map<String,dynamic>>.from(_queue);
    _tracksSinceAd = 0;
    adPlaying.value = false;
    await _startTrack(track);
  }

  Future<void> _startTrack(Map<String,dynamic> track) async {
    final url = (track['audio_url'] ?? '').toString();
    if (!url.startsWith('http')) throw Exception('This track has no playable audio URL.');
    currentTrack.value = track;
    adPlaying.value = false;
    final title = (track['title'] ?? 'High-Tech Entertainment').toString();
    final artist = (track['artist'] ?? 'High-Tech Entertainment').toString();
    final artwork = Uri.tryParse((track['cover_url'] ?? '').toString());
    await player.setAudioSource(AudioSource.uri(
      Uri.parse(url),
      tag: MediaItem(
        id: (track['id'] ?? url).toString(),
        title: title,
        artist: artist,
        album: 'High-Tech Entertainment',
        artUri: artwork != null && artwork.hasScheme ? artwork : null,
      ),
    ));
    // Do not await play(): its Future completes only when playback ends.
    // Awaiting it here kept the completion handler locked for the whole track,
    // preventing autoplay and ad-to-track transitions.
    unawaited(player.play().catchError((error) {
      debugPrint('HTE audio playback error: $error');
    }));
    final user = Supabase.instance.client.auth.currentUser;
    if (user != null && track['id'] != null) {
      Supabase.instance.client.from('listening_history').insert({'user_id':user.id,'track_id':track['id']}).then((_) {}).catchError((_) {});
    }
  }

  Future<void> _advanceAfterCompletion() async {
    if (adPlaying.value) { adPlaying.value = false; await _playNextTrack(); return; }
    _tracksSinceAd++;
    if (audioAdsEnabled && _tracksSinceAd >= adEvery) {
      _tracksSinceAd = 0;
      if (await _playAd()) return;
    }
    await _playNextTrack();
  }

  Future<bool> _playAd() async {
    try {
      final result = await Supabase.instance.client.from('audio_ads')
        .select('id,title,audio_url,ends_at').eq('is_published',true)
        .or('starts_at.is.null,starts_at.lte.${DateTime.now().toUtc().toIso8601String()}')
        .order('priority',ascending:false).limit(10);
      final now = DateTime.now().toUtc();
      final ads = List<Map<String,dynamic>>.from(result).where((a) {
        final end = DateTime.tryParse((a['ends_at'] ?? '').toString());
        return (a['audio_url'] ?? '').toString().startsWith('http') && (end == null || end.isAfter(now));
      }).toList();
      if (ads.isEmpty) return false;
      final ad = ads[Random().nextInt(ads.length)];
      adPlaying.value = true;
      currentTrack.value = {'title':'Advertisement','artist':ad['title'] ?? 'High-Tech Entertainment'};
      await player.setAudioSource(AudioSource.uri(
        Uri.parse(ad['audio_url'].toString()),
        tag: MediaItem(
          id: (ad['id'] ?? ad['audio_url']).toString(),
          title: (ad['title'] ?? 'Advertisement').toString(),
          artist: 'High-Tech Entertainment',
          album: 'Sponsored audio',
        ),
      ));
      // Let the completion stream handle the end of the advert.
      unawaited(player.play().catchError((error) {
        debugPrint('HTE advert playback error: $error');
      }));
      final user = Supabase.instance.client.auth.currentUser;
      if (user != null) {
        Supabase.instance.client.from('ad_impressions').insert({'ad_id':ad['id'],'user_id':user.id}).then((_) {}).catchError((_) {});
      }
      return true;
    } catch (_) { adPlaying.value = false; return false; }
  }

  Future<void> _playNextTrack() async {
    if (_queue.isEmpty) return;
    if (shuffle && _queue.length > 1) {
      final choices = List<int>.generate(_queue.length,(i)=>i)..remove(_index);
      _index = choices[Random().nextInt(choices.length)];
    } else { _index++; }
    if (_index >= _queue.length) {
      if (!autoplay) { await player.stop(); return; }
      try {
        final data = await Supabase.instance.client.from('tracks').select()
          .eq('is_published',true).order('created_at',ascending:false).limit(40);
        final tracks = List<Map<String,dynamic>>.from(data)
          .where((t)=>(t['audio_url'] ?? '').toString().startsWith('http'))
          .where((t)=>t['id']?.toString()!=currentTrack.value?['id']?.toString()).toList();
        if (tracks.isEmpty) { await player.stop(); return; }
        _queue = tracks;
        _index = shuffle ? Random().nextInt(_queue.length) : 0;
        queueView.value = List<Map<String,dynamic>>.from(_queue);
      } catch (_) { await player.stop(); return; }
    }
    await _startTrack(_queue[_index]);
  }

  Future<void> next() async { await player.stop(); await _playNextTrack(); }
  Future<void> previous() async {
    if (player.position > const Duration(seconds:3)) { await player.seek(Duration.zero); return; }
    if (_queue.isEmpty) return;
    _index = (_index-1).clamp(0,_queue.length-1).toInt();
    await _startTrack(_queue[_index]);
  }
  Future<void> toggleShuffle() async { shuffle=!shuffle; await player.setShuffleModeEnabled(shuffle); }
  Future<void> setRepeat(bool value) => player.setLoopMode(value ? LoopMode.one : LoopMode.off);
}
