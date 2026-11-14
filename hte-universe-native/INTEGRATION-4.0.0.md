# HTE UNIVERSE 4.0.0 — source integration register

## Integration decision

The uploaded `HTE-Universe-source.zip` was inspected against the richer native Android source already maintained in this repository. The canonical implementation remains `hte-universe-native/`, based on the Kotlin + Jetpack Compose native application.

The archive is a compact starter snapshot (29 ZIP entries; approximately 29 KB of source). It contains a second application shell (`HteApp` / `HteRoot`), a basic catalogue repository, a separate player controller and a second `media.HtePlaybackService`. It does not contain the original logo or launcher resources and its README marks several key product features unfinished.

## Why the archive is not copied wholesale

Both projects use the same Android package namespace and define overlapping media responsibilities. Copying the archive directly would introduce duplicate fully qualified classes, competing playback-service ownership, parallel settings/catalogue models and two different application entry points. Its UI also describes Founder Studio, downloads, Google sign-in, AI Companion and startup audio as unfinished, while the canonical native project already contains implementations for these areas.

The starter `HteRoot.kt` also uses `rememberSaveable` without importing it, so it is not a verified drop-in replacement.

## Canonical implementation retained

- `UniverseShell.kt`, `Universe2026Home.kt` and the feature-oriented Compose screens.
- `FounderStudioScreen.kt`, `MemberCenter.kt`, `SettingsHub.kt` and `VoiceAssistantDialog.kt`.
- Supabase auth, catalogue, member and Founder Studio repositories.
- Media3 background playback service/controller, audio effects and offline download manager.
- Signed native updater and Android app configuration.
- Original brand assets: `ht_logo.png`, HTE launcher/crown vectors and bundled `hte_universe_intro.mp3`.

## Compatible integration principles

- Keep one Activity entry point, one playback service and one Supabase client.
- Preserve `com.hightechentertainment.app`, existing production signing identity and existing Supabase data.
- Port only isolated fixes or useful behavior after verifying compatibility; do not import duplicate app shells or replace working features with starter placeholders.
- Do not run destructive database migrations or rotate credentials/signing keys as part of this integration.
- Treat voice service permission/configuration and real signed-in device testing as separate acceptance gates.

## CI repair work in this branch

- Guard account notifications when Android 13+ notification permission has not been granted.
- Remove stale manifest declarations for optional LiveKit screen capture and camera hardware.
- Apply explicit Media3 unstable API annotations to the playback service and fullscreen video dialog.
- Remove an API-27-only navigation-bar attribute from the API-26 base style.

## Verification status

The prior GitHub Actions run for the native screen upgrade compiled the application and passed unit tests, but Android lint failed with 16 errors. This branch contains targeted repairs for those findings. A new CI run must pass compile, unit tests and lint before signed release checks can be considered. No APK is declared release-ready by this document.
