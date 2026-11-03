import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/playback_engine.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('HtePlayback factory always returns the shared service', () {
    expect(identical(HtePlayback.instance, HtePlayback()), isTrue);
    expect(identical(HtePlayback(), HtePlayback()), isTrue);
    expect(identical(HtePlayback.instance.player, HtePlayback().player), isTrue);
  });

  test('uses an advert interval of eight completed songs', () {
    expect(HtePlayback.advertInterval, 8);
  });

  group('Completed track advert scheduling', () {
    test('triggers only after eight completed tracks, then starts a new cycle', () {
      final counter = CompletedTrackAdCounter();

      for (var track = 1; track < 8; track++) {
        expect(
          counter.recordCompletion(interval: 8, enabled: true),
          isFalse,
          reason: 'Track $track must not trigger an advert',
        );
      }

      expect(counter.completedTracks, 7);
      expect(counter.recordCompletion(interval: 8, enabled: true), isTrue);
      expect(counter.completedTracks, 0);
      for (var track = 1; track < 8; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }
      expect(counter.recordCompletion(interval: 8, enabled: true), isTrue);
    });

    test('manual track selection does not reset completed-track count', () {
      final counter = CompletedTrackAdCounter();
      for (var track = 0; track < 5; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }

      // Selecting another track is intentionally not a counter operation.
      expect(counter.completedTracks, 5);
      for (var track = 0; track < 2; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }
      expect(counter.recordCompletion(interval: 8, enabled: true), isTrue);
    });

    test('disabled ads do not accumulate hidden completions', () {
      final counter = CompletedTrackAdCounter();
      for (var track = 0; track < 10; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: false), isFalse);
      }
      expect(counter.completedTracks, 0);
      for (var track = 0; track < 7; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }
      expect(counter.recordCompletion(interval: 8, enabled: true), isTrue);
    });

    test('invalid intervals never trigger adverts', () {
      final counter = CompletedTrackAdCounter();
      expect(counter.recordCompletion(interval: 0, enabled: true), isFalse);
      expect(counter.recordCompletion(interval: -1, enabled: true), isFalse);
      expect(counter.completedTracks, 0);
    });
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
