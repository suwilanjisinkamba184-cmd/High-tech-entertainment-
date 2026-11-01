-- HTE role-based administration and private role directory.
-- Apply after the existing HTE catalog, streaming and founder-policy migrations.
-- Admin identities are stored outside the exposed public schema. Partner and promoter
-- subscriptions are separate roles and never imply administrative access.

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create table if not exists private.user_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null check (role in ('owner','admin','music_promoter','partner')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null
);
alter table private.user_roles enable row level security;
revoke all on private.user_roles from public, anon, authenticated;

insert into private.user_roles (user_id, role, is_active)
values ('b268df9e-eff5-43b9-a38f-869b33add38b'::uuid, 'owner', true)
on conflict (user_id) do update set role='owner', is_active=true;

create or replace function private.hte_has_role(_roles text[])
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select auth.uid() is not null and exists (
    select 1 from private.user_roles r
    where r.user_id = auth.uid()
      and r.is_active
      and r.role = any(_roles)
  );
$$;
revoke all on function private.hte_has_role(text[]) from public, anon;
grant execute on function private.hte_has_role(text[]) to authenticated;

create or replace function private.hte_current_role()
returns text
language sql stable security definer
set search_path = ''
as $$
  select r.role
  from private.user_roles r
  where auth.uid() is not null
    and r.user_id = auth.uid()
    and r.is_active
  limit 1;
$$;
revoke all on function private.hte_current_role() from public, anon;
grant execute on function private.hte_current_role() to authenticated;

create or replace function public.hte_is_admin()
returns boolean
language sql stable security invoker
set search_path = ''
as $$
  select coalesce(private.hte_has_role(array['owner','admin']), false);
$$;
revoke all on function public.hte_is_admin() from public, anon;
grant execute on function public.hte_is_admin() to authenticated;

create or replace function public.hte_my_role()
returns text
language sql stable security invoker
set search_path = ''
as $$
  select private.hte_current_role();
$$;
revoke all on function public.hte_my_role() from public, anon;
grant execute on function public.hte_my_role() to authenticated;

create or replace function private.hte_assign_role(_user_id uuid, _role text, _active boolean default true)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or not private.hte_has_role(array['owner']) then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  if _role not in ('owner','admin','music_promoter','partner') then
    raise exception 'Unsupported HTE role' using errcode = '22023';
  end if;
  if _user_id is null or not exists (select 1 from auth.users u where u.id = _user_id) then
    raise exception 'Target account does not exist' using errcode = '22023';
  end if;
  insert into private.user_roles(user_id, role, is_active, created_by)
  values (_user_id, _role, coalesce(_active,true), auth.uid())
  on conflict (user_id) do update
    set role=excluded.role, is_active=excluded.is_active, created_by=auth.uid();
end;
$$;
revoke all on function private.hte_assign_role(uuid,text,boolean) from public, anon;
grant execute on function private.hte_assign_role(uuid,text,boolean) to authenticated;

create or replace function public.hte_assign_role(_user_id uuid, _role text, _active boolean default true)
returns void
language sql security invoker
set search_path = ''
as $$
  select private.hte_assign_role(_user_id, _role, _active);
$$;
revoke all on function public.hte_assign_role(uuid,text,boolean) from public, anon;
grant execute on function public.hte_assign_role(uuid,text,boolean) to authenticated;

-- Catalogue administration: only owner/admin can manage rows; visitors read published rows.
do $$
declare t text;
begin
  foreach t in array array['tracks','artists','releases','videos'] loop
    execute format('drop policy if exists %I on public.%I', 'HTE founder can insert '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE founder can update '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE founder can delete '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE founder inserts '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE founder updates '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE founder deletes '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE admin inserts '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE admin updates '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE admin deletes '||t, t);
    execute format('drop policy if exists %I on public.%I', 'Public and founder can read '||t, t);
    execute format('drop policy if exists %I on public.%I', 'Public and administrators can read '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE administrators insert '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE administrators update '||t, t);
    execute format('drop policy if exists %I on public.%I', 'HTE administrators delete '||t, t);
    execute format('create policy %I on public.%I for select to anon, authenticated using (is_published = true or private.hte_has_role(array[''owner'',''admin'']))', 'Public and administrators can read '||t, t);
    execute format('create policy %I on public.%I for insert to authenticated with check (private.hte_has_role(array[''owner'',''admin'']))', 'HTE administrators insert '||t, t);
    execute format('create policy %I on public.%I for update to authenticated using (private.hte_has_role(array[''owner'',''admin''])) with check (private.hte_has_role(array[''owner'',''admin'']))', 'HTE administrators update '||t, t);
    execute format('create policy %I on public.%I for delete to authenticated using (private.hte_has_role(array[''owner'',''admin'']))', 'HTE administrators delete '||t, t);
    execute format('grant select on public.%I to anon, authenticated', t);
    execute format('grant insert, update, delete on public.%I to authenticated', t);
  end loop;
end $$;

-- Campaign controls remain private to owner/admin. Public reads are limited to active published campaigns.
drop policy if exists "HTE founder inserts audio ads" on public.audio_ads;
drop policy if exists "HTE founder updates audio ads" on public.audio_ads;
drop policy if exists "HTE founder deletes audio ads" on public.audio_ads;
drop policy if exists "HTE administrators read audio ads" on public.audio_ads;
drop policy if exists "HTE administrators insert audio ads" on public.audio_ads;
drop policy if exists "HTE administrators update audio ads" on public.audio_ads;
drop policy if exists "HTE administrators delete audio ads" on public.audio_ads;
drop policy if exists "Published active ads or founder can read" on public.audio_ads;
create policy "Published active ads or administrators can read" on public.audio_ads
for select to anon, authenticated
using ((is_published and (starts_at is null or starts_at <= now()) and (ends_at is null or ends_at > now()))
  or private.hte_has_role(array['owner','admin']));
create policy "HTE administrators insert audio ads" on public.audio_ads
for insert to authenticated with check (private.hte_has_role(array['owner','admin']));
create policy "HTE administrators update audio ads" on public.audio_ads
for update to authenticated using (private.hte_has_role(array['owner','admin']))
with check (private.hte_has_role(array['owner','admin']));
create policy "HTE administrators delete audio ads" on public.audio_ads
for delete to authenticated using (private.hte_has_role(array['owner','admin']));
grant select on public.audio_ads to anon, authenticated;
grant insert, update, delete on public.audio_ads to authenticated;

-- Private inbox and aggregate campaign reporting.
drop policy if exists "HTE founder can read contact messages" on public.contact_messages;
drop policy if exists "HTE administrators can read contact messages" on public.contact_messages;
create policy "HTE administrators can read contact messages" on public.contact_messages
for select to authenticated using (private.hte_has_role(array['owner','admin']));
grant select on public.contact_messages to authenticated;

drop policy if exists "HTE administrators can read ad impressions" on public.ad_impressions;
create policy "HTE administrators can read ad impressions" on public.ad_impressions
for select to authenticated using (private.hte_has_role(array['owner','admin']));
grant select on public.ad_impressions to authenticated;

-- Storage: retain public delivery, but allow only assigned administrators to write.
drop policy if exists "HTE founder can upload audio" on storage.objects;
drop policy if exists "HTE founder can update audio" on storage.objects;
drop policy if exists "HTE founder can delete audio" on storage.objects;
drop policy if exists "HTE admin can upload audio" on storage.objects;
drop policy if exists "HTE admin can update audio" on storage.objects;
drop policy if exists "HTE admin can delete audio" on storage.objects;
drop policy if exists "HTE administrators upload audio" on storage.objects;
drop policy if exists "HTE administrators update audio" on storage.objects;
drop policy if exists "HTE administrators delete audio" on storage.objects;
create policy "HTE administrators upload audio" on storage.objects
for insert to authenticated with check (bucket_id='hte-audio' and private.hte_has_role(array['owner','admin']));
create policy "HTE administrators update audio" on storage.objects
for update to authenticated using (bucket_id='hte-audio' and private.hte_has_role(array['owner','admin']))
with check (bucket_id='hte-audio' and private.hte_has_role(array['owner','admin']));
create policy "HTE administrators delete audio" on storage.objects
for delete to authenticated using (bucket_id='hte-audio' and private.hte_has_role(array['owner','admin']));

drop policy if exists "HTE founder uploads videos" on storage.objects;
drop policy if exists "HTE founder deletes videos" on storage.objects;
drop policy if exists "HTE administrators upload videos" on storage.objects;
drop policy if exists "HTE administrators delete videos" on storage.objects;
create policy "HTE administrators upload videos" on storage.objects
for insert to authenticated with check (bucket_id='hte-video' and private.hte_has_role(array['owner','admin']));
create policy "HTE administrators delete videos" on storage.objects
for delete to authenticated using (bucket_id='hte-video' and private.hte_has_role(array['owner','admin']));

-- Keep public visitor account data private; partner/promoter roles do not confer admin access.
-- No SELECT policy or Data API grant is added for private.user_roles.
