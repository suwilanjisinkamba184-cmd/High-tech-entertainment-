-- HTE temporary, rights-aware public media delivery.
-- Only HTE-owned/licensed direct audio can be staged for download.
-- External YouTube/Spotify links remain platform streams and are never copied.

UPDATE public.tracks
SET download_allowed = false,
    rights_status = 'external_link',
    download_branding_status = 'not_applicable'
WHERE source_platform IN ('youtube','spotify','external');

UPDATE public.tracks
SET download_allowed = false,
    rights_status = 'review_required'
WHERE source_platform = 'direct'
  AND rights_status = 'legacy_existing';

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'hte-download-temp',
  'hte-download-temp',
  false,
  104857600,
  ARRAY['audio/mpeg','audio/mp3','audio/mp4','audio/x-m4a','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/webm']::text[]
)
ON CONFLICT (id) DO UPDATE
SET public = false,
    file_size_limit = 83886080,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

CREATE TABLE IF NOT EXISTS public.hte_download_jobs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  track_id uuid NOT NULL REFERENCES public.tracks(id) ON DELETE CASCADE,
  object_path text NOT NULL UNIQUE,
  file_name text NOT NULL,
  ip_hash text NOT NULL,
  status text NOT NULL DEFAULT 'created'
    CHECK (status IN ('created','uploaded')),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS hte_download_jobs_ip_created_idx
  ON public.hte_download_jobs (ip_hash, created_at DESC);
CREATE INDEX IF NOT EXISTS hte_download_jobs_expiry_idx
  ON public.hte_download_jobs (expires_at)
  WHERE status IN ('created','uploaded');

ALTER TABLE public.hte_download_jobs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.hte_download_jobs FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.hte_download_jobs TO service_role;

COMMENT ON TABLE public.hte_download_jobs IS
  'Server-only temporary media delivery jobs. Private objects are removed after 30 minutes; job records expire after 60 minutes.';
