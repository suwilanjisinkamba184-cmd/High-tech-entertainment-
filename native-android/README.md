# High-Tech Entertainment — Native Android 3.1

Native Android application workstream using Kotlin, Jetpack Compose and AndroidX Media3. The existing Flutter application remains the production fallback until native acceptance gates pass.

## Release identity
- Public version: **High-Tech Entertainment 3.1**
- Android versionName: `3.1.0`
- Android versionCode: `30` (greater than the existing Flutter 2.2.10 build code 27)
- Application ID: `com.hightechentertainment.app`
- Minimum Android: 8.0 / API 26
- Target SDK: API 36
- Public branding does not include the internal “SU” suffix.

## Implemented in the native branch
- Kotlin/Compose app shell, email/password authentication, encrypted session persistence and Google OAuth PKCE.
- Authenticated Supabase REST transport using OkHttp, including PATCH and Storage operations.
- Published music catalogue, Media3 background audio, queue controls, repeat/shuffle/seek and invisible bundled startup jingle.
- User favourites, listening-history recording and display, playlist create/add/remove/delete flows.
- Video catalogue and full-screen Media3 video playback.
- App-private offline audio downloads using Android DownloadManager; downloaded files are preferred for playback.
- Profile editing, per-user avatar upload and password change.
- Booking submission and owner booking history; founder booking inbox/status processing.
- Private Founder Studio entry, founder-only RLS-backed draft creation/publishing for tracks, artists, releases and videos, audio/video upload and sponsored-audio campaign scheduling.
- Scheduled sponsored audio inserted before music, with ad impression records and no listener-side ad-disable switch.
- GitHub signed-release update checker and installer that verifies package identity, higher version code, APK SHA-256 and signing certificate before Android's user-approved install flow.
- Initial native Voice Companion screen with Android on-device speech recognition, local-only TTS voice selection, saved voice preference and offline playback/navigation/search commands.

## Voice Companion scope
The initial native voice increment is press-to-talk only. It uses Android's on-device SpeechRecognizer when the device reports that an offline recognizer is available, and lists only Text-to-Speech voices that do not require a network connection. The microphone permission is requested only when the user taps the voice button. No always-on wake word is enabled. Offline language-pack availability and the actual selected voice must be verified on the target device. Online ElevenLabs commands, website integration, news/release announcements, recommendations and proactive notification delivery remain future integration work.

## Supabase integration
The existing production project already contained profiles, videos, user_favorites, playlists, playlist_tracks, listening_history, audio_ads and ad_impressions. The native implementation reuses those exact tables; it does not create parallel library tables.

Two additive production schema changes have been applied:
1. `bookings` table with owner-scoped read/insert and owner/admin processing policies.
2. Public avatar delivery for `hte-avatars`; upload/update/delete remain restricted to the authenticated user's UUID folder.

The migration source is `database/2026-11-07-high-tech-entertainment-3.1.sql`. Existing production music, users and storage objects were not reset or rewritten. The app still uses only the publishable key; RLS is authoritative.

## Build and validation
Requires JDK 17, Android SDK 36 and Gradle 9.8.2.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions debug APKs are validation artifacts, not production releases. Current feature commits are awaiting a fresh native build/test result. Live account tests, actual uploads, offline playback, ad delivery and physical-device acceptance must still be proven.

## Remaining acceptance work
- Complete end-to-end testing of user isolation, owner/admin RLS and actual Supabase Data API access on-device.
- Add playlist reorder, download manager UI/quota controls, and artwork/profile-image presentation polish.
- Add video upload progress/resume for large files; current native upload limit is 50 MB.
- Validate sponsored audio ordering, impression accounting, audio focus and no-skip behaviour on real devices.
- Run the signed-candidate workflow, compare its signing certificate with the installed production app, then test update installation over an existing installation.
- Test Android 8+, Android Go low-memory devices, tablets, font scaling, TalkBack, headset/Bluetooth controls, app process recreation and install-over.
- Do not merge or replace Flutter production until all gates pass.

## Security and release rules
- Never bundle service-role/secret keys or keystore material.
- Founder permissions are enforced by Supabase owner/admin RLS, not merely hidden navigation.
- Native updates are full signed APK installs; Flutter Shorebird cannot patch Kotlin, manifests, permissions, plugins or bundled native resources.
- APK install is blocked unless package ID, version code, APK hash and signer all match the manifest and the installed signer.
- Production remains untouched until the user explicitly approves a verified release.
