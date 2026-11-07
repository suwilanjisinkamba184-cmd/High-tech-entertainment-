# High-Tech Entertainment — Native Android 3.1

This is the Android-native application workstream built with Kotlin, Jetpack Compose and AndroidX Media3. The native 3.1 release is being developed alongside the existing Flutter production client. Flutter remains the production fallback until native acceptance gates pass.

## Release identity

- Public version: **High-Tech Entertainment 3.1**
- Android versionName: `3.1.0`
- Android versionCode: `30`
- Application ID: `com.hightechentertainment.app`
- Minimum Android: 8.0 / API 26
- Target SDK: API 36
- Public branding does not include the internal “SU” suffix.

## Current status

**3.1 implementation in progress — not a production replacement.**

The native branch currently contains the Kotlin/Compose shell, authenticated Supabase REST transport, email/password authentication, encrypted session persistence, Google OAuth PKCE callback implementation, published-track catalogue, Media3 background audio service, queue controls, and bundled startup-jingle resource generation.

An additive Supabase SQL migration has been added at `database/2026-11-07-high-tech-entertainment-3.1.sql`. It defines the initial data model and RLS policies for published videos, user favourites, playlists, playlist tracks, listening history and bookings. It does not modify or delete existing production tables or data. **The SQL has not been applied to the live Supabase project and must be reviewed and tested before execution.**

Still to implement and verify in native UI/services: video playback, persistent library screens, downloads/offline playback, profile/avatar management, bookings workflow, Founder Studio, campaign delivery, signed native update installer, password update, adaptive/accessibility work, and full device testing.

## Build and validation

Requires JDK 17, Android SDK 36 and Gradle 9.8.2. Compose BOM 2026.04.01 is pinned for API 36 compatibility. Android Gradle Plugin 9 uses built-in Kotlin support; do not add the legacy `org.jetbrains.kotlin.android` plugin or `android.kotlinOptions` block.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions debug APKs are validation artifacts, not production releases. A successful build does not prove live authentication, Supabase RLS, audio playback or feature completeness.

## Data and security rules

- Reuse the existing Supabase project and preserve all users, rows and Storage objects.
- Only the Supabase publishable key may be bundled in the Android client.
- Never put service-role credentials or keystore material in source control.
- RLS is authoritative; hiding Founder Studio navigation is not authorization.
- Review and test the additive 3.1 SQL migration before applying it to production.
- No public booking insertion or public media upload policy is introduced.

## Signing and release gate

A native APK can update an installed app only if the signing certificate matches and versionCode is higher. The production certificate fingerprint has not yet been compared with the configured native signing key. Do not distribute the native build as an in-place update until this is proven on a physical device.

## 3.1 implementation sequence

1. Preserve the Flutter production app and existing Supabase data.
2. Complete persistent favourites, playlists and listening history using owner-scoped RLS.
3. Implement video catalogue and Media3 full-screen video playback.
4. Implement WorkManager downloads, integrity checks and offline playback.
5. Implement profile/avatar and account privacy flows.
6. Implement booking submission and status views.
7. Implement Founder Studio with server-enforced founder permissions and secure uploads.
8. Integrate server-controlled sponsored audio without a listener ad-disable control.
9. Implement signed native update manifest verification and Android user-approved installation.
10. Pass API 26+, low-memory, accessibility, media, security and install-over acceptance tests.

The 3.1 target is feature-complete native Android, not merely a version-label change. Keep Flutter production intact until every release gate passes.
