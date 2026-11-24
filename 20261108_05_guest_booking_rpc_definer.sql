CREATE OR REPLACE FUNCTION public.submit_booking_request(
  p_name text,
  p_email text,
  p_subject text,
  p_message text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  new_id uuid;
  member_id uuid := auth.uid();
BEGIN
  -- Narrow insert-only compatibility endpoint: no caller-supplied user_id and no read access.
  -- Guests get NULL user_id; signed-in members are bound to auth.uid().
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

REVOKE ALL ON FUNCTION public.submit_booking_request(text, text, text, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.submit_booking_request(text, text, text, text) TO anon, authenticated;