# High-Tech Entertainment — YouTube automation

## Workflows
The repository contains two separate automation paths:
- `.github/workflows/sync-official-youtube-channel.yml` and the `sync-youtube-channel` Edge Function for the configured official HTE channel.
- `.github/workflows/youtube-zambian-sync.yml` and the `sync-zambian-music` Edge Function for the approved Zambian artist discovery configuration.

Read the workflow YAML before changing schedules, page limits or dispatch inputs. A configured workflow is not proof that the schedule ran or that a new video was imported; inspect the Actions run and the resulting catalogue row.

## Configuration and credentials
The official-channel workflow uses the repository variable `HTE_YOUTUBE_CHANNEL_ID` when configured. Use the exact accepted value documented in the workflow (channel ID or supported handle/URL format). The Zambian discovery path reads its approved channel configuration from `config/zambian-youtube.json`.

- `YOUTUBE_API_KEY` belongs in Supabase project secrets.
- `SUPABASE_SERVICE_ROLE_KEY` belongs only in the Supabase server environment.
- `SUPABASE_ACCESS_TOKEN` is a management/deployment credential used by the relevant GitHub workflow; it is not a client runtime key.
- Never put the service-role key or YouTube API key in GitHub client code, the website or Android app.

## Catalogue and rights
The synchronization functions write metadata into the shared Supabase video catalogue. YouTube-hosted videos should use official YouTube playback; a YouTube watch URL is not a downloadable MP4. Do not create an HTE download URL for third-party media unless HTE has the original file and distribution rights.

For HTE-owned uploads, use the approved Storage upload path and the bucket ID confirmed by current application configuration and Supabase. Do not infer bucket names from old documentation.

## Verification
Check the workflow run, Edge Function response and resulting `public.videos` row. Confirm duplicate handling, visibility, channel identity and playback behavior. Report imports only after the row is present and the video can be opened through the intended player.
