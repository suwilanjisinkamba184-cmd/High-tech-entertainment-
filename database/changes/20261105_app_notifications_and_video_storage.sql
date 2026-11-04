-- In-app announcements and private user notifications for HTE.
create table if not exists public.app_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade,
  title text not null check (char_length(btrim(title)) between 1 and 120),
  body text not null check (char_length(btrim(body)) between 1 and 1000),
  category text not null default 'announcement'
    check (category in ('release','account','announcement')),
  created_at timestamptz not null default now()
);
alter table public.app_notifications enable row level security;
revoke all on public.app_notifications from anon;
grant select, insert on public.app_notifications to authenticated;
drop policy if exists "Users read their notifications and global announcements" on public.app_notifications;
create policy "Users read their notifications and global announcements"
  on public.app_notifications for select to authenticated
  using (user_id is null or user_id = (select auth.uid()));
drop policy if exists "HTE admins create notifications" on public.app_notifications;
create policy "HTE admins create notifications"
  on public.app_notifications for insert to authenticated
  with check (
    private.hte_has_role(array['owner','admin'])
    and (user_id is null or user_id = (select auth.uid()))
  );

-- Public video delivery; only HTE owner/admin roles can upload, replace or delete.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-videos','hte-videos',true,524288000,
  array['video/mp4','video/x-m4v','video/quicktime','video/webm'])
on conflict (id) do update set
  public = true,
  file_size_limit = 524288000,
  allowed_mime_types = excluded.allowed_mime_types;
drop policy if exists "HTE admins upload videos" on storage.objects;
create policy "HTE admins upload videos" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'hte-videos' and private.hte_has_role(array['owner','admin']));
drop policy if exists "HTE admins update videos" on storage.objects;
create policy "HTE admins update videos" on storage.objects
  for update to authenticated
  using (bucket_id = 'hte-videos' and private.hte_has_role(array['owner','admin']))
  with check (bucket_id = 'hte-videos' and private.hte_has_role(array['owner','admin']));
drop policy if exists "HTE admins delete videos" on storage.objects;
create policy "HTE admins delete videos" on storage.objects
  for delete to authenticated
  using (bucket_id = 'hte-videos' and private.hte_has_role(array['owner','admin']));
