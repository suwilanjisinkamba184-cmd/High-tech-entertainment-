-- Reconstructed from the live server-only booking-notification rate limiter.
CREATE SCHEMA IF NOT EXISTS private;
CREATE TABLE IF NOT EXISTS private.booking_notification_rate_limits (
  ip_hash text PRIMARY KEY CHECK (char_length(ip_hash) = 64),
  window_started_at timestamptz NOT NULL DEFAULT now(),
  request_count integer NOT NULL DEFAULT 0 CHECK (request_count >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS booking_notification_rate_limits_updated_idx
  ON private.booking_notification_rate_limits (updated_at);
ALTER TABLE private.booking_notification_rate_limits ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Deny all client access to booking rate limits" ON private.booking_notification_rate_limits;
CREATE POLICY "Deny all client access to booking rate limits"
  ON private.booking_notification_rate_limits FOR ALL TO anon, authenticated
  USING (false) WITH CHECK (false);
REVOKE ALL ON TABLE private.booking_notification_rate_limits FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.hte_consume_booking_notification_limit(
  _ip_hash text, _max_requests integer DEFAULT 5, _window_seconds integer DEFAULT 900
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_now timestamptz := now();
  v_count integer;
BEGIN
  IF coalesce(auth.jwt()->>'role','') <> 'service_role' THEN
    RAISE EXCEPTION 'Server-only operation' USING errcode = '42501';
  END IF;
  IF _ip_hash IS NULL OR char_length(_ip_hash) <> 64 OR _max_requests < 1 OR _window_seconds < 1 THEN
    RAISE EXCEPTION 'Invalid rate-limit arguments' USING errcode = '22023';
  END IF;
  INSERT INTO private.booking_notification_rate_limits AS r
    (ip_hash, window_started_at, request_count, updated_at)
  VALUES (_ip_hash, v_now, 1, v_now)
  ON CONFLICT (ip_hash) DO UPDATE SET
    window_started_at = CASE WHEN r.window_started_at <= v_now - make_interval(secs => _window_seconds) THEN v_now ELSE r.window_started_at END,
    request_count = CASE WHEN r.window_started_at <= v_now - make_interval(secs => _window_seconds) THEN 1 ELSE r.request_count + 1 END,
    updated_at = v_now
  RETURNING request_count INTO v_count;
  IF v_count = 1 THEN
    DELETE FROM private.booking_notification_rate_limits WHERE updated_at < v_now - interval '1 day';
  END IF;
  RETURN v_count <= _max_requests;
END;
$function$;
REVOKE ALL ON FUNCTION public.hte_consume_booking_notification_limit(text,integer,integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hte_consume_booking_notification_limit(text,integer,integer) TO service_role;
