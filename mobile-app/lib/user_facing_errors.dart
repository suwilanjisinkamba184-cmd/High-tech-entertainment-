import 'dart:async';
import 'dart:io';

/// Converts backend, network and media exceptions into listener-safe copy.
/// Never return raw exception text to the public application UI.
String hteFriendlyError(
  Object error, {
  String fallback = 'Something went wrong. Please try again.',
}) {
  final value = error.toString().toLowerCase();

  if (error is SocketException ||
      error is HandshakeException ||
      error is TimeoutException ||
      value.contains('socketexception') ||
      value.contains('failed host lookup') ||
      value.contains('network is unreachable') ||
      value.contains('connection refused') ||
      value.contains('connection reset') ||
      value.contains('clientexception') ||
      value.contains('timed out') ||
      value.contains('no address associated with hostname')) {
    return 'No internet connection. Check your connection and try again.';
  }

  if (value.contains('401') ||
      value.contains('403') ||
      value.contains('jwt') ||
      value.contains('unauthorized') ||
      value.contains('not authenticated')) {
    return 'Your session may have expired. Please sign in again.';
  }

  if (value.contains('404') || value.contains('not found')) {
    return 'This content is no longer available. Please refresh or try another item.';
  }

  if (value.contains('permission denied') ||
      value.contains('row-level security') ||
      value.contains('rls')) {
    return 'This content is not available to your account right now.';
  }

  return fallback;
}
