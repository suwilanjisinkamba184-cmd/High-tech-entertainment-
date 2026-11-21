-- HTE catalogue links, privacy-safe audience events, and branded-download readiness.
-- Additive migration: existing catalogue rows and ownership policies remain intact.

ALTER TABLE public.tracks
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS source_platform text NOT NULL DEFAULT 'direct'
    CHECK (source_platform IN ('direct','youtube','spotify','external')),
  ADD COLUMN IF NOT EXISTS rights_status text NOT NULL DEFAULT 'legacy_existing'
    CHECK (rights_status IN ('legacy_existing','owned_or_licensed','external_link','stream_only','review_required')),
  ADD COLUMN IF NOT EXISTS download_allowed boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS branded_audio_url text,
  ADD COLUMN IF NOT EXISTS download_branding_status text NOT NULL DEFAULT 'legacy_unbranded'
    CHECK (download_branding_status IN ('pending','ready','failed','not_applicable','legacy_unbranded'));

-- New uploads must explicitly opt into download rights; existing rows retain their prior flag.
ALTER TABLE public.tracks
  ALTER COLUMN download_allowed SET DEFAULT false,
  ALTER COLUMN download_branding_status SET DEFAULT 'pending';

CREATE UNIQUE INDEX IF NOT EXISTS tracks_source_url_unique
  ON public.tracks (lower(trim(source_url)))
  WHERE source_url IS NOT NULL AND length(trim(source_url)) > 0;

CREATE INDEX IF NOT EXISTS hte_tracks_published_created_idx
  ON public.tracks (is_published, created_at DESC);

CREATE TABLE IF NOT EXISTS public.hte_audience_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  visitor_id uuid NOT NULL,
  session_id uuid NOT NULL,
  event_type text NOT NULL CHECK (event_type IN ('page_view','track_play','track_download','artist_view','release_view')),
  track_id uuid REFERENCES public.tracks(id) ON DELETE SET NULL,
  artist_id uuid REFERENCES public.artists(id) ON DELETE SET NULL,
  page_path text NOT NULL DEFAULT '/' CHECK (char_length(page_path) <= 300),
  source text NOT NULL DEFAULT 'website' CHECK (source IN ('website','android')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hte_audience_events_type_created_idx
  ON public.hte_audience_events (event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS hte_audience_events_visitor_created_idx
  ON public.hte_audience_events (visitor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS hte_audience_events_track_created_idx
  ON public.hte_audience_events (track_id, created_at DESC)
  WHERE track_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS hte_audience_events_artist_created_idx
  ON public.hte_audience_events (artist_id, created_at DESC)
  WHERE artist_id IS NOT NULL;

ALTER TABLE public.hte_audience_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can record privacy-safe audience events" ON public.hte_audience_events;
CREATE POLICY "Public can record privacy-safe audience events"
  ON public.hte_audience_events
  FOR INSERT TO anon, authenticated
  WITH CHECK (
    event_type IN ('page_view','track_play','track_download','artist_view','release_view')
    AND char_length(page_path) <= 300
    AND (track_id IS NULL OR EXISTS (
      SELECT 1 FROM public.tracks t WHERE t.id = track_id AND t.is_published = true
    ))
    AND (artist_id IS NULL OR EXISTS (
      SELECT 1 FROM public.artists a WHERE a.id = artist_id AND a.is_published = true
    ))
  );

DROP POLICY IF EXISTS "HTE administrators can read audience events" ON public.hte_audience_events;
CREATE POLICY "HTE administrators can read audience events"
  ON public.hte_audience_events
  FOR SELECT TO authenticated
  USING (private.hte_has_role(ARRAY['owner','admin']));

REVOKE ALL ON TABLE public.hte_audience_events FROM PUBLIC;
GRANT INSERT ON TABLE public.hte_audience_events TO anon, authenticated;
GRANT SELECT ON TABLE public.hte_audience_events TO authenticated;

-- Existing founder analytics are extended rather than replaced with a parallel dashboard API.
DROP FUNCTION IF EXISTS public.hte_admin_get_analytics();
DROP FUNCTION IF EXISTS private.hte_admin_get_analytics();

CREATE FUNCTION private.hte_admin_get_analytics()
RETURNS TABLE(
  total_plays bigint,
  unique_listeners bigint,
  ad_impressions bigint,
  published_tracks bigint,
  total_tracks bigint,
  top_track_title text,
  top_track_plays bigint,
  page_views bigint,
  unique_viewers bigint,
  registered_members bigint,
  artist_subscribers bigint,
  track_downloads bigint,
  page_views_30d bigint
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
  with track_activity as (
    select h.track_id, count(*)::bigint as plays
    from public.listening_history h
    group by h.track_id
    union all
    select e.track_id, count(*)::bigint as plays
    from public.hte_audience_events e
    where e.event_type = 'track_play' and e.track_id is not null
    group by e.track_id
  ),
  top_track as (
    select t.title, sum(a.plays)::bigint as plays
    from track_activity a
    join public.tracks t on t.id = a.track_id
    group by t.id, t.title
    order by sum(a.plays) desc
    limit 1
  )
  select
    (select count(*)::bigint from public.listening_history)
      + (select count(*)::bigint from public.hte_audience_events where event_type = 'track_play'),
    (select count(distinct user_id)::bigint from public.listening_history),
    (select count(*)::bigint from public.ad_impressions),
    (select count(*)::bigint from public.tracks where is_published = true),
    (select count(*)::bigint from public.tracks),
    (select top_track.title from top_track),
    coalesce((select top_track.plays from top_track), 0::bigint),
    (select count(*)::bigint from public.hte_audience_events where event_type = 'page_view'),
    (select count(distinct visitor_id)::bigint from public.hte_audience_events where event_type = 'page_view'),
    (select count(*)::bigint from auth.users),
    (select count(distinct user_id)::bigint from public.artist_follows),
    (select count(*)::bigint from public.hte_audience_events where event_type = 'track_download'),
    (select count(*)::bigint from public.hte_audience_events
      where event_type = 'page_view' and created_at >= now() - interval '30 days');
end;
$function$;

REVOKE ALL ON FUNCTION private.hte_admin_get_analytics() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.hte_admin_get_analytics() TO authenticated;

CREATE FUNCTION public.hte_admin_get_analytics()
RETURNS TABLE(
  total_plays bigint,
  unique_listeners bigint,
  ad_impressions bigint,
  published_tracks bigint,
  total_tracks bigint,
  top_track_title text,
  top_track_plays bigint,
  page_views bigint,
  unique_viewers bigint,
  registered_members bigint,
  artist_subscribers bigint,
  track_downloads bigint,
  page_views_30d bigint
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
  SELECT * FROM private.hte_admin_get_analytics();
$function$;

REVOKE ALL ON FUNCTION public.hte_admin_get_analytics() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.hte_admin_get_analytics() TO authenticated;

-- Enable catalogue realtime updates only if the publication does not already include tracks.
DO $publication$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
     AND NOT EXISTS (
       SELECT 1 FROM pg_publication_tables
       WHERE pubname = 'supabase_realtime'
         AND schemaname = 'public'
         AND tablename = 'tracks'
     ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.tracks;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
     AND NOT EXISTS (
       SELECT 1 FROM pg_publication_tables
       WHERE pubname = 'supabase_realtime'
         AND schemaname = 'public'
         AND tablename = 'artists'
     ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.artists;
  END IF;
END
$publication$;
