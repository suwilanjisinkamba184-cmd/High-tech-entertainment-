import 'package:supabase_flutter/supabase_flutter.dart';

class HteTrack {
  const HteTrack({
    required this.id,
    required this.title,
    required this.artist,
    this.genre,
    this.audioUrl,
    this.sourceUrl,
    this.sourcePlatform = 'direct',
    this.coverUrl,
    this.durationSeconds,
  });

  final String id;
  final String title;
  final String artist;
  final String? genre;
  final String? audioUrl;
  final String? sourceUrl;
  final String sourcePlatform;
  final String? coverUrl;
  final int? durationSeconds;

  factory HteTrack.fromJson(Map<String, dynamic> json) => HteTrack(
        id: json['id']?.toString() ?? '',
        title: json['title']?.toString() ?? 'Untitled track',
        artist: json['artist']?.toString() ?? 'High-Tech Entertainment',
        genre: json['genre']?.toString(),
        audioUrl: json['audio_url']?.toString(),
        sourceUrl: json['source_url']?.toString(),
        sourcePlatform: json['source_platform']?.toString() ?? 'direct',
        coverUrl: json['cover_url']?.toString(),
        durationSeconds: _intValue(json['duration_seconds']),
      );
}

class HteVideo {
  const HteVideo({
    required this.id,
    required this.title,
    required this.videoUrl,
    this.description,
    this.artist,
    this.thumbnailUrl,
    this.youtubeId,
    this.sourcePlatform,
    this.downloadUrl,
  });

  final String id;
  final String title;
  final String videoUrl;
  final String? description;
  final String? artist;
  final String? thumbnailUrl;
  final String? youtubeId;
  final String? sourcePlatform;
  final String? downloadUrl;

  factory HteVideo.fromJson(Map<String, dynamic> json) => HteVideo(
        id: json['id']?.toString() ?? '',
        title: json['title']?.toString() ?? 'Untitled video',
        videoUrl: json['video_url']?.toString() ?? '',
        description: json['description']?.toString(),
        artist: json['artist']?.toString(),
        thumbnailUrl: json['thumbnail_url']?.toString(),
        youtubeId: json['youtube_id']?.toString(),
        sourcePlatform: json['source_platform']?.toString(),
        downloadUrl: json['download_url']?.toString(),
      );
}

class HteRelease {
  const HteRelease({
    required this.id,
    required this.title,
    required this.slug,
    this.coverUrl,
    this.releaseDate,
    this.description,
  });

  final String id;
  final String title;
  final String slug;
  final String? coverUrl;
  final String? releaseDate;
  final String? description;

  factory HteRelease.fromJson(Map<String, dynamic> json) => HteRelease(
        id: json['id']?.toString() ?? '',
        title: json['title']?.toString() ?? 'Untitled release',
        slug: json['slug']?.toString() ?? '',
        coverUrl: json['cover_url']?.toString(),
        releaseDate: json['release_date']?.toString(),
        description: json['description']?.toString(),
      );
}

class HteArtist {
  const HteArtist({
    required this.id,
    required this.name,
    required this.slug,
    this.bio,
    this.imageUrl,
  });

  final String id;
  final String name;
  final String slug;
  final String? bio;
  final String? imageUrl;

  factory HteArtist.fromJson(Map<String, dynamic> json) => HteArtist(
        id: json['id']?.toString() ?? '',
        name: json['name']?.toString() ?? 'HTE artist',
        slug: json['slug']?.toString() ?? '',
        bio: json['bio']?.toString(),
        imageUrl: json['image_url']?.toString(),
      );
}

class HteCatalogue {
  const HteCatalogue({
    this.tracks = const [],
    this.videos = const [],
    this.releases = const [],
    this.artists = const [],
  });

  final List<HteTrack> tracks;
  final List<HteVideo> videos;
  final List<HteRelease> releases;
  final List<HteArtist> artists;

  static const empty = HteCatalogue();
}

/// Read-only public catalogue queries. Supabase RLS remains authoritative:
/// public tracks/videos/releases are filtered by publication status, while
/// artist visibility follows the existing artist policy.
class HteCatalogueRepository {
  HteCatalogueRepository({SupabaseClient? client})
      : _client = client ?? Supabase.instance.client;

  final SupabaseClient _client;

  Future<HteCatalogue> load() async {
    final tracks = await _client
        .from('tracks')
        .select('id,title,artist,genre,audio_url,source_url,source_platform,cover_url,duration_seconds')
        .eq('is_published', true);
    final videos = await _client
        .from('videos')
        .select('id,title,description,artist,video_url,thumbnail_url,youtube_id,source_platform,download_url')
        .eq('is_published', true);
    final releases = await _client
        .from('releases')
        .select('id,title,slug,cover_url,release_date,description')
        .eq('is_published', true);
    final artists = await _client
        .from('artists')
        .select('id,name,slug,bio,image_url');

    return HteCatalogue(
      tracks: _mapRows(tracks, HteTrack.fromJson),
      videos: _mapRows(videos, HteVideo.fromJson),
      releases: _mapRows(releases, HteRelease.fromJson),
      artists: _mapRows(artists, HteArtist.fromJson),
    );
  }
}

List<T> _mapRows<T>(
  List<dynamic> rows,
  T Function(Map<String, dynamic>) factory,
) =>
    rows
        .map((row) => factory(Map<String, dynamic>.from(row as Map)))
        .toList(growable: false);

int? _intValue(dynamic value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}
