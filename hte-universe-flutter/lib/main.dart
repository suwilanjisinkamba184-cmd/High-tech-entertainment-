import 'package:material_ui/material_ui.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'src/backend/supabase_config.dart';
import 'src/hte_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final backendConfigured = await HteBackend.initialize();
  runApp(HteApp(backendConfigured: backendConfigured));
}

abstract final class HteBackend {
  static Future<bool> initialize() async {
    if (!HteSupabaseConfig.isConfigured) return false;

    try {
      await Supabase.initialize(
        url: HteSupabaseConfig.url,
        publishableKey: HteSupabaseConfig.publishableKey,
      );
      return true;
    } catch (_) {
      // A backend startup failure must not prevent the shell from opening.
      // The feature layer will expose a retryable, user-safe service state.
      return false;
    }
  }
}
