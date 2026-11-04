import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:high_tech_entertainment/user_facing_errors.dart';

void main() {
  group('hteFriendlyError', () {
    test('shows a clear offline message for socket failures', () {
      expect(
        hteFriendlyError(const SocketException('Failed host lookup: api.example.com')),
        'No internet connection. Check your connection and try again.',
      );
    });

    test('shows a clear offline message for timeouts', () {
      expect(
        hteFriendlyError(TimeoutException('request timed out')),
        'No internet connection. Check your connection and try again.',
      );
    });

    test('does not expose raw backend exception details', () {
      const fallback = 'We could not load your music right now.';
      final message = hteFriendlyError(
        StateError('PostgrestException: PGRST204 internal database details'),
        fallback: fallback,
      );
      expect(message, fallback);
      expect(message, isNot(contains('PGRST204')));
      expect(message, isNot(contains('PostgrestException')));
    });

    test('maps expired sessions to listener-friendly copy', () {
      expect(
        hteFriendlyError(StateError('401 unauthorized JWT expired')),
        'Your session may have expired. Please sign in again.',
      );
    });

    test('maps missing content without exposing HTTP details', () {
      expect(
        hteFriendlyError(StateError('404 not found')),
        'This content is no longer available. Please refresh or try another item.',
      );
    });
  });
}
