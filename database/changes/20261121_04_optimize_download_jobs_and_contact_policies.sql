-- Keep the temporary-download job lookup indexed and consolidate equivalent
-- contact-message read policies without changing their effective authorization.
CREATE INDEX IF NOT EXISTS hte_download_jobs_track_id_idx
  ON public.hte_download_jobs (track_id);

DROP POLICY IF EXISTS "HTE administrators can read contact messages"
  ON public.contact_messages;
DROP POLICY IF EXISTS "Members read own booking requests"
  ON public.contact_messages;

CREATE POLICY "HTE administrators and members read permitted contact messages"
  ON public.contact_messages
  FOR SELECT
  TO authenticated
  USING (
    private.hte_has_role(ARRAY['owner', 'admin']::text[])
    OR (
      user_id = (SELECT auth.uid())
      AND subject LIKE 'Booking request%'
    )
  );
