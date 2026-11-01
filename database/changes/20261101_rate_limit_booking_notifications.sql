-- Rate-limit the public booking notification endpoint without exposing rate-limit records.
create table if not exists private.booking_notification_rate_limits (
  ip_hash text primary key check (char_length(ip_hash) = 64),
  window_started_at timestamptz not null default now(),
  request_count integer not null default 0 check (request_count >= 0),
  updated_at timestamptz not null default now()
);
alter table private.booking_notification_rate_limits enable row level security;
revoke all on private.booking_notification_rate_limits from public, anon, authenticated;

create or replace function public.hte_consume_booking_notification_limit(
  _ip_hash text,
  _max_requests integer default 5,
  _window_seconds integer default 900
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_now timestamptz := now();
  v_count integer;
begin
  if coalesce(auth.jwt()->>'role','') <> 'service_role' then
    raise exception 'Server-only operation' using errcode = '42501';
  end if;
  if _ip_hash is null or char_length(_ip_hash) <> 64
     or _max_requests < 1 or _window_seconds < 1 then
    raise exception 'Invalid rate-limit arguments' using errcode = '22023';
  end if;
  insert into private.booking_notification_rate_limits as r
    (ip_hash, window_started_at, request_count, updated_at)
  values (_ip_hash, v_now, 1, v_now)
  on conflict (ip_hash) do update set
    window_started_at = case
      when r.window_started_at <= v_now - make_interval(secs => _window_seconds) then v_now
      else r.window_started_at end,
    request_count = case
      when r.window_started_at <= v_now - make_interval(secs => _window_seconds) then 1
      else r.request_count + 1 end,
    updated_at = v_now
  returning request_count into v_count;
  return v_count <= _max_requests;
end;
$$;
revoke all on function public.hte_consume_booking_notification_limit(text,integer,integer) from public, anon, authenticated;
grant execute on function public.hte_consume_booking_notification_limit(text,integer,integer) to service_role;
