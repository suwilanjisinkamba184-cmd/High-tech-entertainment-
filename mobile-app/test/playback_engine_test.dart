import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/playback_engine.dart';

void main() {
  test('HtePlayback factory always returns the shared service', () {
    expect(identical(HtePlayback.instance, HtePlayback()), isTrue);
    expect(identical(HtePlayback(), HtePlayback()), isTrue);
    expect(identical(HtePlayback.instance.player, HtePlayback().player), isTrue);
  });

  group('Direct audio URL validation', () {
    test('accepts a public Supabase MP3 URL', () {
      final uri = HtePlayback.directAudioUri(
        'https://project.supabase.co/storage/v1/object/public/hte-audio/song.mp3',
      );
      expect(uri, isNotNull);
      expect(uri!.path, endsWith('.mp3'));
    });

    test('rejects Spotify embed URLs for native audio playback', () {
      expect(
        HtePlayback.directAudioUri(
          'https://open.spotify.com/embed/track/3t9ly5CXTe0oWrIIXQ6jF8',
        ),
        isNull,
      );
    });

    test('rejects malformed and non-web URLs', () {
      expect(HtePlayback.directAudioUri(''), isNull);
      expect(HtePlayback.directAudioUri('spotify:track:abc123'), isNull);
      expect(HtePlayback.directAudioUri('file:///music/song.mp3'), isNull);
    });
  });
}
