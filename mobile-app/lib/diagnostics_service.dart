import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Small, local-only crash/error journal for the private Founder Studio.
/// Never stores auth tokens, request headers, passwords or user content.
class HteDiagnostics {
  static const _key = 'hte_diagnostic_events_v1';
  static const _maxEvents = 100;

  static String _redact(String value) {
    var result = value;
    result = result.replaceAll(
      RegExp(r'(bearer\s+)[A-Za-z0-9._~+/-]+=*', caseSensitive: false),
      r'$1[REDACTED]',
    );
    result = result.replaceAll(
      RegExp(r'(apikey|api_key|access_token|refresh_token|password)(\s*[:=]\s*)[^\s,;]+', caseSensitive: false),
      r'$1$2[REDACTED]',
    );
    result = result.replaceAll(
      RegExp(r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'),
      '[REDACTED_TOKEN]',
    );
    return result.length > 12000 ? result.substring(0, 12000) : result;
  }

  static Future<void> record(
    Object error, {
    StackTrace? stackTrace,
    String source = 'runtime',
  }) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key);
      final List<dynamic> previous = raw == null
          ? <dynamic>[]
          : (jsonDecode(raw) as List<dynamic>);
      final event = <String, dynamic>{
        'time': DateTime.now().toUtc().toIso8601String(),
        'source': _redact(source),
        'error': _redact(error.toString()),
        'stack': stackTrace == null ? '' : _redact(stackTrace.toString()),
      };
      previous.add(event);
      final retained = previous.length > _maxEvents
          ? previous.sublist(previous.length - _maxEvents)
          : previous;
      await prefs.setString(_key, jsonEncode(retained));
    } catch (_) {
      // Diagnostics must never crash or block the app.
    }
  }

  static Future<List<Map<String, dynamic>>> read() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key);
      if (raw == null || raw.isEmpty) return <Map<String, dynamic>>[];
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList()
          .reversed
          .toList();
    } catch (_) {
      return <Map<String, dynamic>>[];
    }
  }

  static Future<void> clear() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_key);
    } catch (_) {}
  }

  static Future<String> exportReport() async {
    final events = await read();
    final buffer = StringBuffer()
      ..writeln('HIGH-TECH ENTERTAINMENT - DIAGNOSTIC REPORT')
      ..writeln('Generated: ${DateTime.now().toUtc().toIso8601String()}')
      ..writeln('Stored events: ${events.length}')
      ..writeln('Sensitive credentials are redacted.')
      ..writeln('');
    for (final event in events) {
      buffer
        ..writeln('TIME: ${event['time'] ?? 'unknown'}')
        ..writeln('SOURCE: ${event['source'] ?? 'unknown'}')
        ..writeln('ERROR: ${event['error'] ?? 'unknown'}')
        ..writeln('STACK:')
        ..writeln(event['stack'] ?? '')
        ..writeln('---');
    }
    return buffer.toString();
  }
}
