# High-Tech Entertainment — Native Android 3.0 Migration

## Decision

Android-first, native Kotlin + Jetpack Compose with AndroidX Media3 and the existing Supabase project. Flutter remains in the repository and remains the production fallback until the native client passes all release gates.

## Product contract to preserve

- High-Tech Entertainment branding: black, metallic gold and silver.
- Public listener experience: Home, Discover, releases, artist catalogue, videos, search, library, favourites, playlists, profile, sharing and supported bookings.
- Required user authentication for protected account features.
- Founder Studio protected by server-side authorization and Supabase RLS, not merely hidden navigation.
- Startup jingle is a short bundled sound. It plays without an audio player or status UI on the splash.
- Music and sponsored audio use one shared playback engine. Startup jingle never counts as a music track or triggers an advert.
- Video playback, downloads, update checks, privacy and account deletion.
- Reuse the existing Supabase project, tables, buckets and user records. Never recreate or reset production data.

## Architecture

### UI
- Single Activity, Jetpack Compose and Material 3.
- Navigation Compose with explicit routes.
- ViewModels expose immutable UI state using StateFlow.
- UI collects state lifecycle-aware.
- Adaptive navigation: bottom bar on compact phones, navigation rail on larger windows.
- Dark theme with restrained gold/silver accents and accessible contrast.

### Domain
- Use cases for sign-in, catalogue retrieval, playback, downloads, favourites, publishing and admin actions.
- Domain rules do not depend on Compose, Android views or Supabase SDK types.

### Data
- Repository interfaces isolate features from data sources.
- Supabase Kotlin SDK for Auth, PostgREST, Storage and Realtime after version/API compatibility is verified.
- Room for durable offline catalogue/download metadata; DataStore for preferences and non-sensitive settings.
- Supabase publishable key only in the client. Never bundle a service-role key.
- RLS and server-side role checks enforce user and founder boundaries.

### Media
- AndroidX Media3 ExoPlayer for audio/video.
- MediaSessionService owns the single shared music player and media session.
- Bundled startup sound uses a separate short-lived local player; no visible player controls or status UI.
- Audio focus, noisy-route handling, lifecycle and foreground media notification follow Android platform requirements.
- WorkManager handles deferrable downloads/sync, not continuous playback.

### Updates
- Native Kotlin, manifest, permission, plugin and bundled-asset changes require a new APK.
- Catalogue/content changes can be delivered from Supabase without an app binary.
- Direct APK updater requires existing signing identity, higher versionCode, package verification and Android's user-approved install flow.
- Flutter Shorebird OTA is not the native update mechanism.

## Compatibility targets

- minSdk 26 (Android 8.0).
- Phones from 320dp width upward, portrait and landscape.
- Tablets and foldables with window-size-aware navigation.
- Font/display scaling, screen readers and keyboard navigation.
- Low-RAM acceptance includes process recreation, network loss and media focus interruption.

## Release gates

1. Obtain production signing certificate fingerprint from a known APK and compare with the configured GitHub signing key.
2. Build debug and signed release APKs reproducibly in CI.
3. Pass unit, repository, Compose UI and media service tests.
4. Pass Supabase Auth and RLS tests with ordinary and founder accounts.
5. Verify startup sound plays locally and never blocks app entry or exposes sound-status UI.
6. Pass audio/video playback, media controls, downloads and offline tests.
7. Verify install, in-place upgrade, data preservation and rollback on actual devices.
8. Pass API 26, Android 14 Go low-memory, current Android, tablet and foldable profiles.
9. Promote native APK only after all gates pass.

## First implementation slice

\`native-android/\` contains a minimal native Compose shell and CI workflow. It is not a complete app or a verified install-over-compatible release. Migrate one tested feature slice at a time.
