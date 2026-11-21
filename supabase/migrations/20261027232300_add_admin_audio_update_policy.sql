-- Reconstructed squashed baseline for the earliest applied HTE migration version.
-- The current checked-in setup and admin-policy scripts are consolidated here so a
-- clean replay creates the base catalogue and the final initial founder policies.
-- The live project already records version 20261027232300, so this file is skipped
-- there; later source migrations retain their historical versions.
-- High-Tech Entertainment Supabase baseline schema.
-- Review before running. This uses the table names discussed for this project.
-- Run in Supabase Dashboard > SQL Editor. Never put a service_role/secret key in the browser.

create extension if not exists pgcrypto;

create table if not exists public.artists (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique default ('artist-' || gen_random_uuid()::text),
  bio text not null default '',
  image_url text,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.releases (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  slug text not null unique default ('release-' || gen_random_uuid()::text),
  artist_id uuid references public.artists(id) on delete set null,
  cover_url text,
  release_date date,
  spotify_url text,
  description text not null default '',
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.tracks (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  artist text not null default 'Unknown artist',
  artist_id uuid references public.artists(id) on delete set null,
  release_id uuid references public.releases(id) on delete set null,
  audio_url text,
  cover_url text,
  genre text,
  uploader text not null default 'High Tech Entertainment',
  duration_seconds integer,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

-- Video catalogue existed in production before the native 3.1 migration.
-- Reconstructed here so a clean chronological replay creates the table before
-- the November 7 Founder policy migration modifies its write policies.
create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text,
  artist text,
  video_url text not null,
  thumbnail_url text,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.videos enable row level security;

drop policy if exists "Public can read published videos" on public.videos;
create policy "Public can read published videos" on public.videos
  for select to anon, authenticated using (is_published = true);

drop policy if exists "HTE administrators insert videos" on public.videos;
create policy "HTE administrators insert videos" on public.videos
  for insert to authenticated
  with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE administrators update videos" on public.videos;
create policy "HTE administrators update videos" on public.videos
  for update to authenticated
  using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
  with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE administrators delete videos" on public.videos;
create policy "HTE administrators delete videos" on public.videos
  for delete to authenticated
  using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

revoke all on public.videos from anon, authenticated;
grant select on public.videos to anon, authenticated;
grant insert, update, delete on public.videos to authenticated;
grant all on public.videos to service_role;

create table if not exists public.contact_messages (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 100),
  email text not null check (char_length(email) between 3 and 254),
  subject text not null default '' check (char_length(subject) <= 160),
  message text not null check (char_length(message) between 1 and 5000),
  created_at timestamptz not null default now()
);

-- Keep this baseline safe to rerun against existing projects.
alter table public.releases add column if not exists spotify_url text;
alter table public.tracks add column if not exists genre text;

alter table public.artists enable row level security;
alter table public.releases enable row level security;
alter table public.tracks enable row level security;
alter table public.contact_messages enable row level security;

-- Index catalog foreign keys used for joins and deletes.
create index if not exists tracks_artist_id_idx on public.tracks (artist_id);
create index if not exists tracks_release_id_idx on public.tracks (release_id);

-- Public visitors can only read published content.
drop policy if exists "Public can read published artists" on public.artists;
create policy "Public can read published artists" on public.artists for select to anon, authenticated using (is_published = true);
drop policy if exists "Public can read published releases" on public.releases;
create policy "Public can read published releases" on public.releases for select to anon, authenticated using (is_published = true);
drop policy if exists "Public can read published tracks" on public.tracks;
create policy "Public can read published tracks" on public.tracks for select to anon, authenticated using (is_published = true);

-- Public visitors can send a message but cannot read messages back.
drop policy if exists "Visitors can submit contact messages" on public.contact_messages;
create policy "Visitors can submit contact messages" on public.contact_messages for insert to anon, authenticated with check (
  char_length(name) between 1 and 100 and
  char_length(email) between 3 and 254 and
  char_length(subject) <= 160 and
  char_length(message) between 1 and 5000
);

-- Only authenticated users can insert/update/delete catalog rows by default only if an
-- explicit policy is added. For this project, admin operations should be performed through
-- a separately secured server or carefully restricted policies, not an open public policy.
revoke all on public.artists from anon, authenticated;
revoke all on public.releases from anon, authenticated;
revoke all on public.tracks from anon, authenticated;
revoke all on public.contact_messages from anon, authenticated;
grant select on public.artists, public.releases, public.tracks to anon, authenticated;
grant insert on public.contact_messages to anon, authenticated;

-- Storage bucket for public audio. Upload policy is intentionally not opened to everyone.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-audio', 'hte-audio', true, 104857600,
  array['audio/mpeg','audio/mp3','audio/mp4','audio/x-m4a','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/flac','audio/webm'])
on conflict (id) do update set public = true, file_size_limit = 104857600,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE audio" on storage.objects;
create policy "Public can read HTE audio" on storage.objects for select to anon, authenticated using (bucket_id = 'hte-audio');
-- Do not add public upload policies. Configure authenticated admin uploads only after admin role checks are established.


-- High-Tech Entertainment admin policies.
-- Founder-only authorization is bound to the confirmed Supabase Auth user UUID.
-- Founder email (for reference): suwilanjisinkamba184@gmail.com
-- Founder auth.uid(): b268df9e-eff5-43b9-a38f-869b33add38b
-- Run supabase-setup.sql first, then run this file.
-- The website uses the bucket ID hte-audio. A separately created HT Storage bucket is not used by this code.
-- Do NOT enable open/public INSERT or UPDATE policies for catalog tables.

-- Remove unnecessary client privileges from the unused, empty public.public table if present.
DO $do$
BEGIN
  IF to_regclass('public.public') IS NOT NULL THEN
    REVOKE ALL PRIVILEGES ON TABLE public.public FROM anon, authenticated, PUBLIC;
    EXECUTE 'DROP POLICY IF EXISTS "Deny client access to unused public table" ON public.public';
    EXECUTE 'CREATE POLICY "Deny client access to unused public table" ON public.public FOR ALL TO anon, authenticated USING (false) WITH CHECK (false)';
  END IF;
END
$do$;

-- Ensure the bucket used by the website exists; this is safe to rerun.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-audio', 'hte-audio', true, 104857600, array['audio/mpeg','audio/mp4','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/webm'])
on conflict (id) do update set public = true, file_size_limit = 104857600, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE audio" on storage.objects;
create policy "Public can read HTE audio" on storage.objects for select to anon, authenticated using (bucket_id = 'hte-audio');
drop policy if exists "HTE administrators upload audio" on storage.objects;
create policy "HTE administrators upload audio" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE administrators update audio" on storage.objects;
create policy "HTE administrators update audio" on storage.objects
for update to authenticated
using (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
)
with check (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE administrators delete audio" on storage.objects;
create policy "HTE administrators delete audio" on storage.objects
for delete to authenticated
using (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE administrators insert tracks" on public.tracks;
create policy "HTE administrators insert tracks" on public.tracks
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE administrators update tracks" on public.tracks;
create policy "HTE administrators update tracks" on public.tracks
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE administrators delete tracks" on public.tracks;
create policy "HTE administrators delete tracks" on public.tracks
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

-- Admin writes for artists/releases are separate from public SELECT policies.
drop policy if exists "HTE admin manages artists" on public.artists;
drop policy if exists "HTE administrators insert artists" on public.artists;
create policy "HTE administrators insert artists" on public.artists
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE administrators update artists" on public.artists;
create policy "HTE administrators update artists" on public.artists
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE administrators delete artists" on public.artists;
create policy "HTE administrators delete artists" on public.artists
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE admin manages releases" on public.releases;
drop policy if exists "HTE administrators insert releases" on public.releases;
create policy "HTE administrators insert releases" on public.releases
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE administrators update releases" on public.releases;
create policy "HTE administrators update releases" on public.releases
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE administrators delete releases" on public.releases;
create policy "HTE administrators delete releases" on public.releases
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

-- One SELECT policy per catalog table: public visitors see published rows;
-- the founder can also see unpublished drafts.
DROP POLICY IF EXISTS "HTE founder can read all tracks" ON public.tracks;
DROP POLICY IF EXISTS "Public can read published tracks" ON public.tracks;
CREATE POLICY "Public and founder can read tracks" ON public.tracks
FOR SELECT TO anon, authenticated
USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

DROP POLICY IF EXISTS "HTE founder can read all artists" ON public.artists;
DROP POLICY IF EXISTS "Public can read published artists" ON public.artists;
CREATE POLICY "Public and founder can read artists" ON public.artists
FOR SELECT TO anon, authenticated
USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

DROP POLICY IF EXISTS "HTE founder can read all releases" ON public.releases;
DROP POLICY IF EXISTS "Public can read published releases" ON public.releases;
CREATE POLICY "Public and founder can read releases" ON public.releases
FOR SELECT TO anon, authenticated
USING (is_published = true OR (SELECT auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

grant select on public.tracks, public.artists, public.releases to authenticated;

-- Required SQL privileges for authenticated admin actions; RLS policies above enforce the founder user ID restriction.
grant insert, update, delete on public.tracks to authenticated;
grant insert, update, delete on public.artists to authenticated;
grant insert, update, delete on public.releases to authenticated;

-- Founder can read incoming contact enquiries; visitors cannot read messages.
drop policy if exists "HTE founder can read contact messages" on public.contact_messages;
create policy "HTE founder can read contact messages" on public.contact_messages
for select to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
grant select on public.contact_messages to authenticated;

-- The extra "HT Storage" bucket is not used by the website. Keep it private if empty.
update storage.buckets
set public = false
where id = 'ht storage'
  and not exists (select 1 from storage.objects where bucket_id = 'ht storage');


-- Publicly readable artwork with founder-only uploads, updates and deletes.
-- The Android founder studio uploads images to hte-artwork/releases.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'hte-artwork',
  'hte-artwork',
  true,
  20971520,
  array['image/jpeg','image/png','image/webp','image/avif']
)
on conflict (id) do update
set public = true,
    file_size_limit = 20971520,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE artwork" on storage.objects;
create policy "Public can read HTE artwork" on storage.objects
for select to anon, authenticated
using (bucket_id = 'hte-artwork');

drop policy if exists "HTE administrators upload artwork" on storage.objects;
create policy "HTE administrators upload artwork" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'hte-artwork'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE administrators update artwork" on storage.objects;
create policy "HTE administrators update artwork" on storage.objects
for update to authenticated
using (
  bucket_id = 'hte-artwork'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
)
with check (
  bucket_id = 'hte-artwork'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE administrators delete artwork" on storage.objects;
create policy "HTE administrators delete artwork" on storage.objects
for delete to authenticated
using (
  bucket_id = 'hte-artwork'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

