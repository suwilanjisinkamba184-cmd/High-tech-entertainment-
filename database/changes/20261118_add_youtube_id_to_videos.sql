-- Optional hardening for the YouTube sync automation.
-- The sync script de-duplicates by normalising existing video_url values, so it
-- works without this. Adding the column makes double-posting impossible even if
-- two workflow runs overlap.

alter table public.videos
  add column if not exists youtube_id text;

-- Backfill from existing YouTube URLs (watch?v=, youtu.be/, /embed/, /shorts/).
update public.videos
set youtube_id = substring(
  video_url from '(?:v=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})'
)
where youtube_id is null
  and video_url ~ '(?:v=|youtu\.be/|/embed/|/shorts/|/live/)[A-Za-z0-9_-]{11}';

create unique index if not exists videos_youtube_id_key
  on public.videos (youtube_id)
  where youtube_id is not null;

comment on column public.videos.youtube_id is
  'YouTube video id, populated by scripts/youtube_zambian_sync.py. Unique when set.';
