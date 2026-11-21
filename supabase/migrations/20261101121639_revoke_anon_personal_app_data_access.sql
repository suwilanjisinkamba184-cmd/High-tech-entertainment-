-- Revoke anonymous access to personal application data while retaining
-- the intentionally public catalogue, public playlist-track reads, and contact inserts.
REVOKE ALL ON TABLE public.profiles, public.app_preferences,
  public.user_favorites, public.playlists, public.listening_history, public.ad_impressions
FROM PUBLIC, anon;
-- app_notifications is introduced by the later 20261105020850 migration;
-- its client grants are hardened in 20261121173000 after that table exists.

-- Role assignment storage and server-verified role RPCs are established here so
-- later audio-ad and Founder policies can safely depend on them.
-- Reconstructed from the live private role-assignment table and role RPC definitions.
CREATE SCHEMA IF NOT EXISTS private;
GRANT USAGE ON SCHEMA private TO anon, authenticated, service_role;
CREATE TABLE IF NOT EXISTS private.user_roles (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('owner','admin','music_promoter','partner')),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS user_roles_created_by_idx ON private.user_roles (created_by);
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
REVOKE ALL ON FUNCTION private.hte_has_role(text[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.hte_my_role() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.hte_is_admin() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_hte_admin() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.hte_current_role() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.hte_has_role(text[]) TO anon, authenticated, service_role;

DROP POLICY IF EXISTS "Users read own ad impressions" ON public.ad_impressions;
CREATE POLICY "Users and admins read ad impressions" ON public.ad_impressions FOR SELECT TO authenticated
  USING ((user_id = (SELECT auth.uid())) OR private.hte_has_role(ARRAY['owner','admin']));
GRANT EXECUTE ON FUNCTION public.hte_my_role() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.hte_is_admin() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_hte_admin() TO authenticated, service_role;

-- Replace the baseline Founder-only video read rule with the live role-aware
-- catalogue rule: public visitors see published videos; owners/admins can also
-- inspect drafts in Founder Studio.
DROP POLICY IF EXISTS "Public can read published videos" ON public.videos;
DROP POLICY IF EXISTS "Public and administrators can read videos" ON public.videos;
CREATE POLICY "Public and administrators can read videos" ON public.videos
  FOR SELECT TO anon, authenticated
  USING (is_published = true OR private.hte_has_role(ARRAY['owner','admin']));
