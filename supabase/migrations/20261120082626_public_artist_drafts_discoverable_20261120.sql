-- Public artist cards are intentionally discoverable before publication.
-- The artists table contains public-facing profile metadata only; write access
-- remains restricted by the existing owner/admin INSERT, UPDATE and DELETE policies.
ALTER POLICY "Public and administrators can read artists"
ON public.artists
USING (true);

COMMENT ON POLICY "Public and administrators can read artists"
ON public.artists
IS 'Public visitors may discover artist profile cards, including draft profiles; only owner/admin roles may write artist records.';
