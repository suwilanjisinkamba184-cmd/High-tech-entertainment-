-- Reconstructed from the live releases schema and the setup baseline.
ALTER TABLE public.releases ADD COLUMN IF NOT EXISTS spotify_url text;
