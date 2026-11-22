-- Reconstructed from the live table definition during migration reconciliation.
-- The table is server-managed; clients receive no read/write grants.
CREATE TABLE IF NOT EXISTS public.booking_notification_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid NOT NULL UNIQUE REFERENCES public.contact_messages(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','sent','failed')),
  provider_message_id text,
  error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.booking_notification_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Deny client access to booking notification log" ON public.booking_notification_log;
CREATE POLICY "Deny client access to booking notification log"
  ON public.booking_notification_log FOR ALL TO anon, authenticated
  USING (false) WITH CHECK (false);
REVOKE ALL ON TABLE public.booking_notification_log FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.booking_notification_log TO service_role;
