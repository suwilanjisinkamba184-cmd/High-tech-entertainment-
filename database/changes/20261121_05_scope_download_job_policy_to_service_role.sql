-- Explicitly document that only the server-side service role may manage
-- temporary download jobs. No anon/authenticated policy is created.
DROP POLICY IF EXISTS "Service role manages temporary download jobs"
  ON public.hte_download_jobs;

CREATE POLICY "Service role manages temporary download jobs"
  ON public.hte_download_jobs
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);
