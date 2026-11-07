# High-Tech Entertainment — Native Android (3.0)

This is the native Android migration project built with Kotlin, Jetpack Compose and AndroidX Media3. It is developed alongside the existing Flutter client. Flutter remains the production fallback until the native client passes every release and device-acceptance gate.

## Current status

**Foundation scaffold only — not a production replacement.**

Implemented:
- Native Android Gradle application module.
- Existing package identity: `com.hightechentertainment.app`.
- Android 8.0 / API 26 minimum; target API 36.
- Kotlin + Jetpack Compose Material 3 dark/gold UI shell.
- Scrolling home screen and native bottom navigation.
- AndroidX Media3 dependencies for the playback implementation phase.
- Compose instrumentation smoke-test source.
- VersionName `3.0.0`, versionCode `29`.

Not yet migrated:
- Supabase authentication, catalogue and storage repositories.
- Bundled startup sound and silent splash playback.
- MediaSessionService and actual music/video playback.
- Downloads, favourites, playlists and offline cache.
- Founder-only Studio and server-side authorization.
- Signed production release and update installer.
- Full device, accessibility and performance acceptance.

## Build

Requires JDK 17, stable Android SDK 36 and Gradle 9.8. Compose BOM 2026.04.01 (Compose 1.11) is pinned to avoid the API 37 requirement introduced by Compose 1.12. The project uses Android Gradle Plugin 9 built-in Kotlin support; do not add the legacy `org.jetbrains.kotlin.android` plugin or `android.kotlinOptions` block. The Compose compiler plugin remains explicitly applied.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds a debug APK for validation only. It does not publish a production release. The instrumentation smoke test is present in source but requires an Android instrumentation device/emulator to execute.

## Identity and signing gate

The app ID must remain `com.hightechentertainment.app`. A native APK can update an existing installation only if it uses the same signing certificate and a higher versionCode. The actual certificate fingerprint has not yet been compared with the installed production APK. Do not distribute a native production APK until this check passes.

Never commit keystores, passwords or Supabase service-role keys. Only the Supabase publishable key may be used in the client, protected by correct RLS policies.

## Migration order

1. Preserve Flutter and verify production signing identity.
2. Establish native CI and prove a clean debug APK build.
3. Port design system, navigation and adaptive UI.
4. Port Supabase Auth, repositories and user-scoped data.
5. Port startup audio and Media3 playback service.
6. Port videos, downloads, favourites and playlists.
7. Port Founder Studio with server-enforced role checks.
8. Run API 26+ device matrix, low-memory and accessibility tests.
9. Verify certificate, versionCode, install/upgrade, data preservation and rollback.
10. Promote native build only after all gates pass.

Start with one Gradle app module. Extract feature/core modules after their boundaries and tests are established.
