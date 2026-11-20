import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/main.dart' show isSpotifyUrl, spotifyEmbedUrl;

void main() {
  group('Spotify link handling', () {
    test('converts a Spotify track URL to an embed URL', () {
      expect(
        spotifyEmbedUrl('https://open.spotify.com/track/abc123?si=share'),
        'https://open.spotify.com/embed/track/abc123?utm_source=generator&theme=0',
      );
    });

    test('converts a Spotify URI to an embed URL', () {
      expect(
        spotifyEmbedUrl('spotify:album:AbC123'),
        'https://open.spotify.com/embed/album/AbC123?utm_source=generator&theme=0',
      );
    });

    test('rejects unsupported hosts and malformed identifiers', () {
      expect(spotifyEmbedUrl('https://example.com/track/abc123'), isNull);
      expect(spotifyEmbedUrl('https://open.spotify.com/track/abc-123'), isNull);
    });

    test('recognises Spotify URLs and URIs', () {
      expect(isSpotifyUrl('spotify:track:abc123'), isTrue);
      expect(isSpotifyUrl('https://open.spotify.com/track/abc123'), isTrue);
      expect(isSpotifyUrl('https://example.com/audio.mp3'), isFalse);
    });
  });
}
