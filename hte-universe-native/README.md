# HTE Universe — clean Android rebuild

Standalone Android application built from a new Kotlin + Jetpack Compose source tree. It does not import or depend on the legacy Flutter/native application source.

- Application ID: `com.hightechentertainment.app`
- UI: Kotlin 2.4.21, Jetpack Compose, Material 3
- Android build: AGP 8.13.2, Gradle 8.13, JDK 17, compile/target SDK 36
- Visual direction: black / metallic gold / silver; crown, orbiting stars, rotating headphones and video-camera motifs
- Brand: **HTE Universe — The Pride of Africa**
- Supporting line: **Music. Creativity. Connection.**

## Build

Requires JDK 17, Android SDK 36 and Gradle 8.13:

```sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:testDebugUnitTest
```

## Core dependency baseline

- Compose BOM 2026.06.01 (stable API 36-compatible line)
- AndroidX Activity Compose 1.13.0
- AndroidX Lifecycle 2.10.0 (latest stable line compatible with API 36)
- AndroidX Core KTX 1.17.0 (API 36-compatible stable line)
- Media3 1.11.1
- Supabase Kotlin 3.9.0
- Ktor 3.6.1
- ElevenLabs Android 0.13.1

Newer AndroidX Core 1.19.x and Lifecycle 2.11.x releases require API 37 and AGP 9.1/9.2 or newer. This project intentionally remains on the API 36 toolchain so the new dependencies are mutually compatible; compileSdk and AGP can be upgraded together in a later toolchain migration. Target SDK remains 36, separate from compileSdk, to avoid opting the app into newer runtime behavior prematurely.

## Backend configuration

The Android build reads only the Supabase project URL and **publishable** key from Gradle properties or environment variables:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

For GitHub Actions, configure these as repository Actions secrets with those exact names. Do not commit `local.properties`, secret keys, service-role keys, signing files or passwords. The client deliberately rejects missing runtime configuration instead of silently connecting to an unintended backend.

The first integration layer includes the Supabase Kotlin client, PKCE auth configuration, email/password member auth operations, and a read-only published-track repository. Founder authorization must be enforced by trusted server-side app metadata and database RLS; a UI-only role check is not security.

## Signing and release safety

Production signing continues to read the existing `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` and `ANDROID_STORE_PASSWORD` GitHub Actions secrets. The workflow decodes the keystore only into the runner's temporary directory, checks the APK signer against the existing public release, and removes the temporary keystore even if a later step fails. Never commit or rotate these secrets as part of a dependency update.

## Security boundary

Only approved credentials/configuration may be migrated from the previous project. Never copy legacy source files, UI, layouts, assets or application modules. Keep keystore material and API secrets in GitHub Actions secrets / secure local storage; never commit secret values.

## Current milestone

- Clean Compose project foundation and branded startup prototype.
- Supabase client/auth/catalogue data layer; SDK updated to the current upstream Kotlin/Ktor baseline.
- Media3 1.11.1 playback, HLS and DASH libraries included as the native media foundation.
- CI uses current Node 24 GitHub Actions releases and verifies the original production signing certificate.
- Playback service and UI, final startup narration, real-device acceptance, offline downloads, Founder Studio and full screen-by-screen UI still require implementation and testing.
