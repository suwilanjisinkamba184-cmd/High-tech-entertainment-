-- High-Tech Entertainment: founder-only catalogue and media publishing.
-- Applied to the production project on 2026-11-07.
-- Public read policies remain unchanged: visitors can read published catalogue rows.
-- The owner role is stored in private.user_roles and checked by private.hte_has_role().
-- This migration is intentionally safe to re-run.

ALTER POLICY "HTE administrators delete artists" ON public.artists
  USING (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators insert artists" ON public.artists
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update artists" ON public.artists
  USING (private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete releases" ON public.releases
  USING (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators insert releases" ON public.releases
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update releases" ON public.releases
  USING (private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete tracks" ON public.tracks
  USING (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators insert tracks" ON public.tracks
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update tracks" ON public.tracks
  USING (private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete videos" ON public.videos
  USING (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators insert videos" ON public.videos
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update videos" ON public.videos
  USING (private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete artwork" ON storage.objects
  USING (bucket_id = 'hte-artwork' AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update artwork" ON storage.objects
  USING (bucket_id = 'hte-artwork' AND private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (bucket_id = 'hte-artwork' AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators upload artwork" ON storage.objects
  WITH CHECK (bucket_id = 'hte-artwork' AND private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete audio" ON storage.objects
  USING (bucket_id = 'hte-audio' AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update audio" ON storage.objects
  USING (bucket_id = 'hte-audio' AND private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (bucket_id = 'hte-audio' AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators upload audio" ON storage.objects
  WITH CHECK (bucket_id = 'hte-audio' AND private.hte_has_role(ARRAY['owner']::text[]));

ALTER POLICY "HTE administrators delete videos" ON storage.objects
  USING (bucket_id IN ('hte-video', 'hte-videos') AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators update videos" ON storage.objects
  USING (bucket_id IN ('hte-video', 'hte-videos') AND private.hte_has_role(ARRAY['owner']::text[]))
  WITH CHECK (bucket_id IN ('hte-video', 'hte-videos') AND private.hte_has_role(ARRAY['owner']::text[]));
ALTER POLICY "HTE administrators upload videos" ON storage.objects
  WITH CHECK (bucket_id IN ('hte-video', 'hte-videos') AND private.hte_has_role(ARRAY['owner']::text[]));
