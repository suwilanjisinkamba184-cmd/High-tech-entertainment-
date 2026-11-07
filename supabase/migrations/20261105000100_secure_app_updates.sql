create table if not exists public.app_updates (
  id uuid primary key default gen_random_uuid(),
  channel text not null default 'stable' check (channel in ('stable','beta')),
  version_name text not null check (char_length(version_name) between 1 and 32),
  version_code bigint not null check (version_code > 0),
  minimum_supported_version_code bigint not null default 1 check (minimum_supported_version_code > 0),
  is_mandatory boolean not null default false,
  release_notes text not null default '' check (char_length(release_notes) <= 4000),
  apk_url text not null check (apk_url like 'https://%'),
  apk_sha256 text not null check (apk_sha256 ~ '^[a-f0-9]{64}$'),
  package_name text not null default 'com.hightechentertainment.app' check (package_name = 'com.hightechentertainment.app'),
  manifest_signature text not null,
  is_published boolean not null default false,
  published_at timestamptz,
  withdrawn_at timestamptz,
  created_by uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(channel, version_code)
);
alter table public.app_updates enable row level security;
revoke all on public.app_updates from anon, authenticated;
grant select on public.app_updates to anon, authenticated;
drop policy if exists "Public can read current published HTE updates" on public.app_updates;
create policy "Public can read current published HTE updates"
on public.app_updates for select to anon, authenticated
using (is_published = true and withdrawn_at is null);
create index if not exists app_updates_channel_published_idx
on public.app_updates(channel, is_published, version_code desc);
comment on table public.app_updates is 'Signed HTE Android release manifests. Client writes are denied; mutations require founder-authorized Edge Function.';
