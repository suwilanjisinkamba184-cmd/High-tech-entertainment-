# High-Tech Entertainment — YouTube automation

## Official HTE channel sync

The GitHub Actions workflow `.github/workflows/youtube-zambian-sync.yml` invokes the Supabase Edge Function `sync-youtube-channel` every 30 minutes. It reads the official channel's public uploads playlist and adds new public videos to the same `public.videos` catalogue used by the website and Android app.

Configure the public GitHub Actions repository variable `HTE_YOUTUBE_CHANNEL_ID` with one of these values:

- The official YouTube channel URL (for example, `https://www.youtube.com/@yourhandle`)
- The channel ID beginning with `UC`
- The exact `@handle`

The workflow safely skips syncing until that variable is configured. Manual workflow dispatch supports `max_pages` from 1 to 50 (50 uploads per page); the scheduled run scans 20 pages by default.

## Credential boundary

- `YOUTUBE_API_KEY` stays in Supabase project secrets.
- `SUPABASE_SERVICE_ROLE_KEY` stays in Supabase's server environment and is used only by the Edge Function to write catalogue rows.
- GitHub Actions uses the existing `SUPABASE_ACCESS_TOKEN` secret to authenticate with the Supabase Management API. The Edge Function verifies that token can access this exact Supabase project before proceeding.
- The service-role key is not stored in GitHub Actions, the website, or the Android app.

## Catalogue and playback rules

Each imported video is stored with `youtube_id`, `youtube_channel_id`, `source_platform = 'youtube'`, `published_at`, and its YouTube watch URL. A unique index on `youtube_id` prevents duplicate imports. The website and Android app read the same Supabase rows. The Android app embeds YouTube videos in its in-app player; native Media3 remains reserved for HTE-hosted original files.

YouTube Data API does not provide an authorized MP4 download for arbitrary videos. YouTube-only rows therefore have `download_url = NULL`. Founder Studio uploads place the original in the `hte-video` Storage bucket and set `download_url` to that HTE-hosted file, which is the only case where the website/app show an original-download action.
