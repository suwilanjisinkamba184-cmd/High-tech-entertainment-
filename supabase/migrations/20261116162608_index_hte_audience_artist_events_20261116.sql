-- Reconstructed from the live HTE audience-events index definition.
-- The index is also guarded in the 20261116 supplemental migration; IF NOT EXISTS
-- keeps both source paths safe during the reconciliation transition.
CREATE INDEX IF NOT EXISTS hte_audience_events_artist_created_idx
  ON public.hte_audience_events (artist_id, created_at DESC)
  WHERE artist_id IS NOT NULL;
