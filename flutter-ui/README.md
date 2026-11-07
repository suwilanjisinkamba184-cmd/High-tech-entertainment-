# High-Tech Entertainment 3.1 — Flutter presentation module

This directory is the Flutter UI module embedded into the existing Kotlin Android host.

## Architecture contract

- Flutter owns **all user-facing screens, navigation, responsive layout, accessibility, forms and interaction**.
- Kotlin is retained as the Android platform layer: lifecycle, secure OAuth callbacks, permissions, Media3 playback/background service, downloads, native file access, notifications and system intents.
- Supabase remains the only production backend. Preserve the existing project, authentication, tables, storage buckets, Edge Functions and row-level security.
- ElevenLabs remains the existing AI agent provider. API credentials must stay in Supabase Edge Function secrets and must never be packaged in the app.
- Native playback is a single shared Media3 engine. Flutter controls it through `com.hightechentertainment.app/data`; the startup cue uses the separate `com.hightechentertainment.app/startup` channel.

## Full migration status

This branch is the full Flutter app migration for Android 3.1. Flutter is the target for every user-facing experience; the legacy Compose app is not the destination UI. Any remaining native screen handoff is temporary migration debt and must be removed before release.

Implemented in the current branch: branded startup, Flutter email/password and Google OAuth flows, session restoration, listener catalogue screens, profile and booking UI, basic Founder Studio, sponsored-audio campaign controls, playlist/library UI, native document picker and bounded media/avatar upload bridges, plus a Flutter Voice Companion screen backed by the existing ElevenLabs Android SDK.

Still incomplete or awaiting verification: full settings/privacy and account deletion processing, complete Founder Studio draft/campaign edit/delete workflows, live ElevenLabs token configuration and conversation test, automated upload tests, Android 8+/Android Go physical testing and full release acceptance.

See `docs/FULL-APP-MIGRATION-3.1.md` for the feature-parity checklist and release gates. Do not merge or publish until those gates pass.

## Priority One: synchronized 15-second startup

- The Flutter splash begins its 15-second animation/progress timeline when it requests native startup playback.
- Kotlin plays the existing bundled startup MP3 in a dedicated MediaPlayer, outside the Media3 music queue and sponsored-audio rotation.
- Kotlin has an independent 15-second stop/release guard; Flutter also sends `stopStartup` at the visual boundary and when its splash widget is disposed.
- If the bundled cue is shorter than 15 seconds, Kotlin loops it until the 15-second boundary. If another app is already playing music, the startup cue does not interrupt it.
- The splash is not allowed to report fake network or account setup completion; its status text is brand/experience progress only.

## Build

Use Flutter stable 3.47.9 (Dart 3.13.x), Java 17, and the Android SDK configured by the native host CI. Do not use beta/main for production. Generated `.android/` contents are created by Flutter and must not be committed.

Run from this directory:

```sh
flutter pub get
flutter analyze
flutter test
```
