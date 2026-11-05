import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/main.dart'
    show isValidEmailAddress, isValidPasswordPair;

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
}
