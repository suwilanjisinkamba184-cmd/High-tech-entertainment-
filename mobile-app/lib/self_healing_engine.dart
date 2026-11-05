import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'diagnostics_service.dart';

/// Conservative, local self-healing for known recoverable app-state faults.
///
/// This service deliberately does not execute downloaded code, alter auth
/// sessions, delete user files, clear the whole preference store, or change
/// server-side data. Unknown failures are recorded for diagnosis instead.
class HteSelfHealingEngine {
  HteSelfHealingEngine._();

  static bool _initialized = false;
  static bool _repairInProgress = false;
  static DateTime? _lastAutomaticRepair;
  static const Duration _automaticRepairCooldown = Duration(seconds: 30);

  static Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    try {
      final repairs = await repairKnownLocalState();
      if (repairs.isNotEmpty) {
        debugPrint('HTE self-healing repaired startup state: ${repairs.join('; ')}');
      }
    } catch (error, stackTrace) {
      debugPrint('HTE self-healing initialization was safely skipped: $error');
      debugPrintStack(stackTrace: stackTrace);
    }
  }

  /// Record an error and attempt only idempotent, known-safe local repairs.
  static Future<void> recordAndRecover(
    Object error, {
    StackTrace? stackTrace,
    String source = 'runtime',
  }) async {
    await HteDiagnostics.record(error, stackTrace: stackTrace, source: source);
    final now = DateTime.now();
    final last = _lastAutomaticRepair;
    if (last != null && now.difference(last) < _automaticRepairCooldown) return;
    _lastAutomaticRepair = now;
    try {
      final repairs = await repairKnownLocalState();
      if (repairs.isEmpty) {
        debugPrint('HTE self-healing: no known safe repair matched ($source).');
      } else {
        debugPrint('HTE self-healing completed for $source: ${repairs.join('; ')}');
      }
    } catch (repairError, repairStack) {
      debugPrint('HTE self-healing could not complete: $repairError');
      debugPrintStack(stackTrace: repairStack);
    }
  }

  /// Run the same conservative checks on demand from the hidden Engineer Mode.
  static Future<List<String>> repairNow() async {
    if (_repairInProgress) return <String>['A repair check is already running.'];
    _repairInProgress = true;
    try {
      final repairs = await repairKnownLocalState();
      if (repairs.isEmpty) {
        return <String>['No known local faults were found. Your data was left unchanged.'];
      }
      return repairs;
    } catch (error, stackTrace) {
      await HteDiagnostics.record(error, stackTrace: stackTrace, source: 'manual-self-heal');
      return <String>['Repair check stopped safely. Diagnostic details were recorded.'];
    } finally {
      _repairInProgress = false;
    }
  }

  static Future<List<String>> repairKnownLocalState() async {
    final prefs = await SharedPreferences.getInstance();
    final repairs = <String>[];

    // Repair only known preference types/ranges. Do not clear the entire store:
    // it also contains authentication, favourites and download metadata.
    final autoplay = prefs.get('hte_autoplay');
    if (autoplay != null && autoplay is! bool) {
      await prefs.setBool('hte_autoplay', true);
      repairs.add('Restored invalid autoplay preference to its safe default.');
    }

    final shuffle = prefs.get('hte_shuffle');
    if (shuffle != null && shuffle is! bool) {
      await prefs.setBool('hte_shuffle', false);
      repairs.add('Restored invalid shuffle preference to its safe default.');
    }

    final videoSpeed = prefs.get('hte_video_speed');
    if (videoSpeed != null &&
        (videoSpeed is! double || !videoSpeed.isFinite ||
            videoSpeed < 0.5 || videoSpeed > 2.0)) {
      await prefs.setDouble('hte_video_speed', 1.0);
      repairs.add('Reset an invalid video speed preference to 1×.');
    }

    final theme = prefs.get('hte_theme_mode_v1');
    if (theme != null &&
        (theme is! String || !const <String>{'dark', 'light', 'system'}.contains(theme))) {
      await prefs.setString('hte_theme_mode_v1', 'dark');
      repairs.add('Restored an invalid theme preference to High-Tech dark mode.');
    }

    // Confirm the diagnostic journal can be read; its reader is defensive and
    // never exposes credentials. Do not delete evidence during auto-repair.
    await HteDiagnostics.read();
    return repairs;
  }

  static Future<String> diagnosticReport() => HteDiagnostics.exportReport();
}
