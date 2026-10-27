-- High-Tech Entertainment admin policies.
-- Admin email configured for this project: suwilanjisinkamba184@gmail.com
-- Run supabase-setup.sql first, then run this file.
-- The website uses the bucket ID hte-audio. A separately created HT Storage bucket is not used by this code.
-- Do NOT enable open/public INSERT or UPDATE policies for catalog tables.

-- Ensure the bucket used by the website exists; this is safe to rerun.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-audio', 'hte-audio', true, 104857600, array['audio/mpeg','audio/mp4','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/webm'])
on conflict (id) do update set public = true, file_size_limit = 104857600, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Public can read HTE audio" on storage.objects;
create policy "Public can read HTE audio" on storage.objects for select to anon, authenticated using (bucket_id = 'hte-audio');
drop policy if exists "HTE admin can upload audio" on storage.objects;
create policy "HTE admin can upload audio" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'hte-audio'
  and (auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com'
);

drop policy if exists "HTE admin can update audio" on storage.objects;
create policy "HTE admin can update audio" on storage.objects
for update to authenticated
using (
  bucket_id = 'hte-audio'
  and (auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com'
)
with check (
  bucket_id = 'hte-audio'
  and (auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com'
);

drop policy if exists "HTE admin can delete audio" on storage.objects;
create policy "HTE admin can delete audio" on storage.objects
for delete to authenticated
using (
  bucket_id = 'hte-audio'
  and (auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com'
);

drop policy if exists "HTE admin can insert tracks" on public.tracks;
create policy "HTE admin can insert tracks" on public.tracks
for insert to authenticated
with check ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com');

drop policy if exists "HTE admin can update tracks" on public.tracks;
create policy "HTE admin can update tracks" on public.tracks
for update to authenticated
using ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com')
with check ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com');

drop policy if exists "HTE admin can delete tracks" on public.tracks;
create policy "HTE admin can delete tracks" on public.tracks
for delete to authenticated
using ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com');

-- Optional admin management for artists and releases (same email restriction).
drop policy if exists "HTE admin manages artists" on public.artists;
create policy "HTE admin manages artists" on public.artists
for all to authenticated
using ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com')
with check ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com');

drop policy if exists "HTE admin manages releases" on public.releases;
create policy "HTE admin manages releases" on public.releases
for all to authenticated
using ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com')
with check ((auth.jwt() ->> 'email') = 'suwilanjisinkamba184@gmail.com');

-- Required SQL privileges for authenticated admin actions; RLS policies above still enforce the email restriction.
grant insert, update, delete on public.tracks to authenticated;
grant insert, update, delete on public.artists to authenticated;
grant insert, update, delete on public.releases to authenticated;
