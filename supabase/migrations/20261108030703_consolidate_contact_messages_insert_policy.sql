DROP POLICY IF EXISTS "Visitors can submit contact messages" ON public.contact_messages;
DROP POLICY IF EXISTS "Members can submit own booking requests" ON public.contact_messages;

CREATE POLICY "Public and member contact submissions"
ON public.contact_messages
FOR INSERT
TO anon, authenticated
WITH CHECK (
  char_length(trim(name)) BETWEEN 1 AND 100
  AND char_length(trim(email)) BETWEEN 3 AND 254
  AND char_length(trim(message)) BETWEEN 1 AND 5000
  AND (subject IS NULL OR char_length(subject) <= 200)
  AND (
    user_id IS NULL
    OR (
      user_id = (SELECT auth.uid())
      AND subject LIKE 'Booking request%'
    )
  )
);