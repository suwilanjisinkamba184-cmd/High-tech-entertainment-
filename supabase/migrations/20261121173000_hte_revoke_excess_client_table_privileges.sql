-- Remove table-level privileges that bypass RLS (notably TRUNCATE).
-- Regrant only the operations represented by the existing row-level policies.
-- service_role and postgres privileges are intentionally unchanged.

REVOKE ALL ON TABLE public.app_notifications FROM anon, authenticated;
GRANT SELECT, INSERT ON TABLE public.app_notifications TO authenticated;

REVOKE ALL ON TABLE public.hte_audience_events FROM anon, authenticated;
GRANT INSERT ON TABLE public.hte_audience_events TO anon;
GRANT SELECT, INSERT ON TABLE public.hte_audience_events TO authenticated;

REVOKE ALL ON TABLE public.hte_download_branding_settings FROM anon, authenticated;
GRANT SELECT ON TABLE public.hte_download_branding_settings TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.hte_download_branding_settings TO authenticated;

REVOKE ALL ON TABLE public.hte_marketing_campaigns FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.hte_marketing_campaigns TO authenticated;

REVOKE ALL ON TABLE public.hte_social_connections FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.hte_social_connections TO authenticated;

REVOKE ALL ON TABLE public.hte_social_publish_jobs FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.hte_social_publish_jobs TO authenticated;

REVOKE ALL ON TABLE public.ad_impressions FROM anon, authenticated;
GRANT SELECT, INSERT ON TABLE public.ad_impressions TO authenticated;
