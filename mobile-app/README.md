# High-Tech Entertainment — Android application

The official Android client is a native Flutter application. The canonical public website and source repository are maintained separately from the private Founder Studio.

- **Official website:** https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/
- **Source repository:** https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-
- **Current source version:** 2.2.10 SU (build 28)
- **Android support:** Android 8.0 (API 26) and newer
- **Application ID:** `com.hightechentertainment.app`

## Product experiences

### HT Music — public listener experience

- Branded 15-second minimum startup screen with animated HTE logo and progress indicator.
- Bundled startup jingle plays automatically; the splash does not display sound status, volume icons, or an audio visualizer.
- Required account sign-in, Google OAuth/native Google sign-in when configured, and email/password authentication.
- Home, Discover, Releases, Library, Favourites, Playlists, Profile, search, artist catalogue, and music videos.
- Shared native playback service with queue, background controls, shuffle, autoplay, and administrator-configured sponsored audio.
- Spotify release links and embeds.
- Downloads, sharing, privacy, account deletion, update checks, and Android's user-approved APK installer.

### HT Admin — private founder experience

- Founder-only entry, backed by Supabase role checks and row-level security.
- Dashboard, music upload, track publishing/deletion, artist and release management, and sponsored audio-ad management.
- Artwork and video management.
- Role-checked user directory and aggregate listening/advertising analytics.
- Private on-device diagnostics with explicit redacted report export.

## Performance and reliability

- App tabs are mounted on first visit rather than all loading their catalogues at startup, reducing initial network and memory pressure on low-RAM Android devices.

## Responsive device support

- Phone layouts remain edge-to-edge, while tablets and foldables use a centred content width capped at 860 logical pixels.
- Startup branding scales down on compact or short screens to avoid splash-screen overflow.
- The home hero heading scales to the available width on narrow devices.
- Founder Studio metric cards use two, three, or four columns according to available width.
- Shared breakpoints are implemented in `lib/responsive_layout.dart` and covered by widget-test-compatible unit tests for compact phones, standard phones, tablets, and foldable-width layouts.
- Layouts preserve Android system text scaling and do not disable accessibility font settings.

## Audio architecture and startup behaviour

- Startup audio uses the bundled `assets/high-tech-startup.mp3` file; it does not depend on a network request, WebView, or JavaScript playback.
- Startup audio has its own process-scoped player, separate from the single shared music/advert player.
- The startup routine owns audio-session configuration. Do not configure the same audio session again in the splash initializer.
- Startup sound failures are logged to private diagnostics and must never trap the user on the splash or expose sound-status UI.
- The main music experience uses `HtePlayback.instance`. Screens must reuse that service; do not instantiate additional music players inside widgets, tabs, or routes.
- Background notification and lock-screen controls must operate the same shared player.
- The scheduled advert is separate from startup audio and is only eligible after eight completed music tracks. It must not play on startup or on pause/resume.

## Backend and data protection

Supabase project: `wqrxacjbsldbnoekbkbf`.

- The app uses the Supabase publishable key. A service-role key must never be bundled in the client.
- Public catalogue reads are controlled by database row-level security.
- Music storage uses `hte-audio`; artwork uses `hte-artwork`; video storage uses `hte-videos` (legacy `hte-video` files may remain).
- Founder uploads, publishing, deletion, user administration, and analytics require server-side role checks and appropriate RLS policies.
- User playlists, favourites, and listening history must remain scoped to the authenticated user.

## Build, test, and release

The GitHub Actions workflow is `.github/workflows/mobile-build.yml`. It creates the Android scaffold, installs dependencies, runs Flutter tests and static analysis, configures signing, builds the SU APK, verifies its signature, and publishes the APK as a GitHub Release asset.

Required repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `SHOREBIRD_TOKEN` (required for Shorebird release/patch publishing)
- `GOOGLE_WEB_CLIENT_ID` (optional; enables native Google sign-in instead of the OAuth deep-link fallback)

### Local checks

Run from `mobile-app/` with the Flutter SDK installed:

```bash
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

The playback regression suite covers the single-player service contract, direct media URL validation, and the eight-completed-track advert counter. CI also verifies that the bundled startup MP3 has readable duration metadata and decodes without stream errors.

A successful build verifies compilation and packaging, not audible output on every Android device. Startup audio, background playback, notification controls, account sign-in, and media streaming must also be verified on a physical Android device.

### Shorebird OTA rules

- A Shorebird patch updates Dart code for one exact published release. It cannot add/change bundled assets, native Android code, permissions, native plugins, or the Flutter engine.
- The workflow resolves the release version and build from `pubspec.yaml`, then targets the matching Shorebird release using the exact `version+build` value.
- Asset/native compatibility checks must not be bypassed. If a change needs assets, native code, a new native plugin, or a Flutter engine update, publish a new signed APK release instead.
- Pull requests validate only; publishing happens after approved changes reach `main`.
- Do not reuse a Shorebird patch baseline from an older app version. The Android app version, GitHub release tag, and Shorebird release must agree.

## Known verification limits

CI can verify formatting, static analysis, tests, APK creation, signature integrity, and release publication. It cannot prove the sound is physically audible, that every device's audio focus behaves identically, or that all Supabase accounts and media records behave correctly. Those checks require an installed build and a real-device acceptance test.
