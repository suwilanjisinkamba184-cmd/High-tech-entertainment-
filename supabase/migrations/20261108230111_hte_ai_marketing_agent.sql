-- High-Tech Entertainment AI Marketing Agent foundation.
-- Credentials are NEVER stored in these tables; keep OAuth tokens and API secrets
-- in Supabase Edge Function secrets / Vault and access them server-side only.

create table if not exists public.hte_marketing_campaigns (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references auth.users(id) on delete restrict default auth.uid(),
  title text not null check (char_length(btrim(title)) between 1 and 160),
  campaign_type text not null default 'brand'
    check (campaign_type in ('release','artist','beats','studio','brand','announcement')),
  status text not null default 'draft'
    check (status in ('draft','ready','scheduled','publishing','published','partial','failed','cancelled')),
  content jsonb not null default '{}'::jsonb,
  media_url text,
  scheduled_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.hte_social_connections (
  id uuid primary key default gen_random_uuid(),
  platform text not null
    check (platform in ('instagram','facebook','tiktok','youtube','x','whatsapp_business')),
  external_account_id text,
  account_label text,
  connection_status text not null default 'not_connected'
    check (connection_status in ('not_connected','pending','connected','expired','error')),
  granted_scopes text[] not null default '{}',
  metadata jsonb not null default '{}'::jsonb,
  connected_at timestamptz,
  last_checked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(platform, external_account_id)
);

create table if not exists public.hte_social_publish_jobs (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.hte_marketing_campaigns(id) on delete cascade,
  platform text not null
    check (platform in ('instagram','facebook','tiktok','youtube','x','whatsapp_business')),
  status text not null default 'queued'
    check (status in ('queued','processing','published','failed','cancelled','needs_manual_share')),
  external_post_id text,
  error_message text,
  attempts integer not null default 0 check (attempts >= 0 and attempts <= 10),
  scheduled_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(campaign_id, platform)
);

create index if not exists hte_marketing_campaigns_schedule_idx
  on public.hte_marketing_campaigns(status, scheduled_at);
create index if not exists hte_social_publish_jobs_queue_idx
  on public.hte_social_publish_jobs(status, scheduled_at);
create index if not exists hte_social_connections_platform_idx
  on public.hte_social_connections(platform, connection_status);

alter table public.hte_marketing_campaigns enable row level security;
alter table public.hte_social_connections enable row level security;
alter table public.hte_social_publish_jobs enable row level security;

drop policy if exists "HTE admins manage marketing campaigns" on public.hte_marketing_campaigns;
create policy "HTE admins manage marketing campaigns"
  on public.hte_marketing_campaigns for all to authenticated
  using (public.hte_is_admin())
  with check (public.hte_is_admin());

drop policy if exists "HTE admins manage social connections" on public.hte_social_connections;
create policy "HTE admins manage social connections"
  on public.hte_social_connections for all to authenticated
  using (public.hte_is_admin())
  with check (public.hte_is_admin());

drop policy if exists "HTE admins manage social publish jobs" on public.hte_social_publish_jobs;
create policy "HTE admins manage social publish jobs"
  on public.hte_social_publish_jobs for all to authenticated
  using (public.hte_is_admin())
  with check (public.hte_is_admin());

revoke all on public.hte_marketing_campaigns from anon;
revoke all on public.hte_social_connections from anon;
revoke all on public.hte_social_publish_jobs from anon;
grant select, insert, update, delete on public.hte_marketing_campaigns to authenticated;
grant select, insert, update, delete on public.hte_social_connections to authenticated;
grant select, insert, update, delete on public.hte_social_publish_jobs to authenticated;
