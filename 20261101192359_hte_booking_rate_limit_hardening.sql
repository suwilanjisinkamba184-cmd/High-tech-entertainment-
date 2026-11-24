-- Reconstructed from the live contact-message rate-limit trigger.
CREATE OR REPLACE FUNCTION public.hte_contact_rate_limit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  recent_count integer;
  normalized_email text;
BEGIN
  normalized_email := lower(trim(new.email));
  PERFORM pg_advisory_xact_lock(hashtextextended(normalized_email, 0));
  SELECT count(*) INTO recent_count
  FROM public.contact_messages
  WHERE lower(trim(email)) = normalized_email
    AND created_at > now() - interval '10 minutes';
  IF recent_count >= 5 THEN
    RAISE EXCEPTION 'Too many messages from this email address. Please try again later.';
  END IF;
  RETURN new;
END;
$function$;
DROP TRIGGER IF EXISTS hte_contact_rate_limit_trigger ON public.contact_messages;
CREATE TRIGGER hte_contact_rate_limit_trigger
  BEFORE INSERT ON public.contact_messages
  FOR EACH ROW EXECUTE FUNCTION public.hte_contact_rate_limit();
REVOKE ALL ON FUNCTION public.hte_contact_rate_limit() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hte_contact_rate_limit() TO service_role;
