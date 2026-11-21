-- Run after applying the privilege, role-helper and contact-trigger hardening migrations.
-- Raises if any client role retains a table-level TRUNCATE privilege or if
-- required narrow access has been removed.
DO $test$
DECLARE
  target text;
BEGIN
  FOREACH target IN ARRAY ARRAY[
    'public.app_notifications',
    'public.hte_audience_events',
    'public.hte_download_branding_settings',
    'public.hte_marketing_campaigns',
    'public.hte_social_connections',
    'public.hte_social_publish_jobs',
    'public.ad_impressions'
  ] LOOP
    IF has_table_privilege('anon', target, 'TRUNCATE')
       OR has_table_privilege('authenticated', target, 'TRUNCATE') THEN
      RAISE EXCEPTION 'Client TRUNCATE privilege remains on %', target;
    END IF;
  END LOOP;

  IF has_table_privilege('anon', 'public.hte_audience_events', 'INSERT') IS NOT TRUE
     OR has_table_privilege('authenticated', 'public.hte_audience_events', 'INSERT') IS NOT TRUE
     OR has_table_privilege('authenticated', 'public.hte_download_branding_settings', 'SELECT') IS NOT TRUE
     OR has_table_privilege('authenticated', 'public.app_notifications', 'SELECT') IS NOT TRUE THEN
    RAISE EXCEPTION 'A required public or member read/insert privilege is missing';
  END IF;

  IF has_table_privilege('anon', 'public.app_notifications', 'SELECT')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'SELECT')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'UPDATE')
     OR has_table_privilege('authenticated', 'public.hte_social_connections', 'TRUNCATE') THEN
    RAISE EXCEPTION 'An excessive client table privilege remains';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'hte_contact_rate_limit'
      AND p.proconfig @> ARRAY['search_path=""']
  ) THEN
    RAISE EXCEPTION 'Contact rate-limit SECURITY DEFINER search_path is not empty';
  END IF;

  IF NOT has_schema_privilege('service_role', 'private', 'USAGE')
     OR NOT has_function_privilege('service_role', 'private.hte_current_role()', 'EXECUTE')
     OR NOT has_function_privilege('service_role', 'private.hte_has_role(text[])', 'EXECUTE') THEN
    RAISE EXCEPTION 'Service-role access to private role helpers is incomplete';
  END IF;

  IF has_table_privilege('anon', 'private.user_roles', 'SELECT')
     OR has_function_privilege('anon', 'private.hte_current_role()', 'EXECUTE')
     OR has_function_privilege('anon', 'private.hte_has_role(text[])', 'EXECUTE') IS NOT TRUE
     OR has_function_privilege('authenticated', 'private.hte_has_role(text[])', 'EXECUTE') IS NOT TRUE THEN
    RAISE EXCEPTION 'Role-helper privileges do not preserve public RLS evaluation safely';
  END IF;
END;
$test$;
