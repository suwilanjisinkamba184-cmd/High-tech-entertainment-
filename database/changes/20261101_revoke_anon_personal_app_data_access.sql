-- Least-privilege cleanup for native app user-owned data.
-- Anonymous visitors must not receive table-level access to per-user activity.
-- Authenticated users retain their existing grants; RLS continues to restrict
-- each row to its owner.
REVOKE ALL PRIVILEGES ON TABLE
  public.user_favorites,
  public.listening_history,
  public.ad_impressions
FROM anon;
