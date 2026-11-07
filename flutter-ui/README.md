# High-Tech Entertainment 3.1 — Flutter presentation module

This directory is the Flutter UI module embedded into the existing Kotlin Android host.

## Architecture contract

- Flutter owns presentation, navigation, responsive layouts, and UI animation.
- The Kotlin host remains the Android application host and owns Android lifecycle, authentication/session integration, Media3 audio/video playback, background playback, downloads, notifications, and permission handling.
- Supabase remains the existing backend. Do not create a second production database or duplicate playback engine.
- Native-to-Flutter calls use typed, versioned platform-channel contracts. The startup audio method channel is scaffolded as `com.hightechentertainment.app/startup`.

## Startup acceptance requirement

The branded startup sequence is exactly 15 seconds. Native Kotlin starts the existing bundled startup sound when the Flutter splash becomes visible. Flutter runs the matching 15-second animation/progress timeline. The host must stop/release the startup player at the 15-second boundary and cancel safely on lifecycle interruption. The startup sound must never enter Media3's music queue or sponsored-audio rotation.

The current Dart screen contains the synchronized 15-second visual timeline and calls the native startup bridge. The Kotlin bridge and FlutterView host wiring are the next integration step; until that is complete, the sound call is intentionally safe to no-op in Flutter preview.

## Build

Use Flutter stable 3.47.9 (Dart 3.13.x), Java 17, and the Android SDK configured by the native host CI. Do not use beta/main for production. Generated `.android/` contents are created by Flutter and must not be committed.

Run from this directory:

```sh
flutter pub get
flutter test
```

The Android host imports this module using Flutter's source-subproject add-to-app integration.
