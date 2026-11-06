-- High-Tech Entertainment website priority-one backend migration.
-- Safe to rerun. Creates the videos table expected by the public video catalogue
-- and Founder Studio. Founder writes are restricted to the confirmed owner UUID.
create extension if not exists pgcrypto;

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(btrim(title)) between 1 and 150),
  artist text not null default '' check (char_length(artist) <= 150),
  description text not null default '' check (char_length(description) <= 3000),
  video_url text not null check (video_url ~ '^https://'),
  thumbnail_url text,
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists videos_published_created_idx
  on public.videos (created_at desc)
  where is_published = true;

alter table public.videos enable row level security;
revoke all on public.videos from anon, authenticated;
grant select on public.videos to anon, authenticated;
grant insert, update, delete on public.videos to authenticated;

drop policy if exists "Public and founder can read videos" on public.videos;
create policy "Public and founder can read videos" on public.videos
  for select to anon, authenticated
  using (
    is_published = true
    or (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
  );

drop policy if exists "HTE founder inserts videos" on public.videos;
create policy "HTE founder inserts videos" on public.videos
  for insert to authenticated
  with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE founder updates videos" on public.videos;
create policy "HTE founder updates videos" on public.videos
  for update to authenticated
  using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
  with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE founder deletes videos" on public.videos;
create policy "HTE founder deletes videos" on public.videos
  for delete to authenticated
  using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

-- Both bucket IDs are retained because the web and native Founder Studio
-- currently use different historical names.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('hte-video','hte-video',true,524288000,array['video/mp4','video/x-m4v','video/quicktime','video/webm']),
  ('hte-videos','hte-videos',true,524288000,array['video/mp4','video/x-m4v','video/quicktime','video/webm'])
on conflict (id) do update set
  public = true,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE videos" on storage.objects;
create policy "Public can read HTE videos" on storage.objects
  for select to anon, authenticated
  using (bucket_id in ('hte-video','hte-videos'));

drop policy if exists "HTE administrators upload videos" on storage.objects;
create policy "HTE administrators upload videos" on storage.objects
  for insert to authenticated
  with check (
    bucket_id in ('hte-video','hte-videos')
    and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
  );

drop policy if exists "HTE administrators update videos" on storage.objects;
create policy "HTE administrators update videos" on storage.objects
  for update to authenticated
  using (
    bucket_id in ('hte-video','hte-videos')
    and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
  )
  with check (
    bucket_id in ('hte-video','hte-videos')
    and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
  );

drop policy if exists "HTE administrators delete videos" on storage.objects;
create policy "HTE administrators delete videos" on storage.objects
  for delete to authenticated
  using (
    bucket_id in ('hte-video','hte-videos')
    and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
  );
