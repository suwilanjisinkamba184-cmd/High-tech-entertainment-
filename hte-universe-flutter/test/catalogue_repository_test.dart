import 'package:flutter_test/flutter_test.dart';
import 'package:hte_universe/src/backend/catalogue_repository.dart';

void main() {
  group('HTE catalogue models', () {
    test('track parses nullable media metadata and duration', () {
      final track = HteTrack.fromJson({
        'id': 'track-1',
        'title': 'Golden Hour',
        'artist': 'L Junior ZM',
        'genre': 'Afrobeat',
        'audio_url': 'https://cdn.example.com/golden-hour.mp3',
        'source_url': 'https://cdn.example.com/golden-hour.mp3',
        'source_platform': 'direct',
        'cover_url': 'https://cdn.example.com/cover.jpg',
        'duration_seconds': 184,
      });

      expect(track.id, 'track-1');
      expect(track.title, 'Golden Hour');
      expect(track.artist, 'L Junior ZM');
      expect(track.durationSeconds, 184);
      expect(track.audioUrl, 'https://cdn.example.com/golden-hour.mp3');
    });

    test('video parses YouTube metadata and download URL', () {
      final video = HteVideo.fromJson({
        'id': 'video-1',
        'title': 'Studio Session',
        'video_url': 'https://youtu.be/abcdefghijk',
        'youtube_id': 'abcdefghijk',
        'thumbnail_url': 'https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg',
        'download_url': null,
      });

      expect(video.youtubeId, 'abcdefghijk');
      expect(video.videoUrl, 'https://youtu.be/abcdefghijk');
      expect(video.downloadUrl, isNull);
    });

    test('release and artist rows tolerate optional fields', () {
      final release = HteRelease.fromJson({
        'id': 'release-1',
        'title': 'The Golden Vocals',
        'slug': 'golden-vocals',
        'cover_url': null,
      });
      final artist = HteArtist.fromJson({
        'id': 'artist-1',
        'name': 'L Junior ZM',
        'slug': 'l-junior-zm',
        'bio': null,
        'image_url': null,
      });

      expect(release.title, 'The Golden Vocals');
      expect(release.slug, 'golden-vocals');
      expect(artist.name, 'L Junior ZM');
      expect(artist.bio, isNull);
    });
  });
}
