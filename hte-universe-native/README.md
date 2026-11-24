# HTE Universe — native Android application

This directory is the production Android application source. The app is implemented in Kotlin and Jetpack Compose, with AndroidX Media3 for native playback and the existing Supabase project for authentication and application data. The legacy Flutter tree in `mobile-app/` is retained for historical reference and is not the production Android build.

## Verified build identity

The Gradle application module is the source of truth for build identity. Values below were checked against `app/build.gradle.kts` on 24 November 2026.

| Setting | Current value |
|---|---|
| Application ID / namespace | `com.hightechentertainment.app` |
| Version name | `4.3.1` |
| Version code | `55` |
| Minimum SDK | `26` (Android 8.0) |
| Compile SDK | `37` |
| Target SDK | `37` |
| JDK / JVM target | `17` |
| Android Gradle Plugin | `9.3.1` |
| Gradle | `9.6.0` |
| Kotlin | `2.4.21` |
| Compose BOM | `2026.09.00` |
| AndroidX Media3 | `1.11.1` |
| Supabase Kotlin BOM | `3.9.0` |
| Ktor | `3.6.1` |
| ElevenLabs Android SDK | `0.13.1` |

Do not copy these values into release scripts as independent constants. Read or validate them against the Gradle application configuration so that documentation and release metadata cannot silently drift.

## Build and test

Required local tooling: JDK 17, Android SDK platform/build tools 37, and Gradle 9.6.0.

From this directory:

```sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:testDebugUnitTest
```

Run the repository's Android GitHub Actions workflow for the complete CI path. A successful compile or unit-test run does not prove device-level behavior, backend authorization, release signing continuity, or successful installation over an existing build.

## Backend configuration

The app receives only the Supabase project URL and browser/mobile-safe publishable key through Gradle properties or environment variables:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `GOOGLE_WEB_CLIENT_ID` (when Google sign-in is enabled)

Never commit a service-role key, secret API key, database password, keystore, or signing password. Founder authorization must be enforced by trusted server-side authorization and Supabase Row Level Security (RLS); hiding a screen or button is not a security boundary.

The existing Supabase project and its production data are not to be reset, recreated, or bulk-rewritten as part of a client repair. Any schema change must be additive, reviewed, tested, and separately approved.

## Signing and releases

The native release workflow uses the existing signing configuration through GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_STORE_PASSWORD`

The workflow must verify the APK signer against the established production signing certificate before publication. Never rotate or replace the signing identity as part of routine code or dependency work.

Native Kotlin/Compose changes require a full signed APK release. Shorebird applies Dart patches to the legacy Flutter runtime; it cannot patch Kotlin, Compose, the Android manifest, or native dependencies. Do not describe a Shorebird patch as an update to this native app.

The official distribution channel is the repository's GitHub Releases page:
https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/latest

## Architecture and integration boundaries

- Kotlin / Jetpack Compose owns the Android UI and navigation.
- AndroidX Media3 owns native audio/video playback and Android media-session integration.
- Supabase Auth, PostgreSQL, RLS, Storage, and approved Edge Functions provide shared backend services.
- The website is built from `website/` using Astro; its public homepage is not the legacy root `index.html`.
- Member account and Founder Studio web routes use the shared account/admin assets documented in the root README and `website/README.md`.
- Public catalogue visibility and Founder permissions are controlled by backend publication fields and RLS, not by client-side assumptions.

## Acceptance checklist

Do not mark a candidate production-ready until evidence is recorded for all applicable items:

- [ ] Android unit tests and lint pass on the exact candidate commit.
- [ ] Debug and signed release APKs assemble successfully.
- [ ] Package ID, version code/name, and signer fingerprint match the intended upgrade path.
- [ ] APK installs cleanly and updates an existing installation without losing user data.
- [ ] Email/password and enabled Google authentication flows work on a physical device.
- [ ] Published catalogue, audio playback, background playback, media controls, video playback, and retry states work on a physical device.
- [ ] Offline downloads, storage-full behavior, and recovery are tested.
- [ ] Founder-only operations are rejected for ordinary accounts by backend authorization/RLS.
- [ ] Android Go memory pressure, app process recreation, API 26+, accessibility, font scaling, and reduced-motion behavior are checked.
- [ ] Website and Android app resolve the same intended published catalogue and backend project.
- [ ] Release checksum, signing certificate, install-over test, and rollback procedure are recorded.

Keep this file synchronized with `app/build.gradle.kts` and the actual workflow implementation. If any value differs, update this document in the same reviewed change.
