# High-Tech Entertainment Native Android 3.1 — Voice Companion and Release Audit

**Audit date:** 2026-11-07  
**Target:** `native-android/`, Kotlin + Jetpack Compose + AndroidX Media3  
**Release identity:** `com.hightechentertainment.app`, versionName `3.1.0`, versionCode `30`  
**Branch:** `migration/native-android-kotlin-3.0-foundation`  
**PR:** https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/pull/53

## Executive result

**Not release-ready.** The native app has a substantial feature foundation, but the most recent CI runs failed at Kotlin compilation. No validation APK was produced by those runs, no signed production candidate has been verified, and the voice assistant is not fully implemented online or offline.

Do not merge this PR or replace the production app until all release gates below pass.

## Speech engine decision

The Android system `TextToSpeech` engine must not be used by the High-Tech Voice Companion.

Recommended architecture:

- **Online speech / conversational agent:** ElevenLabs Conversational AI using the official Android Kotlin SDK. Use a private agent and obtain short-lived conversation credentials from a Supabase Edge Function. Never put an ElevenLabs API key in the APK.
- **Offline neural speech synthesis:** sherpa-onnx Android TTS. The engine is Apache-2.0 licensed and supports Android/Kotlin; the chosen model and voice assets must have their own licence checked before bundling or downloading.
- **Recognition:** keep speech recognition independent from synthesis. The current screen uses Android's on-device `SpeechRecognizer` when available. This is not Android Text-to-Speech. Validate whether its offline recognizer and language pack actually work on the target phone.
- **Activation:** press-to-talk only. No always-on microphone or wake-word claim.
- **Audio policy:** voice responses must not be mixed loudly over music. Implement transient ducking or pause/resume with audio-focus restoration, and allow the user to disable spoken responses.
- **Fallback:** never silently fall back to Android/Google TTS. If online voice is unavailable and the offline model is not installed, show a clear unavailable state.

Reference documentation:
- ElevenLabs Android SDK: https://github.com/elevenlabs/elevenlabs-android
- ElevenLabs streaming/TTS API: https://elevenlabs.io/docs/overview/intro
- sherpa-onnx Android: https://k2-fsa.github.io/sherpa/onnx/android/index.html
- sherpa-onnx Android TTS APK/model guidance: https://k2-fsa.github.io/sherpa/onnx/tts/apk.html

## Verified defects and corrections

### 1. Kotlin compilation failure — fixed in source, build still needs re-run

Both inspected GitHub Actions runs failed with:

```
MainActivity.kt:648:48
Unresolved reference: playbackPlayer
```

The failure was caused by the Voice Companion screen referencing an Activity-owned player property from a separate composable scope.

Correction committed to this branch:
- Pass the stop callback through the `HighTechApp` composable boundary.
- Add an explicit favourites playback callback from the Activity.
- Build a dedicated queue containing the user's favourites instead of selecting only the first favourite.
- Keep sponsored audio insertion in the existing queue policy.

**Verification pending:** the source correction must be proven by a fresh successful Kotlin compile, unit-test run, debug APK build and startup-jingle package check.

### 2. Android Text-to-Speech — removed from Voice Companion

The previous `VoiceCompanionScreen.kt` instantiated `android.speech.tts.TextToSpeech`, enumerated installed Android voices and spoke responses through that engine.

Correction committed:
- Removed Android `TextToSpeech` and `Voice` imports and lifecycle.
- Removed voice selection and test-voice UI tied to Android TTS.
- Removed the call that spoke command responses through Android TTS.
- Added an explicit UI state explaining that dedicated ElevenLabs and sherpa-onnx providers are pending.
- Kept press-to-talk speech recognition separate from synthesis.

**Current limitation:** command recognition and app actions can work, but spoken responses are deliberately disabled until the replacement providers are implemented and validated. This avoids playing the unwanted Android/Google TTS voice.

### 3. Favourites command — corrected in source, needs playback test

The previous implementation selected only the first favourite and called the general play callback. It did not create a favourites-only queue.

Correction committed:
- Create a queue from all favourite tracks that have audio URLs.
- Preserve deterministic favourites order and the existing sponsored-audio insertion policy.
- Show an error when there are no playable favourites.

**Verification pending:** test next/previous, repeat, shuffle interaction, sponsored-ad insertion, and playback continuation on-device.

## Current implementation inventory (branch documentation and source review)

The native PR describes the following implemented foundation:
- Kotlin/Compose application shell.
- Email/password authentication and Google OAuth PKCE.
- Encrypted session persistence and refresh management.
- Supabase Auth/REST/Storage transport using the publishable key.
- Media3 background audio, queue controls, repeat, shuffle, seek and bundled startup jingle.
- Catalogue, favourites, listening history and playlist create/add/remove/delete.
- Video catalogue and full-screen playback.
- App-private offline audio downloads.
- Profile editing, avatar upload and password update.
- Booking submission/history and founder booking inbox/status changes.
- Founder Studio draft creation/publication for music, artists, releases and videos.
- Sponsored audio scheduling, queue insertion and impression recording.
- Signed updater checks for package, version, APK SHA-256 and signing certificate.

This inventory is based on branch documentation and source review; it is not a claim that every feature has passed live integration or device testing.

## Online Voice Companion work still required

- Create and configure the correct High-Tech platform-wide ElevenLabs agent (the workspace check found no current agent with the High-Tech name).
- Configure a private agent, prompt, voice and safe command policy.
- Add the official ElevenLabs Android SDK at a pinned, verified version.
- Add a Supabase Edge Function that authenticates the signed-in user and issues short-lived conversation credentials. Keep the ElevenLabs API key server-side.
- Implement Android session lifecycle, connection state, transcript handling, reconnect, cancellation, microphone controls and error states.
- Implement app client tools for safe listener actions: play/pause/next/previous/search/navigation/settings.
- Ensure any Founder Studio or admin tool performs a fresh server-side role check. Never trust the agent prompt, spoken words, UI visibility or client-side boolean as authorization.
- Add website integration using the same authorised agent and command contract, with browser-specific tools and permissions.
- Add verified content retrieval for official news/releases/videos. The assistant must not invent company announcements or claim a draft is published.
- Add opt-in recommendation/announcement preferences and notification controls.

## Offline speech work still required

- Integrate sherpa-onnx native Android runtime for supported ABIs.
- Choose a compact, suitable English voice model; check model licence separately from engine licence.
- Download the model on demand over HTTPS, verify its checksum, store it in app-private storage and allow deletion.
- Add a local model availability/version state and a no-network test.
- Run inference off the UI thread and enforce memory/time limits.
- Test on Android Go memory pressure before making offline TTS a default.
- Do not bundle a large speech model into the base APK without measuring the installed size and memory cost.

## Remaining application and release acceptance gates

### Priority 0 — build and core app stability
- Re-run CI after the compilation correction; resolve every new Kotlin, Gradle, resource or test failure.
- Confirm startup jingle is embedded, plays once, is not shown as a UI element, and does not break media audio focus.
- Verify auth restore, token refresh, sign-out and Google OAuth callback across process death.
- Verify catalogue, player service lifecycle, notification controls, headset/Bluetooth controls and process recreation.
- Verify video playback and cleanup, including rotation/background/resume.

### Priority 1 — backend authorization and data integrity
- Run two-account tests for user-owned favourites, playlists, listening history, downloads and bookings.
- Verify founder denial for a normal account and founder allow for the authorised account at the backend/RLS boundary.
- Verify Storage upload ownership and path restrictions.
- Verify sponsored audio selection, ordering, no listener-side bypass, and impression accounting.
- Enable Supabase leaked-password protection before public release.
- Review existing SECURITY DEFINER RPC warnings and confirm fixed search_path and explicit role checks.

### Priority 1 — downloads and media
- Implement quota enforcement and storage-full recovery.
- Verify download cancellation, retry, removal, duplicate handling and local-first playback.
- Add upload progress/resume and robust error recovery for larger video uploads.
- Verify artwork/avatar rendering and image lifecycle.

### Priority 1 — update and signing
- Run the signed-candidate workflow using repository secrets without exposing them.
- Compare the candidate certificate with the certificate of the currently installed production app.
- Verify versionCode upgrade, APK hash, package identity, tamper rejection, install-over, data preservation and rollback.
- Native Kotlin, manifest, permission and native-library changes require a full signed APK installer update; Flutter Shorebird OTA cannot deliver them.
- Do not call a debug validation artifact a production APK.

### Priority 2 — accessibility and device coverage
- Test Android API 26+.
- Test the target itel A50 / Android 14 Go specifically for memory, audio, offline speech and storage.
- Test compact phones, tablets, font scaling, TalkBack, touch targets, process recreation and low-storage conditions.
- Confirm notification permission handling and battery/background playback behaviour.

## CI evidence captured during this audit

Two recent workflow runs were inspected:

- Run `42360601035`, job `128368028952`: failed at `:app:compileDebugKotlin`.
- Run `42360601030`, job `128368024873`: failed at `:app:compileDebugKotlin`.

Both failed on the same unresolved reference in `MainActivity.kt`. The startup MP3 verification step was skipped because compilation failed. The artifact upload found no APK. Therefore there is no validation APK from those runs.

## Release decision

**HOLD — not approved for merge or public release.**

The immediate source corrections have been committed to the native PR branch. Next, run CI on the corrected branch, then continue the online ElevenLabs integration and offline sherpa-onnx integration as a coordinated implementation. Only after CI, backend isolation, signing and physical-device acceptance pass should a signed release candidate be published.
