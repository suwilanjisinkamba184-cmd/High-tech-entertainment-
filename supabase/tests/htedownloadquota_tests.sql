-- Run after applying 20261121170000_hte_atomic_download_quota.sql.
-- Validates quota-boundary enforcement and replay protection using isolated test rows.
DO $test$
DECLARE
  test_track uuid := gen_random_uuid();
  test_ip_hash text := repeat('a', 64);
  replay_ip_hash text := repeat('b', 64);
  request_id uuid;
  request_nonce uuid;
  accepted boolean;
  accepted_count integer := 0;
  i integer;
BEGIN
  INSERT INTO public.tracks (id, title, is_published)
  VALUES (test_track, 'HTE quota regression fixture', false);

  FOR i IN 1..21 LOOP
    request_id := gen_random_uuid();
    request_nonce := gen_random_uuid();
    accepted := public.hte_create_download_job(
      request_id,
      request_nonce,
      test_track,
      'requests/' || request_id::text || '.mp3',
      'quota-test.mp3',
      test_ip_hash,
      now() + interval '1 hour',
      20
    );
    IF accepted THEN accepted_count := accepted_count + 1; END IF;
    IF i <= 20 AND accepted IS NOT TRUE THEN
      RAISE EXCEPTION 'Quota rejected request % before the configured limit', i;
    END IF;
    IF i = 21 AND accepted IS NOT FALSE THEN
      RAISE EXCEPTION 'Quota accepted request 21; expected the 20-request cap';
    END IF;
  END LOOP;

  IF accepted_count <> 20 THEN
    RAISE EXCEPTION 'Expected 20 accepted reservations, received %', accepted_count;
  END IF;

  request_id := gen_random_uuid();
  request_nonce := gen_random_uuid();
  accepted := public.hte_create_download_job(
    request_id, request_nonce, test_track,
    'requests/' || request_id::text || '.mp3', 'replay-test.mp3',
    replay_ip_hash, now() + interval '1 hour', 20
  );
  IF accepted IS NOT TRUE THEN
    RAISE EXCEPTION 'Initial nonce reservation was rejected unexpectedly';
  END IF;

  -- A downstream failure must not release the nonce and allow request replay.
  UPDATE public.hte_download_jobs SET status = 'failed' WHERE id = request_id;

  BEGIN
    request_id := gen_random_uuid();
    PERFORM public.hte_create_download_job(
      request_id, request_nonce, test_track,
      'requests/' || request_id::text || '.mp3', 'replay-test.mp3',
      replay_ip_hash, now() + interval '1 hour', 20
    );
    RAISE EXCEPTION 'Replayed nonce was accepted; expected unique constraint failure';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  DELETE FROM public.hte_download_jobs WHERE ip_hash IN (test_ip_hash, replay_ip_hash);
  DELETE FROM public.tracks WHERE id = test_track;
END;
$test$;
