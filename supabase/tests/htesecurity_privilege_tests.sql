-- Run after applying 20261121173000_hte_revoke_excess_client_table_privileges.sql.
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
END;
$test$;
