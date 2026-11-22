-- Reconstructed from the live HTE partner/admin contact directory table.
-- Direct client access is intentionally denied; trusted service-role workflows
-- may manage records, while public website cards can be served through a safe RPC.
CREATE TABLE IF NOT EXISTS public.hte_admin_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name text NOT NULL
    CHECK (char_length(trim(display_name)) BETWEEN 2 AND 100),
  job_title text NOT NULL
    CHECK (char_length(trim(job_title)) BETWEEN 2 AND 100),
  public_email text
    CHECK (public_email IS NULL OR char_length(public_email) <= 254),
  public_phone text
    CHECK (public_phone IS NULL OR char_length(public_phone) <= 40),
  public_contact_url text
    CHECK (public_contact_url IS NULL OR char_length(public_contact_url) <= 500),
  sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT hte_admin_contacts_contact_required CHECK (
    NULLIF(trim(COALESCE(public_email, '')), '') IS NOT NULL
    OR NULLIF(trim(COALESCE(public_phone, '')), '') IS NOT NULL
    OR NULLIF(trim(COALESCE(public_contact_url, '')), '') IS NOT NULL
  )
);

ALTER TABLE public.hte_admin_contacts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny all direct access to HTE admin contacts" ON public.hte_admin_contacts;
CREATE POLICY "deny all direct access to HTE admin contacts"
  ON public.hte_admin_contacts FOR ALL TO anon, authenticated
  USING (false) WITH CHECK (false);

REVOKE ALL ON TABLE public.hte_admin_contacts FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.hte_admin_contacts TO service_role;
