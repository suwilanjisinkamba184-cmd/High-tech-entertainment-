# High Tech Entertainment

Premium, responsive music and artist platform for High Tech Entertainment.

## Production website
- Public website: https://high-tech-entertainment.suwilanjisinkamba184.workers.dev/
- Founder studio: https://high-tech-entertainment.suwilanjisinkamba184.workers.dev/admin/
- Account profile: https://high-tech-entertainment.suwilanjisinkamba184.workers.dev/account/

The production hostname is a Cloudflare `workers.dev` endpoint. Hosting and deployment settings are managed in the connected Cloudflare project. GitHub Actions validates the repository; it does not publish a second copy of the site.

## Website features
- Public music catalogue, artist profiles, releases and music videos.
- Streaming player with search, genre filtering, favourites and a device-local playlist.
- Booking, contact and support forms.
- Founder-only administration for tracks, videos, artists, releases and enquiries.
- Account profiles with private avatar storage.

## Stack
- Static HTML, CSS and JavaScript website.
- Flutter native application in `mobile-app/`.
- Supabase Auth, Postgres with Row Level Security, and Supabase Storage.
- GitHub Actions for website validation and Android/Windows app builds.

## Supabase configuration
The browser configuration in `supabase-config.js` contains only the project URL and browser-safe publishable key. Never put a Supabase service-role key or an `sb_secret_` key in frontend files, mobile code, or Git history.

Founder authorization is restricted by the configured Supabase Auth UID and database Row Level Security policies. Frontend checks are only a user-interface convenience; RLS is the security boundary.

### Authentication redirect URLs
In Supabase Authentication URL Configuration, set the production Site URL to:

`https://high-tech-entertainment.suwilanjisinkamba184.workers.dev`

Allow these redirect URLs:
- `https://high-tech-entertainment.suwilanjisinkamba184.workers.dev/admin/`
- `https://high-tech-entertainment.suwilanjisinkamba184.workers.dev/account/`

If this remains a single-founder admin system, disable public user signups in Supabase Auth settings. Existing authorised users can still sign in.

## Database and storage setup
SQL files in this repository:
- `supabase-setup.sql` — baseline tables and storage setup.
- `supabase-admin-policies.sql` — founder-only catalogue and audio permissions.
- `SUPABASE-REDESIGN-MIGRATION.sql` — private profile table and avatar bucket policies.
- `database/changes/` — subsequent database migrations.

Before enabling uploads, verify the actual Storage bucket IDs in the Supabase dashboard. The website upload code currently expects `hte-audio`, `hte-avatars`, and `hte-video`; bucket display names are not necessarily the same as bucket IDs.

## Mobile application
The Flutter source is under `mobile-app/`. The workflow `.github/workflows/mobile-build.yml` builds Android APK/AAB and a Windows ZIP. Android release publishing requires the four repository Actions secrets documented in `mobile-app/ANDROID-RELEASE-SIGNING.md`. Never commit the keystore or passwords.

## Quality checks
- `.github/workflows/deploy-pages.yml` validates required website files, XML and JavaScript syntax.
- `.github/workflows/mobile-build.yml` runs Flutter analysis and builds the mobile deliverables.
- Emil Kowalski design-engineering skills are stored in `.github/skills/`.
- Repository-specific Copilot guidance is stored in `.github/copilot-instructions.md`.

## Launch verification
The following require real service credentials and/or physical-device testing and cannot be proven by static repository checks alone:
1. Google and email/password sign-in, sign-out and redirect behavior.
2. Founder-only RLS access and all admin create/update/delete operations.
3. Actual Storage bucket IDs, upload policies and public/private access.
4. Track and video playback on Android and mobile browsers.
5. Booking, contact and support form delivery.
6. Signed APK installation and update compatibility on a real Android device.
7. Cloudflare production deployment and Supabase redirect configuration.

## Local preview
Serve the repository root with a local HTTP server. Opening files directly using `file://` is not a reliable test for Supabase authentication redirects.
