-- High-Tech Entertainment Supabase baseline schema.
-- Review before running. This uses the table names discussed for this project.
-- Run in Supabase Dashboard > SQL Editor. Never put a service_role/secret key in the browser.

create extension if not exists pgcrypto;

create table if not exists public.artists (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  bio text not null default '',
  image_url text,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.releases (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  artist_id uuid references public.artists(id) on delete set null,
  cover_url text,
  release_date date,
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
  uploader text not null default 'High Tech Entertainment',
  duration_seconds integer,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.contact_messages (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 100),
  email text not null check (char_length(email) between 3 and 254),
  subject text not null default '' check (char_length(subject) <= 160),
  message text not null check (char_length(message) between 1 and 5000),
  created_at timestamptz not null default now()
);

alter table public.artists enable row level security;
alter table public.releases enable row level security;
alter table public.tracks enable row level security;
alter table public.contact_messages enable row level security;

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
  array['audio/mpeg','audio/mp4','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/webm'])
on conflict (id) do update set public = true, file_size_limit = 104857600,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE audio" on storage.objects;
create policy "Public can read HTE audio" on storage.objects for select to anon, authenticated using (bucket_id = 'hte-audio');
-- Do not add public upload policies. Configure authenticated admin uploads only after admin role checks are established.
