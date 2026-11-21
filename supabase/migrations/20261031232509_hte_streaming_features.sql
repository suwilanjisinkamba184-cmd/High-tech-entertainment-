-- Reconstructed from the live HTE streaming and member-library tables.
CREATE TABLE IF NOT EXISTS public.artist_follows (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  artist_id uuid NOT NULL REFERENCES public.artists(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, artist_id)
);
CREATE INDEX IF NOT EXISTS artist_follows_artist_id_idx ON public.artist_follows (artist_id);

CREATE TABLE IF NOT EXISTS public.user_favorites (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  track_id uuid NOT NULL REFERENCES public.tracks(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, track_id)
);
CREATE INDEX IF NOT EXISTS user_favorites_track_id_idx ON public.user_favorites (track_id);

CREATE TABLE IF NOT EXISTS public.playlists (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  description text,
  is_public boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS playlists_user_id_idx ON public.playlists (user_id);

CREATE TABLE IF NOT EXISTS public.playlist_tracks (
  playlist_id uuid NOT NULL REFERENCES public.playlists(id) ON DELETE CASCADE,
  track_id uuid NOT NULL REFERENCES public.tracks(id) ON DELETE CASCADE,
  position integer NOT NULL DEFAULT 0,
  added_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (playlist_id, track_id)
);
CREATE INDEX IF NOT EXISTS playlist_tracks_track_id_idx ON public.playlist_tracks (track_id);

CREATE TABLE IF NOT EXISTS public.listening_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  track_id uuid NOT NULL REFERENCES public.tracks(id) ON DELETE CASCADE,
  played_at timestamptz NOT NULL DEFAULT now(),
  completed boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS listening_history_track_id_idx ON public.listening_history (track_id);
CREATE INDEX IF NOT EXISTS listening_history_user_played_idx ON public.listening_history (user_id, played_at DESC);

ALTER TABLE public.artist_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_tracks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.listening_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users manage own artist follows" ON public.artist_follows;
CREATE POLICY "Users manage own artist follows" ON public.artist_follows FOR ALL TO authenticated
  USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
DROP POLICY IF EXISTS "Users manage own favorites" ON public.user_favorites;
CREATE POLICY "Users manage own favorites" ON public.user_favorites FOR ALL TO authenticated
  USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
DROP POLICY IF EXISTS "Users manage own playlists" ON public.playlists;
CREATE POLICY "Users manage own playlists" ON public.playlists FOR ALL TO authenticated
  USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
DROP POLICY IF EXISTS "Playlist tracks visible for owner or public playlist" ON public.playlist_tracks;
CREATE POLICY "Playlist tracks visible for owner or public playlist" ON public.playlist_tracks FOR SELECT TO anon, authenticated
  USING (EXISTS (SELECT 1 FROM public.playlists p WHERE p.id = playlist_tracks.playlist_id AND (p.is_public OR p.user_id = (SELECT auth.uid()))));
DROP POLICY IF EXISTS "Users add tracks to own playlists" ON public.playlist_tracks;
CREATE POLICY "Users add tracks to own playlists" ON public.playlist_tracks FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM public.playlists p WHERE p.id = playlist_tracks.playlist_id AND p.user_id = (SELECT auth.uid())));
DROP POLICY IF EXISTS "Users update tracks in own playlists" ON public.playlist_tracks;
CREATE POLICY "Users update tracks in own playlists" ON public.playlist_tracks FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM public.playlists p WHERE p.id = playlist_tracks.playlist_id AND p.user_id = (SELECT auth.uid())))
  WITH CHECK (EXISTS (SELECT 1 FROM public.playlists p WHERE p.id = playlist_tracks.playlist_id AND p.user_id = (SELECT auth.uid())));
DROP POLICY IF EXISTS "Users remove tracks from own playlists" ON public.playlist_tracks;
CREATE POLICY "Users remove tracks from own playlists" ON public.playlist_tracks FOR DELETE TO authenticated
  USING (EXISTS (SELECT 1 FROM public.playlists p WHERE p.id = playlist_tracks.playlist_id AND p.user_id = (SELECT auth.uid())));
DROP POLICY IF EXISTS "Users manage own listening history" ON public.listening_history;
CREATE POLICY "Users manage own listening history" ON public.listening_history FOR ALL TO authenticated
  USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);

REVOKE ALL ON TABLE public.artist_follows, public.user_favorites, public.playlists, public.playlist_tracks, public.listening_history FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.artist_follows, public.user_favorites, public.playlists, public.listening_history TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.artist_follows, public.user_favorites, public.playlists, public.playlist_tracks, public.listening_history TO service_role;
GRANT SELECT ON TABLE public.playlist_tracks TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.playlist_tracks TO authenticated;
