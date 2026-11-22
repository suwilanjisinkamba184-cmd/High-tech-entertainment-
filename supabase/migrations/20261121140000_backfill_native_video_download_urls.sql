-- Backfill native uploaded-video downloads for both bucket spellings found in
-- HTE storage history. YouTube/external entries intentionally remain NULL.
-- This is idempotent: only populate missing download metadata.
update public.videos
set download_url = video_url
where download_url is null
  and source_platform = 'upload'
  and (
    video_url like 'https://wqrxacjbsldbnoekbkbf.supabase.co/storage/v1/object/public/hte-video/%'
    or video_url like 'https://wqrxacjbsldbnoekbkbf.supabase.co/storage/v1/object/public/hte-videos/%'
  );
