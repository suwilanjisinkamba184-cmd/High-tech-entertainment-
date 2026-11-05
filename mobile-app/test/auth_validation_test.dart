import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/playback_engine.dart' show CompletedTrackAdCounter;
import 'package:high_tech_entertainment/main.dart'
    show friendlyErrorMessage, isValidEmailAddress, isValidPasswordPair;

void main() {
  group('Account input validation', () {
    test('accepts valid email addresses and trims surrounding spaces', () {
      expect(isValidEmailAddress('listener@example.com'), isTrue);
      expect(isValidEmailAddress('  listener@example.com  '), isTrue);
    });

    test('rejects malformed or incomplete email addresses', () {
      expect(isValidEmailAddress(''), isFalse);
      expect(isValidEmailAddress('listener'), isFalse);
      expect(isValidEmailAddress('listener@'), isFalse);
      expect(isValidEmailAddress('listener@example'), isFalse);
      expect(isValidEmailAddress('listener @example.com'), isFalse);
    });

    test('requires matching passwords with at least eight characters', () {
      expect(isValidPasswordPair('HighTech123', 'HighTech123'), isTrue);
      expect(isValidPasswordPair('short', 'short'), isFalse);
      expect(isValidPasswordPair('HighTech123', 'HighTech124'), isFalse);
    });
  });

  group('Safe error messages', () {
    test('turns network failures into a friendly offline message', () {
      final message = friendlyErrorMessage(const SocketException('Failed host lookup'));
      expect(message, contains('No internet connection'));
      expect(message, isNot(contains('SocketException')));
    });

    test('explains missing media without exposing raw HTTP details', () {
      final message = friendlyErrorMessage(const HttpException('Download failed (HTTP 404).'));
      expect(message, contains('media file is no longer available'));
      expect(message, isNot(contains('404')));
    });

    test('explains unsupported file formats safely', () {
      expect(
        friendlyErrorMessage(const FormatException('raw parser detail')),
        contains('format is not supported'),
      );
    });

    test('does not expose raw storage permission errors', () {
      final message = friendlyErrorMessage(Exception('new row violates row-level security policy'));
      expect(message, contains('not permitted for the current account'));
      expect(message, isNot(contains('row-level security')));
    });

    test('explains oversized uploads in plain language', () {
      expect(
        friendlyErrorMessage(Exception('Payload too large')),
        contains('file is too large'),
      );
      expect(
        friendlyErrorMessage(const FormatException('Choose a profile image smaller than 5 MB.')),
        contains('profile image smaller than 5 MB'),
      );
    });
  });
  group('Scheduled audio advertisement counter', () {
    test('triggers only on the eighth completed music track', () {
      final counter = CompletedTrackAdCounter();
      for (var track = 1; track <= 7; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }
      expect(counter.completedTracks, 7);
      expect(counter.recordCompletion(interval: 8, enabled: true), isTrue);
    });

    test('does not count completions while ads are disabled', () {
      final counter = CompletedTrackAdCounter();
      for (var track = 0; track < 12; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: false), isFalse);
      }
      expect(counter.completedTracks, 0);
    });

    test('resets after an advert has completed', () {
      final counter = CompletedTrackAdCounter();
      for (var track = 0; track < 8; track++) {
        counter.recordCompletion(interval: 8, enabled: true);
      }
      expect(counter.completedTracks, 8);
      counter.reset();
      expect(counter.completedTracks, 0);
      for (var track = 0; track < 7; track++) {
        expect(counter.recordCompletion(interval: 8, enabled: true), isFalse);
      }
    });
  });

}
