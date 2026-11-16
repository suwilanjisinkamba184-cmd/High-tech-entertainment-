-- One active HTE audio ident used by the branded-download composer.
CREATE TABLE IF NOT EXISTS public.hte_download_branding_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  jingle_url text NOT NULL CHECK (jingle_url LIKE 'https://%'),
  is_active boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.hte_download_branding_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can read active HTE download branding" ON public.hte_download_branding_settings;
CREATE POLICY "Public can read active HTE download branding"
  ON public.hte_download_branding_settings
  FOR SELECT TO anon, authenticated
  USING (is_active = true);

DROP POLICY IF EXISTS "HTE administrators manage download branding" ON public.hte_download_branding_settings;
CREATE POLICY "HTE administrators manage download branding"
  ON public.hte_download_branding_settings
  FOR ALL TO authenticated
  USING (private.hte_has_role(ARRAY['owner','admin']))
  WITH CHECK (private.hte_has_role(ARRAY['owner','admin']));

REVOKE ALL ON TABLE public.hte_download_branding_settings FROM PUBLIC;
GRANT SELECT ON TABLE public.hte_download_branding_settings TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.hte_download_branding_settings TO authenticated;
