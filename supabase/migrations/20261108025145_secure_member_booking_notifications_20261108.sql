-- Secure member booking notifications without restricting public contact enquiries.
-- Existing contact messages are preserved; user_id is nullable for legacy and public enquiries.

ALTER TABLE public.contact_messages
  ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES auth.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS contact_messages_user_created_idx
  ON public.contact_messages (user_id, created_at DESC)
  WHERE user_id IS NOT NULL;

DROP POLICY IF EXISTS "Visitors can submit contact messages" ON public.contact_messages;
CREATE POLICY "Visitors can submit contact messages"
  ON public.contact_messages
  FOR INSERT TO anon, authenticated
  WITH CHECK (
    user_id IS NULL
    AND char_length(trim(name)) BETWEEN 1 AND 100
    AND char_length(trim(email)) BETWEEN 3 AND 254
    AND (subject IS NULL OR char_length(subject) <= 200)
    AND char_length(trim(message)) BETWEEN 1 AND 5000
  );

DROP POLICY IF EXISTS "Members can submit own booking requests" ON public.contact_messages;
CREATE POLICY "Members can submit own booking requests"
  ON public.contact_messages
  FOR INSERT TO authenticated
  WITH CHECK (
    user_id = (SELECT auth.uid())
    AND subject LIKE 'Booking request%'
    AND char_length(trim(name)) BETWEEN 1 AND 100
    AND char_length(trim(email)) BETWEEN 3 AND 254
    AND char_length(trim(message)) BETWEEN 1 AND 5000
  );

CREATE OR REPLACE FUNCTION public.submit_booking_request(
  p_name text,
  p_email text,
  p_subject text,
  p_message text
)
RETURNS uuid
LANGUAGE plpgsql
SET search_path TO 'public'
AS $function$
DECLARE
  new_id uuid;
  member_id uuid := auth.uid();
BEGIN
  IF member_id IS NULL THEN
    RAISE EXCEPTION 'Sign in to submit a booking request.';
  END IF;

  IF char_length(trim(coalesce(p_name, ''))) < 1
     OR char_length(trim(p_name)) > 100 THEN
    RAISE EXCEPTION 'Please enter a valid name.';
  END IF;

  IF char_length(trim(coalesce(p_email, ''))) < 3
     OR char_length(trim(p_email)) > 254 THEN
    RAISE EXCEPTION 'Please enter a valid email address.';
  END IF;

  IF p_subject IS NULL
     OR char_length(p_subject) > 200
     OR p_subject NOT LIKE 'Booking request%' THEN
    RAISE EXCEPTION 'Invalid booking subject.';
  END IF;

  IF char_length(trim(coalesce(p_message, ''))) < 1
     OR char_length(trim(p_message)) > 5000 THEN
    RAISE EXCEPTION 'Please enter booking details (up to 5000 characters).';
  END IF;

  INSERT INTO public.contact_messages(name, email, subject, message, user_id)
  VALUES (trim(p_name), trim(p_email), trim(p_subject), trim(p_message), member_id)
  RETURNING id INTO new_id;

  RETURN new_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.submit_booking_request(text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.submit_booking_request(text, text, text, text) TO authenticated;
