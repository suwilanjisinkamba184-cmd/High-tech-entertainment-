-- Cover the creator foreign key for efficient founder campaign filtering.
create index if not exists hte_marketing_campaigns_created_by_idx
  on public.hte_marketing_campaigns(created_by);
