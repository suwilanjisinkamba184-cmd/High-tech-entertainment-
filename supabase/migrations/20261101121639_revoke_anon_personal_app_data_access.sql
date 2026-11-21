-- Revoke anonymous access to personal application data while retaining
-- the intentionally public catalogue, public playlist-track reads, and contact inserts.
REVOKE ALL ON TABLE public.profiles, public.app_preferences, public.app_notifications,
  public.user_favorites, public.playlists, public.listening_history, public.bookings,
  public.ad_impressions
FROM PUBLIC, anon;
