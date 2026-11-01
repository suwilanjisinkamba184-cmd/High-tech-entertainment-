-- HTE policy and index cleanup following role-based admin access.
-- The private role table intentionally has no client-readable rows; an explicit
-- deny policy documents that boundary for database advisors.
drop policy if exists "Deny all client access to HTE role assignments" on private.user_roles;
create policy "Deny all client access to HTE role assignments"
on private.user_roles for all to anon, authenticated
using (false) with check (false);
create index if not exists user_roles_created_by_idx on private.user_roles(created_by);

-- One combined read policy avoids duplicate permissive SELECT evaluation.
drop policy if exists "Users read own ad impressions" on public.ad_impressions;
drop policy if exists "HTE administrators can read ad impressions" on public.ad_impressions;
drop policy if exists "Users and admins read ad impressions" on public.ad_impressions;
create policy "Users and admins read ad impressions" on public.ad_impressions
for select to authenticated
using (user_id = (select auth.uid()) or private.hte_has_role(array['owner','admin']));

-- Keep one SELECT policy for playlist_tracks. Write permissions remain owner-scoped.
drop policy if exists "Users manage tracks in own playlists" on public.playlist_tracks;
drop policy if exists "Users add tracks to own playlists" on public.playlist_tracks;
drop policy if exists "Users update tracks in own playlists" on public.playlist_tracks;
drop policy if exists "Users remove tracks from own playlists" on public.playlist_tracks;
create policy "Users add tracks to own playlists" on public.playlist_tracks
for insert to authenticated with check (
  exists (select 1 from public.playlists p
    where p.id = playlist_tracks.playlist_id and p.user_id = (select auth.uid()))
);
create policy "Users update tracks in own playlists" on public.playlist_tracks
for update to authenticated using (
  exists (select 1 from public.playlists p
    where p.id = playlist_tracks.playlist_id and p.user_id = (select auth.uid()))
) with check (
  exists (select 1 from public.playlists p
    where p.id = playlist_tracks.playlist_id and p.user_id = (select auth.uid()))
);
create policy "Users remove tracks from own playlists" on public.playlist_tracks
for delete to authenticated using (
  exists (select 1 from public.playlists p
    where p.id = playlist_tracks.playlist_id and p.user_id = (select auth.uid()))
);
