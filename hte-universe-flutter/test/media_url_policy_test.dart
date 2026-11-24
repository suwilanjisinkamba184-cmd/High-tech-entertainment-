import 'package:flutter_test/flutter_test.dart';
import 'package:hte_universe/src/media/media_url_policy.dart';

void main() {
  group('HTE media URL policy', () {
    test('accepts secure direct audio files', () {
      expect(
        HteMediaUrlPolicy.directAudioUri(
          'https://cdn.example.com/music/track.mp3?token=temporary',
        ),
        isNotNull,
      );
    });

    test('rejects cleartext and malformed media URLs', () {
      expect(
        HteMediaUrlPolicy.directAudioUri('http://cdn.example.com/track.mp3'),
        isNull,
      );
      expect(HteMediaUrlPolicy.directAudioUri('not a url'), isNull);
    });

    test('does not treat Spotify or YouTube pages as direct audio', () {
      expect(
        HteMediaUrlPolicy.directAudioUri(
          'https://open.spotify.com/track/1234567890123456789012',
        ),
        isNull,
      );
      expect(
        HteMediaUrlPolicy.directAudioUri(
          'https://www.youtube.com/watch?v=abcdefghijk',
        ),
        isNull,
      );
    });

    test('accepts canonical YouTube host variants only', () {
      expect(
        HteMediaUrlPolicy.youtubeVideoId(
          'https://www.youtube.com/watch?v=abcdefghijk',
        ),
        'abcdefghijk',
      );
      expect(
        HteMediaUrlPolicy.youtubeVideoId(
          'https://youtube.www.com/watch?v=abcdefghijk',
        ),
        isNull,
      );
      expect(
        HteMediaUrlPolicy.youtubeVideoId(
          'https://notyoutube.com/watch?v=abcdefghijk',
        ),
        isNull,
      );
    });

    test('extracts YouTube IDs and returns resilient poster candidates', () {
      const videoUrl = 'https://youtu.be/abcdefghijk';
      expect(HteMediaUrlPolicy.youtubeVideoId(videoUrl), 'abcdefghijk');
      expect(
        HteMediaUrlPolicy.youtubeThumbnailCandidates(videoUrl).map((uri) => uri.path),
        [
          '/vi/abcdefghijk/hqdefault.jpg',
          '/vi/abcdefghijk/sddefault.jpg',
          '/vi/abcdefghijk/mqdefault.jpg',
          '/vi/abcdefghijk/default.jpg',
          '/vi/abcdefghijk/maxresdefault.jpg',
        ],
      );
    });

    test('rejects malformed YouTube identifiers', () {
      expect(
        HteMediaUrlPolicy.youtubeVideoId(
          'https://www.youtube.com/watch?v=short',
        ),
        isNull,
      );
      expect(
        HteMediaUrlPolicy.youtubeThumbnailCandidates('https://example.com/video'),
        isEmpty,
      );
    });
  });
}
