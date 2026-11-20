-- Remove overlapping SELECT access caused by a broad ALL policy.
-- Anonymous visitors can read active settings only. Authenticated users can read
-- active settings; HTE owners/admins can also read inactive settings and manage rows.
DROP POLICY IF EXISTS "HTE administrators manage download branding"
ON public.hte_download_branding_settings;

DROP POLICY IF EXISTS "Public can read active HTE download branding"
ON public.hte_download_branding_settings;

CREATE POLICY "Public can read active HTE download branding"
ON public.hte_download_branding_settings
FOR SELECT TO anon
USING (is_active = true);

CREATE POLICY "Authenticated users read active and admins read all HTE download branding"
ON public.hte_download_branding_settings
FOR SELECT TO authenticated
USING (
    is_active = true
    OR private.hte_has_role(ARRAY['owner'::text, 'admin'::text])
);

CREATE POLICY "HTE administrators insert download branding"
ON public.hte_download_branding_settings
FOR INSERT TO authenticated
WITH CHECK (private.hte_has_role(ARRAY['owner'::text, 'admin'::text]));

CREATE POLICY "HTE administrators update download branding"
ON public.hte_download_branding_settings
FOR UPDATE TO authenticated
USING (private.hte_has_role(ARRAY['owner'::text, 'admin'::text]))
WITH CHECK (private.hte_has_role(ARRAY['owner'::text, 'admin'::text]));

CREATE POLICY "HTE administrators delete download branding"
ON public.hte_download_branding_settings
FOR DELETE TO authenticated
USING (private.hte_has_role(ARRAY['owner'::text, 'admin'::text]));
