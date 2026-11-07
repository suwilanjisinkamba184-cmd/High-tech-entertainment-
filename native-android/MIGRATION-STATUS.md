# Native Android migration — execution register

**Target:** High-Tech Entertainment 3.0, Kotlin + Jetpack Compose, Android 8.0+ (API 26).
**Production safety:** keep the existing Flutter app, Supabase project, database, Storage objects, users and current release untouched until native acceptance gates pass.

## Implemented in the native branch

- Kotlin/Compose application shell and dark metallic-gold theme.
- Existing Android application ID retained: `com.hightechentertainment.app`.
- API 26 minimum SDK and API 36 target.
- Media3 dependencies and callback deep-link declaration.
- Supabase PostgREST transport using the existing project URL and publishable key; bounded connect/read timeouts, IO dispatcher, safe relative resource paths and generic response handling. No service-role credential is included and RLS remains authoritative.
- Published-track catalogue query against the existing `tracks` table, with loading, refresh, empty/error states and local title/artist/genre search.
- Initial Supabase Auth REST repository for email/password sign-in, sign-up, refresh-token exchange, sign-out and password-reset request.
- Encrypted session persistence using AES-GCM with a non-exportable Android Keystore key; corrupt/unreadable session data is discarded rather than exposed.
- Added an automatic session manager for persisted-session restoration, refresh-before-expiry scheduling and retry after transient refresh failures.
- Connected the Activity to the session manager and encrypted session store. The native app now gates its listener interface behind an authenticated session, restores saved sessions on launch, and exposes sign-out.
- Added a native email/password sign-in and sign-up screen plus password-reset request UI. These flows use the existing Supabase Auth REST endpoints and the publishable key only.
- Supabase signup now handles email-confirmation-required responses without treating a missing immediate session as a server failure. Authentication is still partial: Google OAuth/PKCE browser callback handling, password update, authenticated catalogue requests, account/profile UI and real-account/RLS isolation tests remain.
- Initial Media3 playback for HTTPS track URLs now runs in a dedicated `MediaSessionService`; the Activity connects through `MediaController`, no longer owns/releases the playback engine, and does not pause playback merely because the UI leaves the foreground.
- Media3 manages audio focus and exposes the standard media-session surface for system/media controls. The catalogue selection now creates a queue of playable tracks, supports next/previous, repeat off/all/one, shuffle and seek scrubbing. Device-level notification, Bluetooth/headset, process-recreation and Android-version behaviour still require physical-device acceptance testing.
- Added a native one-shot startup-jingle player that is separate from the Media3 queue and does not request audio focus. Gradle copies the existing `mobile-app/assets/high-tech-startup.mp3` into generated Android `res/raw` resources before `preBuild`; this must be confirmed in a successful APK build and tested on a device.
- Native validation workflow pinned to the available Gradle 9.8.2 release; smoke-test assertion aligned with the current home-screen content.

## Remaining migration work — acceptance is feature-based

| Area | Required native implementation | Acceptance evidence |
|---|---|---|
| Identity | Email/password, Google OAuth callback handling, password reset/update, sign-out, session refresh and secure token persistence | Email/password UI and Activity-owned session manager integrated; Google OAuth/PKCE, email-confirmation UX, password update and real-account/RLS tests remain |
| Profiles | Profile read/update, avatar upload, account deletion request and privacy controls | Existing user records remain intact; owner/user isolation |
| Catalogue | Extend the initial published-track query to artists, releases, genre filters, pagination and robust retry | Live PostgREST data; no demo catalogue |
| Playback | Queue, seek, repeat, shuffle and polished Now Playing controls; verify system notification, Bluetooth/headset controls, background/resume and process recreation | Queue/transport controls and seek UI implemented; autoplay, track metadata sync edge cases and physical-device audio tests remain |
| Startup audio | Bundled short startup jingle, separate local player, no visible playback/status UI, never enters queue or ad rotation | Resource-generation and isolated player implementation added; APK resource inclusion and cold/warm launch tests remain |
| Promotions | Admin-managed audio campaigns, server-controlled scheduling/frequency and impressions; no listener ad-disable control | Campaign boundary and playback-order tests |
| Video | Video catalogue, Media3 playback, orientation/fullscreen, retry and lifecycle handling | Real stored video playback and network interruption |
| Library | Favourites, personal playlists, listening history, continue listening and share links | Authenticated user-scoped persistence and RLS isolation |
| Downloads | WorkManager downloads, progress, cancellation, checksum, storage quota, offline metadata and deletion | Airplane-mode playback and interrupted-download recovery |
| Founder Studio | Founder-only dashboard, catalogue CRUD, uploads, video management, enquiries/bookings and campaign management | RLS/server authorization tests using founder and ordinary accounts |
| Bookings | Form validation, booking submission, status, confirmation and notification integration | End-to-end booking with rate-limit/replay protection |
| Updates | Signed manifest verification, APK hash verification, version/downgrade checks and Android user-approved installer | Tamper, wrong-hash, downgrade, offline and installer-denial tests |
| UX/accessibility | Adaptive navigation, compact phones, tablets/foldables, landscape, font scaling, TalkBack, keyboard and reduced motion | Compose UI tests and device matrix |
| Reliability | Lifecycle-safe state, bounded diagnostics with redaction, offline errors, retry, crash-free startup and process recreation | Unit/instrumentation tests and low-RAM device runs |
| Release | Production keystore identity comparison, signed APK/AAB, versionCode upgrade, install-over and data-preservation proof | Verified certificate fingerprints and physical-device upgrade report |

## Critical release gates

1. Do not replace or uninstall the production Flutter app during migration.
2. Do not reset, clone over, or recreate production Supabase data.
3. Keep service-role and secret keys server-side only.
4. Do not publish a native APK as an update until its signing certificate matches the installed production app and its versionCode is higher.
5. Do not call a feature migrated until its live backend and device tests pass.
6. Flutter Shorebird OTA does not update native Kotlin code, manifest entries, permissions, plugins or bundled audio; those require a signed APK release.

The current branch is a foundation plus transport layer, not a completed migration or a production-ready release.
