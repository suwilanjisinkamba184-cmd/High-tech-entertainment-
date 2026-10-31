# High-Tech Entertainment native app

Flutter application connected to the existing Supabase project.

## First version
- Black and metallic-gold branding.
- Home, artists, music, videos and account tabs.
- Published content read from the existing artists, releases, tracks and videos tables.
- Music streaming from each published track audio_url.
- Email/password authentication through Supabase Auth.
- GitHub Actions workflows for Android APK/AAB, Windows ZIP and unsigned iOS package.

## Supabase safety
The app uses only the Supabase project URL and publishable key. No service-role key is embedded. Row-level security remains responsible for data access. Public content queries filter to is_published = true.

## Build artifacts
After a successful run, open the repository Actions tab and download the generated workflow artifacts. Android AAB signing and Apple signing/provisioning are still required for store distribution.

## Next milestones
Add production app icon and splash screen, artist/release detail views, secure founder/admin content management, push notifications, and store signing.
