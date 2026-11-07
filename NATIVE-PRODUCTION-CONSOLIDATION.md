# High-Tech Entertainment — Native production consolidation register

## Release decision

- Native candidate: High-Tech Entertainment 3.1.0 (versionCode 30)
- Application ID: `com.hightechentertainment.app`
- Architecture: one native Android application, one Activity entry point, Compose UI, and feature-oriented packages for auth, data, playback, and updates.
- Flutter remains the production fallback. Do not replace it until all native acceptance gates pass.
- No production data reset, table recreation, or bulk content rewrite is authorized.

## Verified production backend

The connected production Supabase project was inspected read-only during consolidation:

- Project status: ACTIVE_HEALTHY; PostgreSQL 17.11.
- Existing catalogue data remains present: 4 tracks and 1 release.
- Existing Auth users remain present: 5.
- The additive `bookings` table exists and is empty.
- Existing `profiles`, `videos`, `user_favorites`, `playlists`, `playlist_tracks`, `listening_history`, `audio_ads`, and `ad_impressions` schemas were inspected and match the native repository's field usage.
- Storage buckets `hte-audio`, `hte-videos`, and `hte-avatars` exist. Public delivery is enabled for these media/avatar buckets; upload authorization remains governed by Storage policies.
- `public.is_hte_admin()` exists as SECURITY INVOKER; authenticated users have the required private role-check access.
- The native booking workflow migration versions are present in production migration history.

## Changes made during this consolidation

1. Corrected the malformed SQL function-body delimiter in `database/2026-11-07-high-tech-entertainment-3.1.sql`.
2. Aligned the migration source with least-privilege booking updates: authenticated users receive SELECT and INSERT; UPDATE is restricted to the `status` column. The live database received a separate additive privilege migration named `native_booking_status_only_updates`.
3. Preserved existing booking rows and all existing catalogue, user, and Storage objects. The privilege migration does not rewrite table data.

## Native feature implementation already present on the branch

- Kotlin / Jetpack Compose native application shell.
- Supabase email/password and Google OAuth PKCE.
- Encrypted session persistence and refresh management.
- Media3 background playback and invisible bundled startup jingle.
- Published music catalogue, favourites, listening history, and playlist create/add/remove/delete.
- Published video catalogue and full-screen video playback.
- App-private offline audio download and local-first playback.
- Profile editing, avatar upload, and password update.
- Booking submission, owner booking history, founder booking inbox/status updates.
- Founder Studio draft creation and publishing for tracks, artists, releases, and videos.
- Scheduled sponsored audio, queue insertion, and impression recording.
- Signed native updater with package, version, APK SHA-256, and signer-certificate verification.

## Remaining implementation and acceptance work

These are not marked complete until evidence exists:

- Add in-app download manager with progress, cancellation, quota/storage-full handling, and recovery.
- Add playlist reorder and verify position persistence.
- Complete artwork/avatar rendering and management.
- Complete sharing, account deletion, and user-facing privacy controls.
- Add upload progress/resume and large-file handling; current audio/video limits are 20 MB / 50 MB.
- Verify ad scheduling boundaries, failed-ad recovery, playback order, and impression idempotency.
- Run live two-account RLS tests for favourites, playlists, history, bookings, founder content, campaigns, and storage uploads.
- Run a fresh native CI build and unit tests; capture artifact and job logs.
- Compare the signed candidate certificate to the certificate on the installed production APK before attempting an in-place update.
- Physical-device test startup audio, background playback, audio focus, Bluetooth/headset controls, video playback, offline mode, app process recreation, Android Go memory pressure, API 26+, tablets, TalkBack, and font scaling.
- Test update install, app data preservation, rollback, and failure recovery.

## Production gates

1. No merge to production and no public native release while any critical CI, security, signer, or physical-device gate is open.
2. Keep Flutter deployed and installable as fallback until native acceptance passes.
3. Never store service-role/secret keys or keystore bytes in source control or the APK.
4. Supabase RLS remains the authorization boundary; hiding Founder Studio navigation is not sufficient.
5. Native OTA means a full signed APK update. Shorebird does not patch Kotlin/native Android code.
6. Only publish after the signed APK, manifest, checksum, signer, install-over test, and rollback procedure are verified.
