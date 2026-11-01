-- Split founder audio-ad management into command-specific policies.
-- Keep public reads limited to currently published ads while allowing the founder
-- to view drafts/expired ads in the admin interface.
DROP POLICY IF EXISTS "HTE founder can manage audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "Published active audio ads are readable" ON public.audio_ads;
DROP POLICY IF EXISTS "Published active ads or founder can read" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE founder inserts audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE founder updates audio ads" ON public.audio_ads;
DROP POLICY IF EXISTS "HTE founder deletes audio ads" ON public.audio_ads;

CREATE POLICY "Published active ads or founder can read" ON public.audio_ads
FOR SELECT TO anon, authenticated
USING (
  (
    is_published = true
    AND (starts_at IS NULL OR starts_at <= now())
    AND (ends_at IS NULL OR ends_at > now())
  )
  OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

CREATE POLICY "HTE founder inserts audio ads" ON public.audio_ads
FOR INSERT TO authenticated
WITH CHECK ((SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

CREATE POLICY "HTE founder updates audio ads" ON public.audio_ads
FOR UPDATE TO authenticated
USING ((SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
WITH CHECK ((SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

CREATE POLICY "HTE founder deletes audio ads" ON public.audio_ads
FOR DELETE TO authenticated
USING ((SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
