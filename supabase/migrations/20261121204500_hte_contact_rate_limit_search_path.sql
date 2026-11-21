-- Harden the contact-message rate-limit trigger's SECURITY DEFINER search path.
-- All application tables are schema-qualified; built-in functions resolve from pg_catalog.
CREATE OR REPLACE FUNCTION public.hte_contact_rate_limit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
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

COMMENT ON FUNCTION public.hte_contact_rate_limit()
IS 'Rate-limits contact-message submissions by normalized email using a transaction advisory lock; SECURITY DEFINER uses an empty search_path.';
