/// Shared URL validation rules. Provider web pages are never treated as raw
/// audio/video streams; they must use their official embed/player integration.
abstract final class HteMediaUrlPolicy {
  static const _audioExtensions = {
    '.mp3',
    '.m4a',
    '.aac',
    '.wav',
    '.flac',
    '.ogg',
    '.opus',
    '.m3u8',
  };

  static const _externalHosts = {
    'spotify.com',
    'spotify.link',
    'youtube.com',
    'youtu.be',
    'youtube-nocookie.com',
    'soundcloud.com',
    'audiomack.com',
    'boomplay.com',
  };

  static Uri? parseSecureUri(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    try {
      final uri = Uri.parse(value.trim());
      if (uri.scheme.toLowerCase() != 'https' ||
          uri.host.isEmpty ||
          uri.userInfo.isNotEmpty) {
        return null;
      }
      return uri;
    } on FormatException {
      return null;
    }
  }

  static bool isExternalProvider(Uri uri) {
    final host = uri.host.toLowerCase();
    return _externalHosts.any(
      (provider) => host == provider || host.endsWith('.$provider'),
    );
  }

  static Uri? directAudioUri(String? value) {
    final uri = parseSecureUri(value);
    if (uri == null || isExternalProvider(uri)) return null;

    final path = uri.path.toLowerCase();
    if (!_audioExtensions.any(path.endsWith)) return null;
    return uri;
  }

  static String? youtubeVideoId(String? value) {
    final uri = parseSecureUri(value);
    if (uri == null) return null;

    final host = uri.host.toLowerCase().replaceFirst('www.', '');
    final segments = uri.pathSegments.where((segment) => segment.isNotEmpty);
    final parts = segments.toList(growable: false);

    String? id;
    if (host == 'youtu.be') {
      id = parts.isEmpty ? null : parts.first;
    } else if (host == 'youtube.com' ||
        host == 'm.youtube.com' ||
        host == 'music.youtube.com' ||
        host == 'youtube-nocookie.com') {
      id = uri.queryParameters['v'];
      if (id == null &&
          parts.isNotEmpty &&
          {'embed', 'shorts', 'live'}.contains(parts.first)) {
        id = parts.length > 1 ? parts[1] : null;
      }
    }

    if (id == null || !RegExp(r'^[A-Za-z0-9_-]{11}$').hasMatch(id)) {
      return null;
    }
    return id;
  }

  static List<Uri> youtubeThumbnailCandidates(String? value) {
    final id = youtubeVideoId(value);
    if (id == null) return const [];
    return [
      Uri.https('img.youtube.com', '/vi/$id/hqdefault.jpg'),
      Uri.https('img.youtube.com', '/vi/$id/sddefault.jpg'),
      Uri.https('img.youtube.com', '/vi/$id/mqdefault.jpg'),
      Uri.https('img.youtube.com', '/vi/$id/default.jpg'),
      Uri.https('img.youtube.com', '/vi/$id/maxresdefault.jpg'),
    ];
  }
}
