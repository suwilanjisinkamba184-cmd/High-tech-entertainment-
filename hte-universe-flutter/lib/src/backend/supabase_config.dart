/// Public client configuration shared with the website. The publishable key is
/// intentionally public; privileged service-role keys must never be placed here.
abstract final class HteSupabaseConfig {
  static const url = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: 'https://wqrxacjbsldbnoekbkbf.supabase.co',
  );

  static const publishableKey = String.fromEnvironment(
    'SUPABASE_PUBLISHABLE_KEY',
    defaultValue: 'sb_publishable_hB_aRh-dnicpRQq3wiRtWg_JTdhmMda',
  );

  static bool get isConfigured => url.startsWith('https://') && publishableKey.isNotEmpty;
}
