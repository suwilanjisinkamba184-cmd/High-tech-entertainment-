import 'package:material_ui/material_ui.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'src/hte_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final backendConfigured = await HteBackend.initialize();
  runApp(HteApp(backendConfigured: backendConfigured));
}

abstract final class HteBackend {
  static Future<bool> initialize() async {
    const url = String.fromEnvironment('SUPABASE_URL');
    const publishableKey = String.fromEnvironment('SUPABASE_PUBLISHABLE_KEY');

    if (url.isEmpty || publishableKey.isEmpty) {
      return false;
    }

    try {
      await Supabase.initialize(
        url: url,
        publishableKey: publishableKey,
      );
      return true;
    } catch (_) {
      // A backend startup failure must not prevent the shell from opening.
      // The feature layer will expose a retryable, user-safe service state.
      return false;
    }
  }
}
