-- High-Tech Entertainment 3.1 additive booking workflow migration.
-- Production already contains videos, profiles, user_favorites, playlists,
-- playlist_tracks, listening_history and audio_ads. Do not recreate them.
-- Existing private.hte_has_role() is the authoritative owner/admin check.

create table if not exists public.bookings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 100),
  email text not null check (char_length(trim(email)) between 3 and 254),
  booking_type text not null check (char_length(trim(booking_type)) between 2 and 80),
  requested_date date,
  message text not null default '' check (char_length(message) <= 3000),
  status text not null default 'pending'
    check (status in ('pending','confirmed','declined','completed','cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists bookings_user_created_idx
  on public.bookings (user_id, created_at desc);
create index if not exists bookings_status_created_idx
  on public.bookings (status, created_at desc);

alter table public.bookings enable row level security;

drop policy if exists "Users submit own bookings" on public.bookings;
create policy "Users submit own bookings" on public.bookings
  for insert to authenticated
  with check (user_id = (select auth.uid()));

drop policy if exists "Users read own bookings" on public.bookings;
create policy "Users read own bookings" on public.bookings
  for select to authenticated
  using (user_id = (select auth.uid()) or private.hte_has_role(array['owner','admin']));

drop policy if exists "Admins process bookings" on public.bookings;
create policy "Admins process bookings" on public.bookings
  for update to authenticated
  using (private.hte_has_role(array['owner','admin']))
  with check (private.hte_has_role(array['owner','admin']));

grant select, insert on public.bookings to authenticated;
grant update on public.bookings to authenticated;

-- Keep private library tables aligned with the live schema:
-- user_favorites(user_id, track_id), playlists, playlist_tracks,
-- listening_history(user_id, track_id, played_at, completed).
-- Existing RLS policies already restrict these records to their owners.
-- Video catalogue, scheduled audio_ads and ad_impressions already have
-- role-based policies and are intentionally not weakened by this migration.

-- Add release description used by Founder Studio and the public release page.
alter table public.releases add column if not exists description text not null default '';

-- Public avatar delivery; uploads/updates/deletes remain restricted to each owner's UUID folder.
update storage.buckets
set public = true,
    file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg','image/png','image/webp']
where id = 'hte-avatars';

drop policy if exists "Public can read HTE avatar files" on storage.objects;
create policy "Public can read HTE avatar files" on storage.objects
for select to anon, authenticated
using (bucket_id = 'hte-avatars');

-- Existing per-user INSERT/UPDATE/DELETE policies remain in place.
