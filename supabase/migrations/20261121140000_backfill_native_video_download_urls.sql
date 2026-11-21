-- Backfill download metadata for native uploads stored in the current plural bucket.
-- YouTube-only entries intentionally keep download_url NULL.
update public.videos
set download_url = video_url
where download_url is null
  and video_url like 'https://wqrxacjbsldbnoekbkbf.supabase.co/storage/v1/object/public/hte-videos/%';
