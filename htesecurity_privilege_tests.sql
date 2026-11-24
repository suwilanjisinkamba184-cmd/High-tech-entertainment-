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
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'INSERT')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'UPDATE')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'DELETE')
     OR has_table_privilege('authenticated', 'public.hte_social_connections', 'TRUNCATE') THEN
    RAISE EXCEPTION 'An excessive client table privilege remains';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'artists'
      AND policyname = 'Public and administrators can read artists' AND cmd = 'SELECT'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'releases'
      AND policyname = 'Public and administrators can read releases' AND cmd = 'SELECT'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'tracks'
      AND policyname = 'Public and administrators can read tracks' AND cmd = 'SELECT'
  ) THEN
    RAISE EXCEPTION 'Role-aware public catalogue read policies are incomplete';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'artists'
      AND policyname = 'HTE administrators insert artists'
      AND with_check LIKE '%hte_has_role%'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'releases'
      AND policyname = 'HTE administrators insert releases'
      AND with_check LIKE '%hte_has_role%'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'tracks'
      AND policyname = 'HTE administrators insert tracks'
      AND with_check LIKE '%hte_has_role%'
  ) THEN
    RAISE EXCEPTION 'Founder-only catalogue insert policies are incomplete';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'videos' AND c.relrowsecurity
  ) THEN
    RAISE EXCEPTION 'Video catalogue RLS is not enabled';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'videos'
      AND policyname = 'Public and administrators can read videos'
      AND cmd = 'SELECT'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'videos'
      AND policyname = 'HTE administrators insert videos'
      AND cmd = 'INSERT' AND with_check LIKE '%hte_has_role%'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'videos'
      AND policyname = 'HTE administrators update videos'
      AND cmd = 'UPDATE' AND qual LIKE '%hte_has_role%' AND with_check LIKE '%hte_has_role%'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'videos'
      AND policyname = 'HTE administrators delete videos'
      AND cmd = 'DELETE' AND qual LIKE '%hte_has_role%'
  ) THEN
    RAISE EXCEPTION 'Video catalogue Founder-only write policies are incomplete';
  END IF;

  IF NOT has_table_privilege('anon', 'public.hte_download_branding_settings', 'SELECT')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'INSERT')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'UPDATE')
     OR has_table_privilege('anon', 'public.hte_download_branding_settings', 'DELETE') THEN
    RAISE EXCEPTION 'Public active download branding must remain read-only for anonymous visitors';
  END IF;

  IF has_table_privilege('anon', 'public.hte_admin_contacts', 'SELECT')
     OR has_table_privilege('authenticated', 'public.hte_admin_contacts', 'SELECT')
     OR has_table_privilege('authenticated', 'public.hte_admin_contacts', 'INSERT')
     OR NOT has_table_privilege('service_role', 'public.hte_admin_contacts', 'SELECT') THEN
    RAISE EXCEPTION 'HTE admin contact directory privileges are not least-privilege';
  END IF;

  IF NOT has_table_privilege('service_role', 'public.app_notifications', 'SELECT')
     OR NOT has_table_privilege('service_role', 'public.hte_audience_events', 'INSERT')
     OR NOT has_table_privilege('service_role', 'public.hte_download_branding_settings', 'UPDATE')
     OR NOT has_table_privilege('service_role', 'public.hte_marketing_campaigns', 'SELECT')
     OR NOT has_table_privilege('service_role', 'public.hte_social_connections', 'SELECT')
     OR NOT has_table_privilege('service_role', 'public.hte_social_publish_jobs', 'INSERT')
     OR NOT has_table_privilege('service_role', 'public.ad_impressions', 'SELECT') THEN
    RAISE EXCEPTION 'Trusted service-role table access was unintentionally removed';
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
