# High-Tech Entertainment — Native Android App

The Flutter application in this folder is the native Android client for High-Tech Entertainment.\n\nOfficial website: https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/ Current app version: **2.2.3**.

## Experiences

### HT Music — public listener experience
- Branded animated startup with a separate best-effort startup sound, followed by required Supabase sign-in.
- Native Google account chooser when GOOGLE_WEB_CLIENT_ID is configured, with a Supabase Google OAuth deep-link fallback; email/password authentication includes deep-link password recovery.
- Home, Discover, Releases, Library, Favourites, Playlists and Profile.
- Artist catalogue, music videos, search, track sharing and RLS-protected in-app announcements.
- Native audio playback, queue, background controls, shuffle, autoplay and sponsored audio.
- Spotify release links and embeds.
- App settings, privacy, account deletion and automatic GitHub release checks on Home, plus manual update checks, download progress, APK integrity checks and Android's user-approved package installer.

### HT Admin — private founder studio
- Founder-only entry for the configured owner account.
- Dashboard, music upload, track publishing and deletion.
- Artist and release draft creation and publishing.
- Public artwork upload, artwork library listing and URL management.
- Secure user directory through a role-checked Supabase function; destructive account actions and role changes remain server-side only.
- Live play, unique-listener and ad-impression analytics through a role-checked aggregate function; download counts remain unavailable until download events are recorded.
- Recent catalogue activity; a full actor-by-actor audit trail requires a dedicated audit-log table.

## Supabase integration

Project: `wqrxacjbsldbnoekbkbf`

- Auth uses the Supabase publishable key; no service-role key is bundled.
- Music files use the public `hte-audio` bucket. Upload/delete policies require an HTE owner/admin role.
- Release artwork uses `hte-artwork`; public reads are enabled while upload/update/delete are restricted to HTE owner/admin roles.
- Legacy video files remain in `hte-video`; current Founder Studio uploads use the public `hte-videos` bucket (500 MB limit) with owner/admin-only write policies and the public `videos` catalogue.
- Public catalogue reads are governed by existing RLS policies.
- User playlists, favourites and listening history are scoped by the signed-in user's RLS policies.
- In-app announcements use `app_notifications`; authenticated users can read broadcasts and their own notifications, while only HTE owners/admins can create announcements.
- Public profile photos use the public `hte-artwork` bucket under `avatars/<user-id>/`; upload/delete policies restrict each signed-in user to their own folder, with a 5 MB app-side upload limit.

The artwork, in-app notification and video-storage changes are recorded under `database/changes/` and have been applied to the connected project. The secure user directory and analytics functions are also applied and return records only to authenticated HTE owners/admins.

## Android build

The GitHub Actions workflow runs Flutter tests and static analysis, creates the Android platform scaffold, copies the Flutter source and assets, configures Android 8+ (min SDK 26), configures the app link and background audio service, and builds APK and AAB outputs. Signed Android releases use semantic version tags so the in-app update checker can compare versions.

Required repository Actions secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_WEB_CLIENT_ID` (optional; enables the native Google account chooser instead of the Supabase OAuth deep-link fallback)
- `SHOREBIRD_TOKEN` (required; used only by GitHub Actions to publish signed OTA patches and Shorebird releases)

## OTA updates

The Android release workflow initializes Shorebird on the first production build and commits the generated `shorebird.yaml` configuration to `mobile-app/`. Dart-only changes under `mobile-app/lib/` automatically publish a patch to the stable track. Changes to assets, dependencies, app configuration, or build workflows trigger a full signed Shorebird APK release; increment the app version/build number in `pubspec.yaml` before that release. Pull requests run validation only. You can also run the workflow manually and choose `patch` or `release`. Shorebird patches cannot update native Android code, permissions, native plugin changes, or bundled assets; those require users to install a new APK.

Build locally from this directory:
```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --release
```

Windows source files are preserved, but Windows build execution and release packaging are paused. The current CI validation and distributable release target is Android only. iOS distribution is not currently part of the supported release target.


## Single-instance audio playback

HT Music uses one process-wide `HtePlayback.instance` service and one `just_audio` `AudioPlayer`. The service owns the active track, queue, completion handling, audio-ad insertion, and playback error state. The mini-player, full player, queue screen, and all catalogue screens must read and control that same service; do not create `AudioPlayer()` instances in widgets, tabs, routes, or screen state objects.

- Background audio is initialized once at application startup with `JustAudioBackground.init` before the shared playback service is first accessed. The separate bundled startup sound and scheduled advert are isolated from authentication startup: if either fails, the app continues to sign-in. The scheduled advert is only eligible after eight completed music tracks, never on startup or pause/resume. The completion count is saved locally so a normal app restart does not reset the interval.
- Track changes replace the source on the shared player; navigation must not dispose or recreate it.
- The notification and lock-screen controls are backed by the same player and media metadata.
- The service is exposed through `HtePlayback.instance`. The `HtePlayback()` factory remains as a compatibility entry point and returns the same singleton.
- Run `flutter test` and `flutter analyze lib` after playback changes. Verify play/pause, previous/next, navigation persistence, notification controls, and locked-screen playback on a physical Android device.

A singleton prevents duplicate app-owned player instances, but a successful build alone does not prove Android background playback on every device; physical-device verification remains required.

## Network and error handling

The app converts low-level connectivity and service exceptions into plain-language messages instead of exposing raw exception text, backend response codes or stack traces.

- When a network request fails because the device is offline or cannot reach the service, the user sees: **“No internet connection. Connect to the internet and try again.”**
- Authentication, media-not-found, permission, unsupported-format and local-storage failures receive specific plain-language guidance; unknown failures use: **“Something went wrong. Please try again.”**
- During startup, the existing **Try again** button reruns service initialisation after the user reconnects.
- Diagnostic exception details are retained only in developer logs where applicable; they are not included in these user-facing messages.

