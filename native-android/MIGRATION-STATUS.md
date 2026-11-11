# High-Tech Entertainment 3.1 — native Android execution register

**Public version:** High-Tech Entertainment 3.1  
**Android versionName/versionCode:** 3.1.0 / 30  
**Package:** `com.hightechentertainment.app`  
**Architecture:** Kotlin, Jetpack Compose, AndroidX Media3; minimum API 26.  
**Production safety:** Flutter remains the production fallback. Existing Supabase data and Storage objects are preserved.

## Implemented on the native branch
- Compose application shell, catalogue/search, email/password auth, Google OAuth PKCE and encrypted session storage.
- Authenticated Supabase REST transport with OkHttp PATCH support.
- Media3 background audio service, queue controls, repeat/shuffle/seek and bundled invisible startup jingle.
- Favourites using existing `user_favorites` table.
- Listening-history events and history screen using existing `listening_history` table.
- Playlist create, add tracks, list members, remove tracks and delete playlist using existing `playlists` and `playlist_tracks`.
- Published video catalogue and full-screen Media3 playback.
- Offline downloads using Android DownloadManager and local-file-first playback.
- Profile edit, public avatar upload to per-user folder, and password update.
- Booking form, user booking history, founder booking inbox and status updates.
- Founder-only navigation and server-enforced owner/admin RLS for studio actions.
- Founder draft creation and publication for tracks/artists/releases/videos; audio upload up to 20 MB and video upload up to 50 MB.
- Sponsored audio campaign scheduling, publish/pause, playback insertion and impression writes.
- Signed update checker/installer with package, version, checksum and signer verification.

## Production Supabase changes applied
- Added `public.bookings` with RLS: users submit/read their own bookings; owner/admin can read and process bookings.
- Changed `hte-avatars` to public delivery; existing per-user folder upload/update/delete policies remain.
- Reused existing `videos`, `profiles`, `user_favorites`, `playlists`, `playlist_tracks`, `listening_history`, `audio_ads` and `ad_impressions` tables. No duplicate library tables were created.
- Existing production music rows and users were preserved.

## Still requires completion evidence

| Area | Current position | Acceptance evidence still needed |
|---|---|---|
| Favourites/history/playlists | UI and REST integration committed | Live owner-isolation tests on two accounts |
| Videos | Native catalogue/player and upload flow committed | Actual Supabase video upload + physical playback test |
| Offline audio | System downloads and local-first playback committed | Resume, quota, storage-full and airplane-mode tests |
| Profiles/password | Edit, avatar upload and password API committed | Live Auth and avatar upload test |
| Bookings | Table applied, form and founder status workflow committed | End-to-end user/founder RLS test |
| Founder Studio | Draft create/publish and campaign controls committed | Confirm owner/admin role behaviour with non-founder denial |
| Sponsored audio | Schedule and queue integration committed | Real ad playback, no-skip and impression test |
| Native updater | Manifest verifier and PackageInstaller flow committed | Run signed-candidate workflow and install over matching certificate |
| Accessibility/adaptive | Not yet fully tested | TalkBack, font scale, tablets and Android Go |
| Production release | Not approved | Fresh CI pass, signing match, device install-over and user approval |

## Release gates
1. Preserve Flutter production until native tests pass.
2. Never reset/recreate production Supabase data.
3. Keep service-role credentials and signing secrets out of source control.
4. RLS and the private owner/admin role system remain authoritative.
5. A debug APK is not a signed production release.
6. Shorebird OTA is Flutter/Dart-only; native changes require a signed APK installer update.
7. Do not publish a production update until the signing certificate and version upgrade are verified on a physical device.

**Current assessment:** Feature implementation is substantially expanded and committed to the development branch. Fresh native CI is still required; live Supabase/device acceptance remains outstanding.
