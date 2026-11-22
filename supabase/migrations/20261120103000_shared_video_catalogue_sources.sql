-- Shared HTE video catalogue metadata for YouTube sync and original-file downloads.
alter table public.videos add column if not exists youtube_id text;
alter table public.videos add column if not exists youtube_channel_id text;
alter table public.videos add column if not exists source_platform text not null default 'upload';
alter table public.videos add column if not exists download_url text;
alter table public.videos add column if not exists published_at timestamptz;

update public.videos
set youtube_id = substring(video_url from '(?:v=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})')
where youtube_id is null and video_url ~ '(?:v=|youtu\.be/|/embed/|/shorts/|/live/)[A-Za-z0-9_-]{11}';

update public.videos set source_platform = case when youtube_id is not null then 'youtube' else 'upload' end;
update public.videos set download_url = video_url
where download_url is null and (video_url like 'https://wqrxacjbsldbnoekbkbf.supabase.co/storage/v1/object/public/hte-video/%' or video_url like 'https://wqrxacjbsldbnoekbkbf.supabase.co/storage/v1/object/public/hte-videos/%');

drop index if exists public.videos_youtube_id_key;
create unique index if not exists videos_youtube_id_key on public.videos (youtube_id);
create index if not exists videos_published_created_at_idx on public.videos (is_published, created_at desc);
comment on column public.videos.download_url is 'Optional HTE-hosted original file URL. NULL for YouTube-only videos.';
comment on column public.videos.youtube_id is 'Canonical YouTube video ID used for idempotent channel synchronization.';
