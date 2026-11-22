import 'media_models.dart';

/// Platform adapters implement these contracts. Feature widgets must not depend
/// directly on Android Media3, iOS AVFoundation or Windows media APIs.
abstract interface class HteAudioPlaybackPort {
  Stream<HtePlaybackSnapshot> get snapshots;

  Future<void> setQueue(
    List<HteMediaItem> items, {
    int startIndex = 0,
  });

  Future<void> play();
  Future<void> pause();
  Future<void> seek(Duration position);
  Future<void> skipNext();
  Future<void> skipPrevious();
  Future<void> setShuffle(bool enabled);
  Future<void> setRepeatMode(HteRepeatMode mode);
  Future<void> dispose();
}

enum HteRepeatMode { off, all, one }

abstract interface class HteVideoPlaybackPort {
  Future<void> open(HteMediaItem item);
  Future<void> close();
  Future<void> retry();
}

abstract interface class HteOfflineMediaPort {
  Stream<HteDownloadProgress> get progress;

  Future<void> download(HteMediaItem item);
  Future<void> cancel(String mediaId);
  Future<void> remove(String mediaId);
  Future<bool> isAvailable();
}
