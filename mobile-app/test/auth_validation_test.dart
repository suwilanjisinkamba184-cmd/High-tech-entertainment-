import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
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
  });
}
