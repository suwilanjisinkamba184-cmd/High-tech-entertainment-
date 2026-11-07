# High-Tech Entertainment — Native Android 3.1

## Release decision

The next public Android release is **High-Tech Entertainment 3.1** (Android versionName `3.1.0`, versionCode `30`). The public version name omits the internal “SU” suffix. Android-first architecture is Kotlin + Jetpack Compose + AndroidX Media3, retaining the existing package ID `com.hightechentertainment.app`.

Flutter remains the production fallback until the native client passes every acceptance gate.

## Product contract

- Black, metallic-gold and silver High-Tech Entertainment identity.
- Listener features: Home, Discover, releases, artists, videos, search, library, favourites, playlists, listening history, profile, sharing and bookings.
- Authentication for account and personal library features.
- Founder Studio protected by server-side authorization and Supabase RLS, not hidden navigation alone.
- Bundled startup jingle plays automatically without visible player/status UI and never enters the music queue or advert rotation.
- Music and sponsored audio use the defined shared playback policy; listeners cannot disable required promotions.
- Downloads, privacy, account deletion and secure native update checks.
- Reuse existing Supabase project, users, tables and Storage objects; never reset production data.

## Architecture

- Single Activity, Compose Material 3, Navigation Compose and lifecycle-aware state.
- Media3 ExoPlayer and MediaSessionService for audio/video.
- WorkManager for deferrable downloads and synchronization.
- Room for offline/download metadata; DataStore for non-sensitive preferences.
- Supabase publishable key only in the client; RLS/server authorization remains authoritative.
- Owner-scoped personal data; founder-only management enforced by database/server policy.
- Signed native APK update flow with manifest/hash/version checks and Android user-approved installation.

## 3.1 feature scope

1. Persistent favourites, playlists and listening history.
2. Video catalogue and full-screen Media3 video playback.
3. Download queue, progress, cancellation, integrity verification and offline playback.
4. Profile editing, avatar upload and privacy/account controls.
5. Booking submission, user status view and founder processing.
6. Founder Studio for tracks, artists, releases, videos, enquiries and campaign management.
7. Server-controlled audio campaign scheduling and impression accounting.
8. Signed native update manifest, APK hash verification, version/downgrade checks and user-approved installer.
9. Responsive UI, accessibility, low-memory reliability and device compatibility.

## Additive Supabase migration

`database/2026-11-07-high-tech-entertainment-3.1.sql` adds the initial tables and RLS policies for videos, favourites, playlists, playlist tracks, listening history and bookings. It is additive and preserves existing data. It has not been applied to production; review and test it in a staging project first. Do not run migrations against production without a verified backup and explicit review.

## Release gates

1. Verify production signing certificate fingerprint against the configured signing key.
2. Build reproducible debug and signed release APKs in CI.
3. Pass unit, repository, Compose UI and Media3 service tests.
4. Test Supabase Auth and RLS with ordinary and founder accounts.
5. Verify startup audio, video, downloads, background playback, headset controls and process recreation on devices.
6. Verify install, in-place upgrade, data preservation and rollback on physical devices.
7. Pass API 26+, low-RAM Android Go, current Android, tablet and accessibility profiles.
8. Promote native APK only after all gates pass.

**Do not merge or distribute the native app as a production replacement until all gates pass.**
