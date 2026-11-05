-- Public profile photos use the existing public hte-artwork bucket.
-- User uploads are confined to avatars/<authenticated-user-id>/.
-- The bucket's existing administrator-only artwork policies remain intact.

drop policy if exists "Users upload own profile avatars" on storage.objects;
create policy "Users upload own profile avatars"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'hte-artwork'
    and (storage.foldername(name))[1] = 'avatars'
    and (storage.foldername(name))[2] = (select auth.uid())::text
  );

drop policy if exists "Users view own profile avatar objects" on storage.objects;
create policy "Users view own profile avatar objects"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'hte-artwork'
    and (storage.foldername(name))[1] = 'avatars'
    and (storage.foldername(name))[2] = (select auth.uid())::text
  );

drop policy if exists "Users delete own profile avatars" on storage.objects;
create policy "Users delete own profile avatars"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'hte-artwork'
    and (storage.foldername(name))[1] = 'avatars'
    and (storage.foldername(name))[2] = (select auth.uid())::text
  );
