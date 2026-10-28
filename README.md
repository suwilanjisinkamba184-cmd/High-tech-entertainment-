# High Tech Entertainment

Premium, responsive music and artist platform for High Tech Entertainment, hosted on GitHub Pages and connected to Supabase.

## Website pages
- Public website: `/` — music catalogue, artist profiles, releases, about and contact form.
- Founder studio: `/admin/` — founder-only music uploads, publish/unpublish controls, artist and release management, and private contact inbox.
- Account profile: `/account/` — private display name, biography, phone and profile photo.

## Stack
- Static HTML/CSS/JavaScript deployed by GitHub Actions to GitHub Pages.
- Supabase Auth for email/password and Google sign-in.
- Supabase Postgres with Row Level Security for catalogue, profile and contact data.
- Supabase Storage: `hte-audio` for public published music and `hte-avatars` for private profile photos.

## Deployment
GitHub Pages is built by `.github/workflows/deploy-pages.yml`. The previous competing Pages workflow has been removed. The build copies `index.html`, `supabase-config.js`, `assets/`, `admin/`, and `account/` into the Pages artifact.

## Supabase configuration
The browser config in `supabase-config.js` contains only the project URL and browser-safe publishable key. Never put a Supabase service-role or `sb_secret_` key in frontend files.

Founder authorization is restricted to the confirmed Supabase Auth UID in the SQL policies. The founder email is `suwilanjisinkamba184@gmail.com`. Authorization is enforced by Supabase Row Level Security, not by hiding frontend controls.

SQL files:
- `supabase-setup.sql` — baseline tables and storage setup.
- `supabase-admin-policies.sql` — founder-only catalogue and audio permissions.
- `SUPABASE-REDESIGN-MIGRATION.sql` — private profile table and avatar bucket policies.

## Important launch checks
1. In Supabase Authentication URL Configuration, allow the live Pages URL as the Site URL and add these Redirect URLs:
   - `https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/admin/`
   - `https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account/`
2. If this remains a single-founder admin system, disable public user signups in Supabase Auth settings. Existing users can still sign in.
3. Test Google sign-in, password sign-in, profile save/photo upload, track upload/playback, publish/unpublish, artist/release creation and contact inbox.
4. Upload your actual cover artwork and published tracks. The catalogue tables currently have no entries, so empty-state messages are expected until content is added.
5. Review Supabase Security Advisor. Leaked-password protection is currently reported as disabled; enable it if available on your project plan.

## Local preview
Serve the repository root with any static web server. Opening files directly as `file://` is not a reliable test for Supabase OAuth redirects.
