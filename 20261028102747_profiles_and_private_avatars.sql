-- High-Tech Entertainment profile and avatar security migration.
-- Applied to Supabase project wqrxacjbsldbnoekbkbf.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  bio text not null default '',
  phone text not null default '',
  avatar_url text not null default '',
  updated_at timestamptz not null default now()
);
alter table public.profiles enable row level security;
revoke all on public.profiles from anon;
grant select, insert, update on public.profiles to authenticated;
drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile" on public.profiles for select to authenticated using ((select auth.uid()) = id);
drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile" on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-avatars','hte-avatars',false,5242880,array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public=false,file_size_limit=5242880,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists "Users can view own avatar files" on storage.objects;
create policy "Users can view own avatar files" on storage.objects for select to authenticated using (bucket_id='hte-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists "Users can upload own avatar files" on storage.objects;
create policy "Users can upload own avatar files" on storage.objects for insert to authenticated with check (bucket_id='hte-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists "Users can update own avatar files" on storage.objects;
create policy "Users can update own avatar files" on storage.objects for update to authenticated using (bucket_id='hte-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text) with check (bucket_id='hte-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists "Users can delete own avatar files" on storage.objects;
create policy "Users can delete own avatar files" on storage.objects for delete to authenticated using (bucket_id='hte-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
