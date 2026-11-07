# High-Tech Entertainment 3.1 — Flutter presentation module

This directory is the Flutter UI module embedded into the existing Kotlin Android host.

## Architecture contract

- Flutter owns presentation, navigation, responsive layouts, and UI animation.
- The Kotlin host remains the Android application host and owns Android lifecycle, authentication/session integration, Media3 audio/video playback, background playback, downloads, notifications, and permission handling.
- Supabase remains the existing backend. Do not create a second production database or duplicate playback engine.
- Native-to-Flutter calls currently use the startup bridge `com.hightechentertainment.app/startup`. Expand this into a versioned, typed Pigeon API as feature migration proceeds.

## Priority One: synchronized 15-second startup

- The Flutter splash begins its 15-second animation/progress timeline when it requests native startup playback.
- Kotlin plays the existing bundled startup MP3 in a dedicated MediaPlayer, outside the Media3 music queue and sponsored-audio rotation.
- Kotlin has an independent 15-second stop/release guard; Flutter also sends `stopStartup` at the visual boundary and when its splash widget is disposed.
- If the bundled cue is shorter than 15 seconds, Kotlin loops it until the 15-second boundary. If another app is already playing music, the startup cue does not interrupt it.
- The splash is not allowed to report fake network or account setup completion; its status text is brand/experience progress only.

## Migration status

The Flutter module is now included as a source Gradle subproject in the Kotlin host, and `FlutterHostActivity` is the launcher. The Flutter home currently provides the first visual shell and a temporary handoff to the existing native app. This is an incremental migration: the complete listener and Founder Studio workflows still need to be ported to Flutter and connected to the existing native service APIs before a production release.

## Build

Use Flutter stable 3.47.9 (Dart 3.13.x), Java 17, and the Android SDK configured by the native host CI. Do not use beta/main for production. Generated `.android/` contents are created by Flutter and must not be committed.

Run from this directory:

```sh
flutter pub get
flutter analyze
flutter test
```
