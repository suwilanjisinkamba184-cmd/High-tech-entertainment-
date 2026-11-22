drop policy if exists "HTE administrators list artwork" on storage.objects;
create policy "HTE administrators list artwork"
on storage.objects for select to authenticated
using (
  bucket_id = 'hte-artwork'
  and private.hte_has_role(array['owner','admin'])
);
