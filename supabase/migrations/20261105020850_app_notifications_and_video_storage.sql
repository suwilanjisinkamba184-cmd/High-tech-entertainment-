-- In-app announcements and private user notifications for HTE.
create table if not exists public.app_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid null references auth.users(id) on delete cascade,
  title text not null check (char_length(btrim(title)) between 1 and 150),
  body text not null check (char_length(btrim(body)) between 1 and 3000),
  category text not null default 'announcement'
    check (category in ('release','account','announcement')),
  created_at timestamptz not null default now()
);

create index if not exists app_notifications_user_created_idx
  on public.app_notifications (user_id, created_at desc);
create index if not exists app_notifications_broadcast_created_idx
  on public.app_notifications (created_at desc)
  where user_id is null;

alter table public.app_notifications enable row level security;
revoke all on public.app_notifications from anon;
grant select, insert on public.app_notifications to authenticated;

drop policy if exists "Users read their notifications and global announcements" on public.app_notifications;
drop policy if exists "Users read broadcast or own notifications" on public.app_notifications;
create policy "Users read broadcast or own notifications"
  on public.app_notifications for select to authenticated
  using (user_id is null or user_id = (select auth.uid()));

drop policy if exists "HTE admins create notifications" on public.app_notifications;
drop policy if exists "HTE owners and admins send notifications" on public.app_notifications;
create policy "HTE owners and admins send notifications"
  on public.app_notifications for insert to authenticated
  with check (
    private.hte_has_role(array['owner','admin'])
    and (user_id is null or user_id = (select auth.uid()))
  );

-- The existing singular hte-video bucket is preserved for older uploads.
-- The current native Founder Studio uses hte-videos.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('hte-videos','hte-videos',true,524288000,
  array['video/mp4','video/x-m4v','video/quicktime','video/webm'])
on conflict (id) do update set
  public = true,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "HTE administrators upload videos" on storage.objects;
create policy "HTE administrators upload videos" on storage.objects
  for insert to authenticated
  with check (bucket_id in ('hte-video','hte-videos')
    and private.hte_has_role(array['owner','admin']));

drop policy if exists "HTE administrators update videos" on storage.objects;
create policy "HTE administrators update videos" on storage.objects
  for update to authenticated
  using (bucket_id in ('hte-video','hte-videos')
    and private.hte_has_role(array['owner','admin']))
  with check (bucket_id in ('hte-video','hte-videos')
    and private.hte_has_role(array['owner','admin']));

drop policy if exists "HTE administrators delete videos" on storage.objects;
create policy "HTE administrators delete videos" on storage.objects
  for delete to authenticated
  using (bucket_id in ('hte-video','hte-videos')
    and private.hte_has_role(array['owner','admin']));
