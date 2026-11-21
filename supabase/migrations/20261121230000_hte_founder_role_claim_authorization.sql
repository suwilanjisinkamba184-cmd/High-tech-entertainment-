-- Replace hardcoded Founder UUID comparisons with a trusted Supabase Auth app_metadata role.
-- The role is assigned by this privileged migration; clients cannot edit app_metadata.
-- Existing users must refresh their auth session after this migration is applied.

create schema if not exists private;

create or replace function private.is_hte_founder()
returns boolean
language sql
stable
set search_path = ''
as $function$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'hte_role') = 'founder', false);
$function$;

revoke all on function private.is_hte_founder() from public;
grant usage on schema private to anon, authenticated;
grant execute on function private.is_hte_founder() to anon, authenticated;

do $founder$
declare
  founder_count integer;
begin
  select count(*) into founder_count
  from auth.users
  where lower(email) = lower('suwilanjisinkamba184@gmail.com');

  if founder_count <> 1 then
    raise exception 'Expected exactly one HTE Founder auth account; found %', founder_count;
  end if;

  update auth.users
  set raw_app_meta_data =
    coalesce(raw_app_meta_data, '{}'::jsonb) || jsonb_build_object('hte_role', 'founder')
  where lower(email) = lower('suwilanjisinkamba184@gmail.com');
end
$founder$;

-- Remove legacy and current Founder/catalog policies before recreating them.
drop policy if exists "HTE admin manages artists" on public.artists;
drop policy if exists "HTE admin manages releases" on public.releases;
drop policy if exists "HTE founder can read all tracks" on public.tracks;
drop policy if exists "HTE founder can read all artists" on public.artists;
drop policy if exists "HTE founder can read all releases" on public.releases;
drop policy if exists "Public can read published tracks" on public.tracks;
drop policy if exists "Public can read published artists" on public.artists;
drop policy if exists "Public can read published releases" on public.releases;
drop policy if exists "Public and founder can read tracks" on public.tracks;
drop policy if exists "Public and founder can read artists" on public.artists;
drop policy if exists "Public and founder can read releases" on public.releases;

create policy "Public and founder can read tracks" on public.tracks
for select to anon, authenticated
using (is_published = true or (select private.is_hte_founder()));

create policy "Public and founder can read artists" on public.artists
for select to anon, authenticated
using (is_published = true or (select private.is_hte_founder()));

create policy "Public and founder can read releases" on public.releases
for select to anon, authenticated
using (is_published = true or (select private.is_hte_founder()));

-- Founder-only catalogue writes.
drop policy if exists "HTE administrators insert tracks" on public.tracks;
create policy "HTE administrators insert tracks" on public.tracks
for insert to authenticated
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators update tracks" on public.tracks;
create policy "HTE administrators update tracks" on public.tracks
for update to authenticated
using ((select private.is_hte_founder()))
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators delete tracks" on public.tracks;
create policy "HTE administrators delete tracks" on public.tracks
for delete to authenticated
using ((select private.is_hte_founder()));

drop policy if exists "HTE administrators insert artists" on public.artists;
create policy "HTE administrators insert artists" on public.artists
for insert to authenticated
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators update artists" on public.artists;
create policy "HTE administrators update artists" on public.artists
for update to authenticated
using ((select private.is_hte_founder()))
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators delete artists" on public.artists;
create policy "HTE administrators delete artists" on public.artists
for delete to authenticated
using ((select private.is_hte_founder()));

drop policy if exists "HTE administrators insert releases" on public.releases;
create policy "HTE administrators insert releases" on public.releases
for insert to authenticated
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators update releases" on public.releases;
create policy "HTE administrators update releases" on public.releases
for update to authenticated
using ((select private.is_hte_founder()))
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators delete releases" on public.releases;
create policy "HTE administrators delete releases" on public.releases
for delete to authenticated
using ((select private.is_hte_founder()));

-- Founder-only video writes.
drop policy if exists "HTE administrators insert videos" on public.videos;
create policy "HTE administrators insert videos" on public.videos
for insert to authenticated
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators update videos" on public.videos;
create policy "HTE administrators update videos" on public.videos
for update to authenticated
using ((select private.is_hte_founder()))
with check ((select private.is_hte_founder()));

drop policy if exists "HTE administrators delete videos" on public.videos;
create policy "HTE administrators delete videos" on public.videos
for delete to authenticated
using ((select private.is_hte_founder()));

-- Founder-only contact-message reads.
drop policy if exists "HTE founder can read contact messages" on public.contact_messages;
create policy "HTE founder can read contact messages" on public.contact_messages
for select to authenticated
using ((select private.is_hte_founder()));

-- Founder-only audio storage writes; public reads remain unchanged.
drop policy if exists "HTE administrators upload audio" on storage.objects;
create policy "HTE administrators upload audio" on storage.objects
for insert to authenticated
with check (bucket_id = 'hte-audio' and (select private.is_hte_founder()));

drop policy if exists "HTE administrators update audio" on storage.objects;
create policy "HTE administrators update audio" on storage.objects
for update to authenticated
using (bucket_id = 'hte-audio' and (select private.is_hte_founder()))
with check (bucket_id = 'hte-audio' and (select private.is_hte_founder()));

drop policy if exists "HTE administrators delete audio" on storage.objects;
create policy "HTE administrators delete audio" on storage.objects
for delete to authenticated
using (bucket_id = 'hte-audio' and (select private.is_hte_founder()));

-- Founder-only artwork storage writes; public reads remain unchanged.
drop policy if exists "HTE administrators upload artwork" on storage.objects;
create policy "HTE administrators upload artwork" on storage.objects
for insert to authenticated
with check (bucket_id = 'hte-artwork' and (select private.is_hte_founder()));

drop policy if exists "HTE administrators update artwork" on storage.objects;
create policy "HTE administrators update artwork" on storage.objects
for update to authenticated
using (bucket_id = 'hte-artwork' and (select private.is_hte_founder()))
with check (bucket_id = 'hte-artwork' and (select private.is_hte_founder()));

drop policy if exists "HTE administrators delete artwork" on storage.objects;
create policy "HTE administrators delete artwork" on storage.objects
for delete to authenticated
using (bucket_id = 'hte-artwork' and (select private.is_hte_founder()));
