class HteMediaItem {
  const HteMediaItem({
    required this.id,
    required this.title,
    required this.artist,
    required this.sourceUrl,
    this.artworkUrl,
    this.sourcePlatform = 'direct',
  });

  final String id;
  final String title;
  final String artist;
  final String sourceUrl;
  final String? artworkUrl;
  final String sourcePlatform;
}

class HtePlaybackSnapshot {
  const HtePlaybackSnapshot({
    this.currentItem,
    this.isPlaying = false,
    this.isBuffering = false,
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.errorCode,
  });

  final HteMediaItem? currentItem;
  final bool isPlaying;
  final bool isBuffering;
  final Duration position;
  final Duration duration;
  final String? errorCode;
}

class HteDownloadProgress {
  const HteDownloadProgress({
    required this.mediaId,
    required this.bytesReceived,
    required this.totalBytes,
    required this.status,
    this.errorCode,
  });

  final String mediaId;
  final int bytesReceived;
  final int totalBytes;
  final HteDownloadStatus status;
  final String? errorCode;

  double? get fraction {
    if (totalBytes <= 0) return null;
    return (bytesReceived / totalBytes).clamp(0, 1).toDouble();
  }
}

enum HteDownloadStatus {
  queued,
  downloading,
  paused,
  completed,
  failed,
  cancelled,
}
