-- Remove unnecessary anonymous-role privileges from personal application data.
-- RLS already blocked access; revoking table grants also removes the unnecessary API surface.
revoke all privileges on table public.app_preferences from anon;
revoke all privileges on table public.artist_follows from anon;
revoke all privileges on table public.playlists from anon;
revoke insert, update, delete on table public.playlist_tracks from anon;
