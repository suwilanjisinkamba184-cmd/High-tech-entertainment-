-- Serialize each visitor's rolling-hour quota check and job insert.
-- The lock and insert execute in one PostgreSQL transaction, so concurrent
-- Edge Function requests cannot all pass a stale count at the quota boundary.
ALTER TABLE public.hte_download_jobs
  ADD COLUMN IF NOT EXISTS request_nonce uuid;

-- Failed staging attempts retain their one-time nonce and quota reservation.
-- This prevents replaying a signed request after a downstream copy/validation failure.
ALTER TABLE public.hte_download_jobs
  DROP CONSTRAINT IF EXISTS hte_download_jobs_status_check;
ALTER TABLE public.hte_download_jobs
  ADD CONSTRAINT hte_download_jobs_status_check
  CHECK (status IN ('created', 'uploaded', 'failed'));

-- Failed reservations remain until expiry for replay prevention, so include
-- them in the cleanup index as well.
DROP INDEX IF EXISTS public.hte_download_jobs_expiry_idx;
CREATE INDEX hte_download_jobs_expiry_idx
  ON public.hte_download_jobs (expires_at)
  WHERE status IN ('created', 'uploaded', 'failed');

CREATE UNIQUE INDEX IF NOT EXISTS hte_download_jobs_request_nonce_key
  ON public.hte_download_jobs (request_nonce)
  WHERE request_nonce IS NOT NULL;

CREATE OR REPLACE FUNCTION public.hte_create_download_job(
  p_id uuid,
  p_request_nonce uuid,
  p_track_id uuid,
  p_object_path text,
  p_file_name text,
  p_ip_hash text,
  p_expires_at timestamptz,
  p_max_downloads integer DEFAULT 20
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
  recent_count bigint;
BEGIN
  IF p_id IS NULL OR p_track_id IS NULL
     OR p_object_path IS NULL OR length(trim(p_object_path)) = 0
     OR p_file_name IS NULL OR length(trim(p_file_name)) = 0
     OR p_ip_hash IS NULL OR p_ip_hash !~ '^[0-9a-f]{64}$'
     OR p_request_nonce IS NULL
     OR p_expires_at IS NULL OR p_expires_at <= now()
     OR p_max_downloads IS NULL OR p_max_downloads < 1 OR p_max_downloads > 100 THEN
    RAISE EXCEPTION 'Invalid download quota parameters'
      USING ERRCODE = '22023';
  END IF;

  -- A transaction-scoped advisory lock is released automatically on commit
  -- or rollback. Hash collisions can only serialize unrelated callers; they
  -- cannot grant extra quota.
  PERFORM pg_advisory_xact_lock(hashtextextended(p_ip_hash, 0));

  SELECT count(*)
    INTO recent_count
    FROM public.hte_download_jobs
   WHERE ip_hash = p_ip_hash
     AND created_at >= now() - interval '1 hour';

  IF recent_count >= p_max_downloads THEN
    RETURN false;
  END IF;

  INSERT INTO public.hte_download_jobs (
    id, request_nonce, track_id, object_path, file_name, ip_hash, status, expires_at
  ) VALUES (
    p_id, p_request_nonce, p_track_id, p_object_path, p_file_name, p_ip_hash, 'created', p_expires_at
  );

  RETURN true;
END;
$function$;

REVOKE ALL ON FUNCTION public.hte_create_download_job(
  uuid, uuid, uuid, text, text, text, timestamptz, integer
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hte_create_download_job(
  uuid, uuid, uuid, text, text, text, timestamptz, integer
) TO service_role;

COMMENT ON FUNCTION public.hte_create_download_job(
  uuid, uuid, uuid, text, text, text, timestamptz, integer
) IS 'Atomically enforces the per-visitor rolling-hour temporary download quota and creates the job. Service-role only.';
