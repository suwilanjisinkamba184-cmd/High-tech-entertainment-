-- High-Tech Entertainment 3.1 additive native-feature schema.
-- Safe migration: creates only new tables/indexes/policies; does not reset or rewrite existing data.
-- Run in Supabase SQL Editor after reviewing. Existing founder authorization UUID is reused
-- from supabase-admin-policies.sql. Never expose a service-role key in the Android client.

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 180),
  description text not null default '',
  video_url text not null,
  thumbnail_url text,
  artist text not null default 'High-Tech Entertainment',
  duration_seconds integer check (duration_seconds is null or duration_seconds > 0),
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.user_favourites (
  user_id uuid not null references auth.users(id) on delete cascade,
  track_id uuid not null references public.tracks(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, track_id)
);

create table if not exists public.user_playlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  description text not null default '',
  is_public boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.user_playlist_tracks (
  playlist_id uuid not null references public.user_playlists(id) on delete cascade,
  track_id uuid not null references public.tracks(id) on delete cascade,
  position integer not null default 0 check (position >= 0),
  added_at timestamptz not null default now(),
  primary key (playlist_id, track_id)
);

create table if not exists public.listening_history (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  track_id uuid not null references public.tracks(id) on delete cascade,
  played_at timestamptz not null default now(),
  progress_seconds integer not null default 0 check (progress_seconds >= 0)
);

create table if not exists public.bookings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete set null,
  name text not null check (char_length(name) between 1 and 100),
  email text not null check (char_length(email) between 3 and 254),
  booking_type text not null check (char_length(booking_type) between 2 and 80),
  requested_date date,
  message text not null default '' check (char_length(message) <= 3000),
  status text not null default 'pending' check (status in ('pending','confirmed','declined','completed','cancelled')),
  created_at timestamptz not null default now()
);

create index if not exists videos_published_created_idx on public.videos (is_published, created_at desc);
create index if not exists playlists_user_updated_idx on public.user_playlists (user_id, updated_at desc);
create index if not exists playlist_tracks_order_idx on public.user_playlist_tracks (playlist_id, position);
create index if not exists listening_history_user_played_idx on public.listening_history (user_id, played_at desc);
create index if not exists bookings_user_created_idx on public.bookings (user_id, created_at desc);

alter table public.videos enable row level security;
alter table public.user_favourites enable row level security;
alter table public.user_playlists enable row level security;
alter table public.user_playlist_tracks enable row level security;
alter table public.listening_history enable row level security;
alter table public.bookings enable row level security;

-- Published video catalogue is public. Only the existing founder identity can manage videos.
drop policy if exists "Public can read published videos" on public.videos;
create policy "Public can read published videos" on public.videos for select to anon, authenticated
using (is_published = true or (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "Founder manages videos" on public.videos;
create policy "Founder manages videos" on public.videos for all to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
grant select on public.videos to anon, authenticated;
grant insert, update, delete on public.videos to authenticated;

-- Private library data is scoped to the authenticated owner.
drop policy if exists "Users manage own favourites" on public.user_favourites;
create policy "Users manage own favourites" on public.user_favourites for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "Users manage own playlists" on public.user_playlists;
create policy "Users manage own playlists" on public.user_playlists for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "Users manage tracks in own playlists" on public.user_playlist_tracks;
create policy "Users manage tracks in own playlists" on public.user_playlist_tracks for all to authenticated
using (exists (select 1 from public.user_playlists p where p.id = playlist_id and p.user_id = (select auth.uid())))
with check (exists (select 1 from public.user_playlists p where p.id = playlist_id and p.user_id = (select auth.uid())));
drop policy if exists "Users manage own listening history" on public.listening_history;
create policy "Users manage own listening history" on public.listening_history for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
grant select, insert, update, delete on public.user_favourites, public.user_playlists,
  public.user_playlist_tracks, public.listening_history to authenticated;
grant usage, select on sequence public.listening_history_id_seq to authenticated;

-- Authenticated users can submit bookings and see only their own; founder can process all.
drop policy if exists "Users submit bookings" on public.bookings;
create policy "Users submit bookings" on public.bookings for insert to authenticated
with check (user_id = (select auth.uid()));
drop policy if exists "Users read own bookings" on public.bookings;
create policy "Users read own bookings" on public.bookings for select to authenticated
using (user_id = (select auth.uid()) or (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "Founder updates bookings" on public.bookings;
create policy "Founder updates bookings" on public.bookings for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
grant select, insert on public.bookings to authenticated;
grant update on public.bookings to authenticated;

-- No anonymous booking insert. Public visitors must authenticate before submitting a booking.
