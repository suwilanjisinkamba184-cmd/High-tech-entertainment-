-- Applied to the production High-Tech Entertainment Supabase project on 2026-10-30.
-- Consolidates public-published and founder-full-read access into one SELECT policy
-- per catalogue table. Does not alter rows, grants, INSERT/UPDATE/DELETE rules, or storage.

DROP POLICY IF EXISTS "HTE founder can read all artists" ON public.artists;
DROP POLICY IF EXISTS "Public can read published artists" ON public.artists;
CREATE POLICY "Public and founder can read artists" ON public.artists
  FOR SELECT TO anon, authenticated
  USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

DROP POLICY IF EXISTS "HTE founder can read all releases" ON public.releases;
DROP POLICY IF EXISTS "Public can read published releases" ON public.releases;
CREATE POLICY "Public and founder can read releases" ON public.releases
  FOR SELECT TO anon, authenticated
  USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

DROP POLICY IF EXISTS "HTE founder can read all tracks" ON public.tracks;
DROP POLICY IF EXISTS "Public can read published tracks" ON public.tracks;
CREATE POLICY "Public and founder can read tracks" ON public.tracks
  FOR SELECT TO anon, authenticated
  USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
