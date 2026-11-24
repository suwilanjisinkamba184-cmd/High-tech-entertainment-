create or replace function public.hte_admin_get_analytics()
returns table (
  total_plays bigint,
  unique_listeners bigint,
  ad_impressions bigint,
  published_tracks bigint,
  total_tracks bigint,
  top_track_title text,
  top_track_plays bigint
)
language plpgsql
security definer
set search_path = ''
as $$
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
$$;

revoke all on function public.hte_admin_get_analytics() from public, anon;
grant execute on function public.hte_admin_get_analytics() to authenticated;
