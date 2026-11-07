# High-Tech Entertainment Native Android 3.1 — Voice Companion and Release Audit

**Audit date:** 2026-11-07  
**Audited revision:** `19a4940c97bc15d6a3024d043a35f9b66d208fb4`  
**Target:** `native-android/`, Kotlin + Jetpack Compose + AndroidX Media3  
**Release identity:** `com.hightechentertainment.app`, versionName `3.1.0`, versionCode `30`  
**PR:** https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/pull/53

## Executive result

**Automated build and signing gates pass; production release remains on HOLD pending live integration and physical-device acceptance.**

The previous audit's statements that the latest compile failed and no signed candidate existed are obsolete. On revision `19a4940`, all of these GitHub Actions workflows completed successfully:

| Workflow | Run | Result |
|---|---:|---|
| Native Android validation | `42366841775` | Passed |
| Native Android CI | `42366841783` | Passed |
| Native Android signed candidate | `42366841746` | Passed |
| Website validation | `42366841827` | Passed |

The signed-candidate workflow built the release APK, verified package/version identity, aligned and signed it, and compared its signer SHA-256 against the publicly published `v2.2.10-SU` APK. The comparison passed. Artifact `13572655239` contains the signed 3.1.0 candidate, checksum and verification manifest. It is a workflow artifact, not a public production release.

The legacy Android Release & OTA workflow also completed, but it used the Flutter/Shorebird pipeline. Its release/OTA publication steps were skipped. It is not evidence that the Kotlin application was published.

## Architecture and current implementation

The active target is native Android: Kotlin, Jetpack Compose, AndroidX Media3, minimum API 26. Do not resume the abandoned Flutter implementation for this release.

The PR documents and source contain the following implementation foundation:
- Email/password authentication, Google OAuth PKCE, encrypted session persistence and refresh.
- Authenticated Supabase REST/Auth/Storage transport using the publishable key.
- Media3 background audio service, queue controls and invisible bundled startup jingle.
- Published catalogue, favourites, listening history and playlists.
- Published video catalogue and full-screen video playback.
- App-private offline audio downloads and local-first playback.
- Profile editing, avatar upload and password update.
- Booking submission/history and founder booking inbox/status updates.
- Founder Studio draft creation/publication for tracks, artists, releases and videos.
- Scheduled sponsored audio, queue insertion and impression recording.
- Signed update checker/installer with package, version, checksum and signer verification.
- Online ElevenLabs Voice Companion session, authenticated short-lived token endpoint and client tools for playback, navigation and catalogue search.

This list is an implementation inventory, not proof every live backend path or device behavior has passed acceptance.

## Voice Companion — verified scope and remaining work

### Online
- Existing ElevenLabs agent: `agent_2601m6tt0dszfqaa7ec4jefe96ww`.
- Agent prompt restricts it to listener help and prohibits fabricated catalogue/news data and privileged actions.
- Three client tools are configured: `control_playback`, `navigate_app`, and `search_catalogue`.
- Supabase Edge Function `elevenlabs-voice-token` is ACTIVE with JWT verification enabled. It validates the user's Supabase session and requests a short-lived ElevenLabs conversation token server-side.
- Native app includes session start/end, microphone permission/mute, transcript/status UI, and callbacks for supported app actions.

**Not yet proven:** a real signed-in conversation on a physical device, audio focus/ducking behavior, provider credentials in the live Supabase function, reconnect/rotation/process-death behavior, and browser integration on the website. Do not claim website integration is complete.

### Offline
- The app has a separate press-to-talk on-device speech-recognition path where Android reports an on-device recognizer.
- Android/Google Text-to-Speech is not used.
- Offline neural speech synthesis via sherpa-onnx is **not implemented**. Consequently, offline mode does not yet provide spoken responses. Do not silently substitute Android TTS.
- Activation is press-to-talk only; no wake-word or always-listening capability is claimed.

## Backend authorization snapshot

A read-only inspection of current production RLS policies found:
- User-owned policies for favourites, playlists, listening history and profiles.
- Published-only public reads for tracks and videos, with owner/admin policies for management.
- Booking insert/select ownership policies and an owner/admin processing policy.
- Owner/admin checks for audio-ad campaign writes.
- Ad impression insertion constrained to the authenticated user's own `user_id`.

The earlier applied booking column grant restricts authenticated client updates to booking status; the owner/admin RLS policy remains the row-level gate. These policy inspections are not substitutes for two-account end-to-end tests.

The prior audit also identified Supabase Auth leaked-password protection as disabled. This setting still needs to be enabled in the Supabase Auth dashboard before public release. Existing SECURITY DEFINER warnings require final review of function definitions and grants.

## Remaining release blockers

### P0 — physical-device acceptance
- Install the signed candidate over the currently installed production app on the target itel A50 / Android 14 Go.
- Confirm the app launches, restores the expected account state and preserves user data.
- Confirm startup jingle plays once, remains invisible in the UI and does not disrupt music playback.
- Test background playback, notification controls, headset/Bluetooth actions, audio focus and process recreation.
- Test video playback, downloads, airplane mode, low-storage behavior and app update/rollback.
- Verify API 26+ compatibility, compact layouts, tablet layout, TalkBack and font scaling.

### P1 — live security and integration
- Run two-account tests for favourites, playlists, history, downloads and bookings.
- Prove normal-user Founder Studio denial and authorized-owner access through backend/RLS, not only UI state.
- Verify Storage upload ownership and path restrictions.
- Verify sponsored-audio ordering, playback policy and impression accounting.
- Test Google OAuth callback and session refresh after process death.
- Confirm `ELEVENLABS_API_KEY` is configured correctly as a server secret; never expose it in the APK or logs.
- Enable leaked-password protection and complete SECURITY DEFINER review.

### P1 — incomplete product capabilities
- Add download quota enforcement, duplicate recovery and storage-full handling.
- Add upload progress/resume and safer streaming upload behavior for larger media.
- Complete artwork/avatar rendering and management polish.
- Complete sharing, account deletion and user-facing privacy controls.
- Implement offline neural speech synthesis with a suitably licensed compact sherpa-onnx model; benchmark memory/latency on Android Go.
- Integrate the Voice Companion into the website with the same authorization and truthfulness rules.
- Add user-controlled notification categories/frequency and verified announcements/release briefings.

## Release decision

**HOLD production merge/release.** The current automated native build, unit tests, website validation, signed APK generation and signer comparison pass. The remaining blockers are live two-account authorization tests, real ElevenLabs credential/session verification, offline speech and other incomplete product capabilities, plus physical install-over/data-preservation tests.

Do not describe the current candidate as fully complete or publicly released. Do not replace the existing production app or Flutter fallback until the above acceptance gates are completed. Native Kotlin changes require a signed APK installer; Shorebird OTA only applies to Flutter/Dart changes.
