# HTE Universe — clean Android rebuild

Standalone Android application built from a new Kotlin + Jetpack Compose source tree. It does not import or depend on the legacy Flutter/native application source.

- Application ID: `com.hightechentertainment.app`
- UI: Kotlin, Jetpack Compose, Material 3
- Visual direction: black / metallic gold / silver; crown, orbiting stars, rotating headphones and video-camera motifs
- Brand: **HTE Universe — The Pride of Africa**
- Supporting line: **Music. Creativity. Connection.**

## Build

Requires JDK 17, Android SDK 36 and Gradle 8.11.1:

```sh
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

## Backend configuration

The Android build reads only the Supabase project URL and **publishable** key from Gradle properties or environment variables:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

For GitHub Actions, configure these as repository Actions secrets with those exact names. Do not commit `local.properties`, secret keys, service-role keys, signing files or passwords. The client deliberately rejects missing runtime configuration instead of silently connecting to an unintended backend.

The first integration layer includes the Supabase Kotlin client, PKCE auth configuration, email/password member auth operations, and a read-only published-track repository. Founder authorization must be enforced by trusted server-side app metadata and database RLS; a UI-only role check is not security.

## Security boundary

Only approved credentials/configuration may be migrated from the previous project. Never copy legacy source files, UI, layouts, assets or application modules. Keep keystore material and API secrets in GitHub Actions secrets / secure local storage; never commit secret values.

## Current milestone

- Clean Compose project foundation and first UI prototype.
- Supabase client/auth/catalogue data layer added; CI compilation of this integration layer is the next verification gate.
- Media3 dependencies added as the native playback foundation; the playback service and UI are not yet implemented.
- Final startup narration/audio master and crown reference photograph have not yet been supplied in this chat.
- Production signing, device acceptance, offline downloads, Founder Studio and full screen-by-screen UI remain outstanding.
