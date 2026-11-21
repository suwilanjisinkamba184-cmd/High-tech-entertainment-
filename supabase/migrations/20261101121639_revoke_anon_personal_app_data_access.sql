-- Revoke anonymous access to personal application data while retaining
-- the intentionally public catalogue, public playlist-track reads, and contact inserts.
REVOKE ALL ON TABLE public.profiles, public.app_preferences, public.app_notifications,
  public.user_favorites, public.playlists, public.listening_history, public.bookings,
  public.ad_impressions
FROM PUBLIC, anon;

-- Role assignment storage and server-verified role RPCs are established here so
-- later audio-ad and Founder policies can safely depend on them.
-- Reconstructed from the live private role-assignment table and role RPC definitions.
CREATE SCHEMA IF NOT EXISTS private;
CREATE TABLE IF NOT EXISTS private.user_roles (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('owner','admin','music_promoter','partner')),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL
);
ALTER TABLE private.user_roles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Deny all client access to HTE role assignments" ON private.user_roles;
CREATE POLICY "Deny all client access to HTE role assignments"
  ON private.user_roles FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
REVOKE ALL ON TABLE private.user_roles FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE private.user_roles TO service_role;

CREATE OR REPLACE FUNCTION private.hte_current_role()
RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$
  SELECT r.role FROM private.user_roles r
  WHERE auth.uid() IS NOT NULL AND r.user_id = auth.uid() AND r.is_active
  LIMIT 1;
$function$;

CREATE OR REPLACE FUNCTION private.hte_has_role(_roles text[])
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$
  SELECT auth.uid() IS NOT NULL AND EXISTS (
    SELECT 1 FROM private.user_roles r
    WHERE r.user_id = auth.uid() AND r.is_active AND r.role = ANY(_roles)
  );
$function$;

CREATE OR REPLACE FUNCTION public.hte_my_role()
RETURNS text LANGUAGE sql STABLE SET search_path TO ''
AS $function$ SELECT private.hte_current_role(); $function$;

CREATE OR REPLACE FUNCTION public.hte_is_admin()
RETURNS boolean LANGUAGE sql STABLE SET search_path TO ''
AS $function$ SELECT coalesce(private.hte_has_role(ARRAY['owner','admin']), false); $function$;

CREATE OR REPLACE FUNCTION public.is_hte_admin()
RETURNS boolean LANGUAGE sql STABLE SET search_path TO ''
AS $function$ SELECT private.hte_has_role(ARRAY['owner','admin']); $function$;

REVOKE ALL ON FUNCTION private.hte_current_role() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.hte_has_role(text[]) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.hte_my_role() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.hte_is_admin() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_hte_admin() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.hte_current_role() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.hte_has_role(text[]) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.hte_my_role() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.hte_is_admin() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_hte_admin() TO authenticated, service_role;
