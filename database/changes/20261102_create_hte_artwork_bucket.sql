-- Public release artwork bucket with founder/admin-only write access.
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
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "HTE administrators upload artwork" on storage.objects;
create policy "HTE administrators upload artwork"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'hte-artwork'
  and private.hte_has_role(array['owner','admin'])
);

drop policy if exists "HTE administrators update artwork" on storage.objects;
create policy "HTE administrators update artwork"
on storage.objects for update to authenticated
using (
  bucket_id = 'hte-artwork'
  and private.hte_has_role(array['owner','admin'])
)
with check (
  bucket_id = 'hte-artwork'
  and private.hte_has_role(array['owner','admin'])
);

drop policy if exists "HTE administrators delete artwork" on storage.objects;
create policy "HTE administrators delete artwork"
on storage.objects for delete to authenticated
using (
  bucket_id = 'hte-artwork'
  and private.hte_has_role(array['owner','admin'])
);
