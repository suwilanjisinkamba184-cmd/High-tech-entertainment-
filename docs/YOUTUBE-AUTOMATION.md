# Zambian music automation \u2014 YouTube \u2192 High Tech Entertainment

This document covers the two things you must do yourself (get a YouTube API key,
add two repository secrets) and explains what the automation does on its own.

---

## What was built

| File | Purpose |
|---|---|
| `scripts/youtube_zambian_sync.py` | Finds new Zambian releases on YouTube and inserts them into Supabase |
| `config/zambian-youtube.json` | The channels and search terms it watches \u2014 edit this, not the code |
| `.github/workflows/youtube-zambian-sync.yml` | Runs the sync every 6 hours, plus a manual "Run workflow" button |

### How a post appears on the site

The site does **not** need a rebuild. `website/src/components/PublicCatalogue.tsx`
and `assets/videos.js` read the Supabase `videos` table in the browser and render
any YouTube URL as an embedded player (`youtube-nocookie.com/embed/<id>`).
So the moment a row is inserted, the video shows up in the **Visual releases**
section on the next page load.

---

## Step 1 \u2014 Create the YouTube API key (you do this)

1. Go to <https://console.cloud.google.com/> and sign in with the Google account
you want to own this.
2. Top bar \u2192 project selector \u2192 **New project**. Name it `HTE Music Sync` \u2192 **Create**.
3. Make sure the new project is selected in the top bar.
4. Left menu \u2192 **APIs & Services** \u2192 **Library**.
5. Search for **YouTube Data API v3** \u2192 open it \u2192 click **Enable**.
6. Left menu \u2192 **APIs & Services** \u2192 **Credentials**.
7. **+ Create credentials** \u2192 **API key**. Copy the key that appears (starts with `AIza`).
8. Click **Edit API key** (or the pencil icon) and restrict it:
   - **API restrictions** \u2192 *Restrict key* \u2192 tick **YouTube Data API v3** only.
   - Leave *Application restrictions* on **None** \u2014 GitHub Actions has no fixed IP.
   - **Save**.

> The free quota is **10,000 units per day**. A channel check costs 1 unit; a
> keyword search costs 100. The default config uses roughly 400\u2013500 units per
> run, so four runs a day stay comfortably inside the free tier.

---

## Step 2 \u2014 Get the Supabase service_role key (you do this)

Writes to `videos` are protected by row-level security and only the founder
account may insert. A GitHub Action is not a signed-in founder, so it needs the
**service_role** key, which bypasses RLS.

1. Open <https://supabase.com/dashboard> and select the HTE project
   (`wqrxacjbsldbnoekbkbf`).
2. **Project Settings** (gear, bottom left) \u2192 **API**.
3. Under **Project API keys**, reveal and copy the **`service_role`** key.

> \u26a0\ufe0f This key can read and write everything. It must only ever live in GitHub
> Secrets \u2014 never in `supabase-config.js`, never in a commit, never in chat.
> The site's public `publishableKey` is a different, safe key.

---

## Step 3 \u2014 Add both secrets to GitHub (you do this)

1. Open <https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment->
2. **Settings** \u2192 **Secrets and variables** \u2192 **Actions**.
3. **New repository secret**:
   - Name `YOUTUBE_API_KEY` \u2192 paste the `AIza...` key \u2192 **Add secret**.
4. **New repository secret**:
   - Name `SUPABASE_SERVICE_ROLE_KEY` \u2192 paste the service_role key \u2192 **Add secret**.

---

## Step 4 \u2014 Test it (you do this)

1. **Actions** tab \u2192 **Sync new Zambian music from YouTube** (left sidebar).
2. **Run workflow** \u2192 tick **dry_run** \u2192 **Run workflow**.
3. Open the run and read the log. A healthy dry run looks like:

```
HTE YouTube sync - 2026-11-18 20:00 UTC
  lookback: 21d   max posts: 8   publish: True   dry-run: True
  catalogue already holds 2 YouTube video(s)
  channel Yo Maps (@YoMaps)
  search  'Zambian music official video'
  37 unique candidate video(s) found
  35 not yet on the site
Ready to post 8 video(s):
  - Bitter Truth - Yo Maps  (https://www.youtube.com/watch?v=...)
Dry run: nothing written.
```

4. When the dry run looks right, run it again with **dry_run** unticked.
5. Reload the site \u2014 the new videos appear under **Visual releases**.

---

## Tuning it

Everything lives in `config/zambian-youtube.json`:

| Key | Meaning |
|---|---|
| `channels` | Handles (`@name`) or `UC...` ids to watch. **Verify these** \u2014 a wrong handle is skipped with a warning, it will not break the run. |
| `queries` | Keyword searches. Each costs 100 quota units, so keep it to ~5. |
| `max_posts_per_run` | Cap per run, so a backlog does not flood the site. |
| `lookback_days` | How far back keyword search looks. |
| `min_duration_seconds` / `max_duration_seconds` | Filters out Shorts and hour-long mixes. |
| `publish_immediately` | `false` inserts drafts you approve in Founder Studio. |

### Prefer to review before publishing?

Set `"publish_immediately": false`. Rows land as drafts and you publish them from
the **Founder Studio \u2192 Videos** tab, which already has Publish/Delete buttons.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `YouTube rejected the API key` | Key is wrong, or YouTube Data API v3 is not enabled on that project. Redo Step 1.5. |
| `quotaExceeded` | Daily 10,000 units used. Trim `queries`, or wait for the midnight-Pacific reset. |
| `Supabase rejected the write` | You pasted the publishable/anon key instead of `service_role`. Redo Step 2. |
| `channel not found: @...` | That handle does not exist. Check the spelling on YouTube and fix the config. |
| Run succeeds but nothing appears | Check the rows have `is_published = true`, and hard-refresh the site (the catalogue is cached by the browser). |
| Nothing new for days | Normal \u2014 the script only posts videos it has not already seen. |

---

## Optional hardening

`database/changes/20261118_add_youtube_id_to_videos.sql` adds a `youtube_id`
column with a unique index. The script does not need it (it de-duplicates by
normalising existing URLs), but it makes double-posting impossible even if two
runs overlap. Run it in the Supabase SQL editor if you want that guarantee.


## HTE official channel synchronization

The same scheduled workflow also imports every new upload from the official HTE YouTube channel. Set the GitHub repository Actions variable `HTE_YOUTUBE_CHANNEL_ID` to the channel's `UC...` ID or exact `@handle` (Settings → Secrets and variables → Actions → Variables). This is a public channel identifier, not a secret. Official-channel uploads bypass the Zambian music discovery duration/title filters, are de-duplicated by `youtube_id`, and are written to the shared `videos` table. YouTube-only records have no `download_url`; only HTE-hosted original files can be downloaded.
