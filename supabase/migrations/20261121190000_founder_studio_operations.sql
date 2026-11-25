-- Founder Studio operational workspaces. Additive only; no existing records are moved.
create table if not exists public.hte_booking_workflow (
 id uuid primary key default gen_random_uuid(),
 message_id uuid not null references public.contact_messages(id) on delete cascade,
 status text not null default 'new' check (status in ('new','reviewing','contacted','confirmed','declined','completed','cancelled')),
 internal_note text check (internal_note is null or char_length(internal_note)<=2000),
 follow_up_at timestamptz,
 updated_by uuid references auth.users(id) on delete set null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(message_id)
);
create index if not exists hte_booking_workflow_status_idx on public.hte_booking_workflow(status,updated_at desc);
alter table public.hte_booking_workflow enable row level security;
drop policy if exists "HTE founders manage guest booking workflow" on public.hte_booking_workflow;
create policy "HTE founders manage guest booking workflow" on public.hte_booking_workflow for all to authenticated using (public.hte_is_admin()) with check (public.hte_is_admin());
revoke all on table public.hte_booking_workflow from public, anon, authenticated;
grant select,insert,update on table public.hte_booking_workflow to authenticated;

create table if not exists public.hte_admin_audit_log (
 id uuid primary key default gen_random_uuid(),
 actor_id uuid not null references auth.users(id) on delete restrict,
 action text not null check (char_length(btrim(action)) between 1 and 120),
 entity_type text not null check (char_length(btrim(entity_type)) between 1 and 80),
 entity_id text not null default '' check (char_length(entity_id)<=160),
 details jsonb not null default '{}'::jsonb check (pg_column_size(details)<=8192),
 created_at timestamptz not null default now()
);
create index if not exists hte_admin_audit_log_created_idx on public.hte_admin_audit_log(created_at desc);
create index if not exists hte_admin_audit_log_actor_idx on public.hte_admin_audit_log(actor_id,created_at desc);
alter table public.hte_admin_audit_log enable row level security;
drop policy if exists "HTE founders read audit log" on public.hte_admin_audit_log;
create policy "HTE founders read audit log" on public.hte_admin_audit_log for select to authenticated using (public.hte_is_admin());
drop policy if exists "HTE founders append audit log" on public.hte_admin_audit_log;
create policy "HTE founders append audit log" on public.hte_admin_audit_log for insert to authenticated with check (actor_id=(select auth.uid()) and public.hte_is_admin());
revoke all on table public.hte_admin_audit_log from public, anon, authenticated;
grant select,insert on table public.hte_admin_audit_log to authenticated;
create or replace function private.reject_hte_audit_mutation() returns trigger language plpgsql set search_path='' as $function$ begin raise exception 'Founder audit records are append-only'; end; $function$;
drop trigger if exists hte_admin_audit_log_append_only on public.hte_admin_audit_log;
create trigger hte_admin_audit_log_append_only before update or delete on public.hte_admin_audit_log for each row execute function private.reject_hte_audit_mutation();

create or replace function private.hte_admin_get_audience_series(_days integer default 30)
returns table(day date,source text,event_type text,event_count bigint,unique_visitors bigint)
language plpgsql security definer set search_path=''
as $function$
declare v_days integer := greatest(1,least(coalesce(_days,30),90));
begin
 if not private.hte_has_role(array['owner','admin']) then raise exception 'Not authorized to view audience analytics' using errcode='42501'; end if;
 return query select (e.created_at at time zone 'UTC')::date,e.source,e.event_type,count(*)::bigint,count(distinct e.visitor_id)::bigint
 from public.hte_audience_events e where e.created_at>=now()-make_interval(days=>v_days) and e.created_at<=now()
 group by (e.created_at at time zone 'UTC')::date,e.source,e.event_type order by 1,2,3;
end; $function$;
revoke all on function private.hte_admin_get_audience_series(integer) from public,anon;
grant execute on function private.hte_admin_get_audience_series(integer) to authenticated;
create or replace function public.hte_admin_get_audience_series(_days integer default 30)
returns table(day date,source text,event_type text,event_count bigint,unique_visitors bigint)
language sql stable security invoker set search_path=''
as $function$ select * from private.hte_admin_get_audience_series(_days); $function$;
revoke all on function public.hte_admin_get_audience_series(integer) from public,anon;
grant execute on function public.hte_admin_get_audience_series(integer) to authenticated;
