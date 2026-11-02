# High-Tech Entertainment — Native Android App

The Flutter application in this folder is the native Android client for High-Tech Entertainment. Current app version: **2.1.0**.

## Experiences

### HT Music — public listener experience
- Branded startup and required Supabase sign-in.
- Google OAuth and email/password authentication.
- Home, Discover, Releases, Library, Favourites, Playlists and Profile.
- Artist catalogue, music videos, search, track sharing and notifications placeholder.
- Native audio playback, queue, background controls, shuffle, autoplay and sponsored audio.
- Spotify release links and embeds.
- App settings, privacy, account deletion and GitHub release update checks.

### HT Admin — private founder studio
- Founder-only entry for the configured owner account.
- Dashboard, music upload, track publishing and deletion.
- Artist and release draft creation and publishing.
- Public artwork upload, artwork library listing and URL management.
- Secure user directory through a role-checked Supabase function; destructive account actions and role changes remain server-side only.
- Catalogue analytics and activity views; audience-wide metrics require a secure aggregate endpoint.

## Supabase integration

Project: `wqrxacjbsldbnoekbkbf`

- Auth uses the Supabase publishable key; no service-role key is bundled.
- Music files use the public `hte-audio` bucket. Upload/delete policies require an HTE owner/admin role.
- Release artwork uses `hte-artwork`; public reads are enabled while upload/update/delete are restricted to HTE owner/admin roles.
- Videos use the existing `hte-video` bucket and public `videos` catalogue.
- Public catalogue reads are governed by existing RLS policies.
- User playlists, favourites and listening history are scoped by the signed-in user's RLS policies.

The artwork bucket and admin listing migrations are recorded under `database/changes/` and have been applied to the connected project. The secure user directory function is also applied and returns records only to authenticated HTE owners/admins.

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

The CI workflow also builds Windows separately. iOS distribution is not currently part of the supported release target.
