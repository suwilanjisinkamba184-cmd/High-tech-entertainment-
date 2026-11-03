# High-Tech Entertainment — Native Android App

The Flutter application in this folder is the native Android client for High-Tech Entertainment.\n\nOfficial website: https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/ Current app version: **2.1.6**.

## Experiences

### HT Music — public listener experience
- Branded animated startup, best-effort bundled startup advert, and required Supabase sign-in.
- Google OAuth and email/password authentication.
- Home, Discover, Releases, Library, Favourites, Playlists and Profile.
- Artist catalogue, music videos, search, track sharing and notifications placeholder.
- Native audio playback, queue, background controls, shuffle, autoplay and sponsored audio.
- Spotify release links and embeds.
- App settings, privacy, account deletion and in-app GitHub release updates with download progress, APK integrity checks and Android's user-approved package installer.

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
- Videos use the existing `hte-video` bucket and public `videos` catalogue.
- Public catalogue reads are governed by existing RLS policies.
- User playlists, favourites and listening history are scoped by the signed-in user's RLS policies.

The artwork bucket and admin listing migrations are recorded under `database/changes/` and have been applied to the connected project. The secure user directory and analytics functions are also applied and return records only to authenticated HTE owners/admins.

## Android build

The GitHub Actions workflow runs Flutter tests and static analysis, creates the Android platform scaffold, copies the Flutter source and assets, configures Android 8+ (min SDK 26), configures the app link and background audio service, and builds APK and AAB outputs. Signed Android releases use semantic version tags so the in-app update checker can compare versions.

Required repository Actions secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

Build locally from this directory:
```bash
flutter pub get
flutter analyze lib
flutter test
flutter build apk --release
```

Windows source files are preserved, but Windows build execution and release packaging are paused. The current CI validation and distributable release target is Android only. iOS distribution is not currently part of the supported release target.


## Single-instance audio playback

HT Music uses one process-wide `HtePlayback.instance` service and one `just_audio` `AudioPlayer`. The service owns the active track, queue, completion handling, audio-ad insertion, and playback error state. The mini-player, full player, queue screen, and all catalogue screens must read and control that same service; do not create `AudioPlayer()` instances in widgets, tabs, routes, or screen state objects.

- Background audio is initialized once at application startup with `JustAudioBackground.init` before the shared playback service is first accessed. Audio initialisation and the optional bundled startup advert are isolated from authentication startup: if either fails or the advert times out, the app continues to sign-in.
- Track changes replace the source on the shared player; navigation must not dispose or recreate it.
- The notification and lock-screen controls are backed by the same player and media metadata.
- The service is exposed through `HtePlayback.instance`. The `HtePlayback()` factory remains as a compatibility entry point and returns the same singleton.
- Run `flutter test` and `flutter analyze lib` after playback changes. Verify play/pause, previous/next, navigation persistence, notification controls, and locked-screen playback on a physical Android device.

A singleton prevents duplicate app-owned player instances, but a successful build alone does not prove Android background playback on every device; physical-device verification remains required.
