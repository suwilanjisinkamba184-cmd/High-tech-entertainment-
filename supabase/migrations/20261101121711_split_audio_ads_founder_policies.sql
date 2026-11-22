-- Separate public playback access from founder/admin ad management.
DROP POLICY IF EXISTS "Public can read audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "Published active ads or administrators can read" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE administrators insert audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE administrators update audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE administrators delete audio ads" ON public.audio_ads;

CREATE POLICY "Published active ads or administrators can read"
  ON public.audio_ads FOR SELECT TO anon, authenticated
  USING (
    (is_published AND (starts_at IS NULL OR starts_at <= now()) AND (ends_at IS NULL OR ends_at > now()))
    OR private.hte_has_role(ARRAY['owner','admin'])
  );
CREATE POLICY "HTE administrators insert audio ads"
  ON public.audio_ads FOR INSERT TO authenticated
  WITH CHECK (private.hte_has_role(ARRAY['owner','admin']));
CREATE POLICY "HTE administrators update audio ads"
  ON public.audio_ads FOR UPDATE TO authenticated
  USING (private.hte_has_role(ARRAY['owner','admin']))
  WITH CHECK (private.hte_has_role(ARRAY['owner','admin']));
CREATE POLICY "HTE administrators delete audio ads"
  ON public.audio_ads FOR DELETE TO authenticated
  USING (private.hte_has_role(ARRAY['owner','admin']));

REVOKE ALL ON TABLE public.audio_ads FROM PUBLIC;
GRANT SELECT ON TABLE public.audio_ads TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.audio_ads TO authenticated;
