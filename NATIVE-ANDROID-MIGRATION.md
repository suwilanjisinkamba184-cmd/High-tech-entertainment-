# High-Tech Entertainment — Native Android 3.1

## Release decision

The next public Android release is **High-Tech Entertainment 3.1** (Android versionName `3.1.0`, versionCode `30`). The public version name omits the internal “SU” suffix. Android architecture is Kotlin + Jetpack Compose + AndroidX Media3, retaining package ID `com.hightechentertainment.app`.

Flutter remains the production fallback until the native client passes every acceptance gate.

## Product contract

- Black, metallic-gold and silver High-Tech Entertainment identity.
- Listener features: Home, Discover, music catalogue, videos, search, favourites, playlists, listening history, profile, bookings and offline audio.
- Account authentication for personal library and bookings.
- Founder Studio is visible only to the authorized founder account in the native navigation; owner/admin authorization is independently enforced by Supabase RLS.
- Bundled startup jingle plays without visible player/status UI and never enters the music queue or advert rotation.
- Sponsored audio is server-controlled, scheduled, queued before music and accounted through ad impressions. There is no listener-side ad-disable switch.
- Reuse existing Supabase users, data and Storage objects; never reset production data.

## Implemented architecture

- Single Activity, Compose Material 3, lifecycle-aware state.
- Media3 ExoPlayer and MediaSessionService for background audio; Media3 PlayerView for full-screen video.
- Android DownloadManager for app-private offline audio downloads and local-first playback.
- OkHttp-based Supabase REST/Auth/Storage transport; only the publishable key is in the client.
- Existing owner-scoped `user_favorites`, `playlists`, `playlist_tracks`, `listening_history`, `profiles`, `videos`, `audio_ads` and `ad_impressions` tables are reused.
- New `bookings` table has owner-scoped read/insert and owner/admin processing RLS.
- `hte-avatars` is public for image delivery; object upload/update/delete remains limited to each user's UUID folder.
- Founder draft management covers tracks, artists, releases and videos. Founder uploads audio up to 20 MB and video up to 50 MB using existing public Storage buckets.
- Native updater checks GitHub release manifests, validates package identity, version code, APK SHA-256 and signing certificate, then invokes Android's user-approved PackageInstaller flow.

## Current limitations / remaining acceptance work

- Offline downloads have system-managed progress/notifications but no in-app download manager, explicit cancellation, quota management or checksum-on-download UI.
- Playlist reorder is not yet exposed.
- Profile/avatar upload is implemented; avatar image rendering and artwork/cover-image management need UI polish.
- Video upload currently caps at 50 MB and has no in-app upload progress/resume.
- Sharing, account deletion and complete privacy controls remain to be integrated.
- The signed-candidate workflow must be run to publish a manifest; then the signer must be matched against the installed production build before in-place installation can be approved.
- No physical-device acceptance has yet been completed for audio focus, headset controls, offline playback, OAuth, ad delivery, low-memory Android Go, TalkBack or font scaling.

## Supabase changes applied

- Added `public.bookings` with RLS: users submit/read their own bookings; owner/admin can read and process bookings. Anonymous privileges were revoked.
- Added `releases.description` for Founder Studio release drafts.
- Changed `hte-avatars` to public delivery, retaining per-user folder upload/update/delete policies.
- Existing production tracks, artists, releases, users, favourites, playlists, history, videos and campaigns were preserved.
- Migration source: `database/2026-11-07-high-tech-entertainment-3.1.sql`.

## Release gates

1. Verify the configured native signing certificate against the currently installed production APK.
2. Pass the fresh native Android CI build and unit tests.
3. Test Supabase Auth and RLS with separate ordinary-user and founder accounts.
4. Verify startup sound, sponsored audio ordering, Media3 video, downloads, background playback, headset controls and process recreation on physical devices.
5. Verify install, in-place upgrade, user data preservation and rollback.
6. Test API 26+, low-memory Android Go, current Android, tablet, TalkBack and font scaling.
7. Promote the native APK only after every gate passes and production release is explicitly approved.

**Do not merge or distribute the native app as a production replacement until all gates pass.**
