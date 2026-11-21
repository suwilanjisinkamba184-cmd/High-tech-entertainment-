-- Consolidate member booking history and Founder/admin contact access into one
-- permissive SELECT policy. This preserves the live project's exact access model
-- while avoiding overlapping permissive policies.
DROP POLICY IF EXISTS "HTE founder can read contact messages" ON public.contact_messages;
DROP POLICY IF EXISTS "Members read own booking requests" ON public.contact_messages;
DROP POLICY IF EXISTS "HTE administrators and members read permitted contact messages" ON public.contact_messages;

CREATE POLICY "HTE administrators and members read permitted contact messages"
ON public.contact_messages
FOR SELECT
TO authenticated
USING (
  private.hte_has_role(ARRAY['owner','admin'])
  OR (
    user_id = (SELECT auth.uid())
    AND subject LIKE 'Booking request%'
  )
);
