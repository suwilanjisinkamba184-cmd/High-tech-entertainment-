CREATE OR REPLACE FUNCTION private.hte_admin_get_analytics()
RETURNS TABLE(
  total_plays bigint,
  unique_listeners bigint,
  ad_impressions bigint,
  published_tracks bigint,
  total_tracks bigint,
  top_track_title text,
  top_track_plays bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
begin
  if not private.hte_has_role(array['owner','admin']) then
    raise exception 'Not authorized to view platform analytics'
      using errcode = '42501';
  end if;

  return query
  with top_track as (
    select tracks.title, count(*)::bigint as plays
    from public.listening_history as history
    join public.tracks as tracks on tracks.id = history.track_id
    group by tracks.id, tracks.title
    order by count(*) desc
    limit 1
  )
  select
    (select count(*)::bigint from public.listening_history),
    (select count(distinct user_id)::bigint from public.listening_history),
    (select count(*)::bigint from public.ad_impressions),
    (select count(*)::bigint from public.tracks where is_published = true),
    (select count(*)::bigint from public.tracks),
    (select top_track.title from top_track),
    coalesce((select top_track.plays from top_track), 0::bigint);
end;
$function$;

CREATE OR REPLACE FUNCTION private.hte_admin_list_users()
RETURNS TABLE(
  id uuid,
  email text,
  created_at timestamp with time zone,
  last_sign_in_at timestamp with time zone,
  role text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
begin
  if not private.hte_has_role(array['owner','admin']) then
    raise exception 'Not authorized to view platform users'
      using errcode = '42501';
  end if;

  return query
  select
    users.id,
    users.email::text,
    users.created_at,
    users.last_sign_in_at,
    coalesce(user_roles.role, 'user')::text
  from auth.users as users
  left join private.user_roles as user_roles
    on user_roles.user_id = users.id
  order by users.created_at desc
  limit 200;
end;
$function$;

REVOKE ALL ON FUNCTION private.hte_admin_get_analytics() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.hte_admin_list_users() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.hte_admin_get_analytics() TO authenticated;
GRANT EXECUTE ON FUNCTION private.hte_admin_list_users() TO authenticated;

CREATE OR REPLACE FUNCTION public.hte_admin_get_analytics()
RETURNS TABLE(
  total_plays bigint,
  unique_listeners bigint,
  ad_impressions bigint,
  published_tracks bigint,
  total_tracks bigint,
  top_track_title text,
  top_track_plays bigint
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
  SELECT * FROM private.hte_admin_get_analytics();
$function$;

CREATE OR REPLACE FUNCTION public.hte_admin_list_users()
RETURNS TABLE(
  id uuid,
  email text,
  created_at timestamp with time zone,
  last_sign_in_at timestamp with time zone,
  role text
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
  SELECT * FROM private.hte_admin_list_users();
$function$;

REVOKE ALL ON FUNCTION public.hte_admin_get_analytics() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.hte_admin_list_users() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hte_admin_get_analytics() TO authenticated;
GRANT EXECUTE ON FUNCTION public.hte_admin_list_users() TO authenticated;