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
drop policy if exists "HTE admin can upload audio" on storage.objects;
create policy "HTE admin can upload audio" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE admin can update audio" on storage.objects;
create policy "HTE admin can update audio" on storage.objects
for update to authenticated
using (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
)
with check (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE admin can delete audio" on storage.objects;
create policy "HTE admin can delete audio" on storage.objects
for delete to authenticated
using (
  bucket_id = 'hte-audio'
  and (select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid
);

drop policy if exists "HTE admin can insert tracks" on public.tracks;
create policy "HTE admin can insert tracks" on public.tracks
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE admin can update tracks" on public.tracks;
create policy "HTE admin can update tracks" on public.tracks
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE admin can delete tracks" on public.tracks;
create policy "HTE admin can delete tracks" on public.tracks
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

-- Admin writes for artists/releases are separate from public SELECT policies.
drop policy if exists "HTE admin manages artists" on public.artists;
drop policy if exists "HTE admin inserts artists" on public.artists;
create policy "HTE admin inserts artists" on public.artists
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE admin updates artists" on public.artists;
create policy "HTE admin updates artists" on public.artists
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE admin deletes artists" on public.artists;
create policy "HTE admin deletes artists" on public.artists
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

drop policy if exists "HTE admin manages releases" on public.releases;
drop policy if exists "HTE admin inserts releases" on public.releases;
create policy "HTE admin inserts releases" on public.releases
for insert to authenticated
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE admin updates releases" on public.releases;
create policy "HTE admin updates releases" on public.releases
for update to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid)
with check ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);
drop policy if exists "HTE admin deletes releases" on public.releases;
create policy "HTE admin deletes releases" on public.releases
for delete to authenticated
using ((select auth.uid()) = 'b268df9e-eff5-43b9-a38f-869b33add38b'::uuid);

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
