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

## Security boundary
Only approved credentials/configuration may be migrated from the previous project. Never copy legacy source files, UI, assets, or application modules. Keep keystore material and API secrets in GitHub Actions secrets / secure local storage; never commit secret values.

## Current milestone
Clean project foundation and first Compose UI prototype. Supabase, Media3 playback, offline storage, release signing, and final startup audio master are subsequent milestones. The crown reference photograph and approved audio master have not yet been supplied in this chat.
