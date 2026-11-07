# High-Tech Entertainment 3.1 — native Android execution register

**Public release identity:** High-Tech Entertainment 3.1  
**Android versionName/versionCode:** 3.1.0 / 30  
**Architecture:** Kotlin, Jetpack Compose, AndroidX Media3; minimum Android API 26.  
**Production safety:** Flutter remains the production fallback. Preserve the existing Supabase project, users, tables and Storage objects.

## Verified foundation

- Native Compose application shell, dark metallic-gold styling and existing application ID `com.hightechentertainment.app`.
- Supabase REST transport using the publishable key, with RLS authoritative and no service-role key in the client.
- Published-track catalogue query with loading, refresh, empty/error states and local search.
- Email/password authentication, signup confirmation handling, password-reset request, encrypted AES-GCM session persistence and session refresh manager.
- Google OAuth PKCE authorization and callback implementation; live provider/callback validation remains outstanding.
- Media3 playback service, queue, next/previous, repeat, shuffle and seek controls.
- Separate bundled startup-jingle player; CI verifies the MP3 is packaged, but physical-device audible playback must still be tested.
- CI previously passed native unit/build/APK packaging validation on commit `160df1e`; new 3.1 commits require fresh CI confirmation.

## 3.1 backend foundation added

File: `database/2026-11-07-high-tech-entertainment-3.1.sql`

This additive migration introduces:
- `videos` — public published catalogue and founder-only management.
- `user_favourites` — authenticated owner-scoped saved tracks.
- `user_playlists` and `user_playlist_tracks` — owner-scoped playlists and membership.
- `listening_history` — owner-scoped listening records.
- `bookings` — authenticated submissions, owner reads and founder processing.

The migration creates new tables/indexes/policies only. It does not reset or rewrite existing data. **It has not been run against production.** Review and test it in a non-production environment first, especially policy behaviour and identity mapping.

## Remaining 3.1 implementation

| Feature | Status | Required completion evidence |
|---|---|---|
| Google sign-in | Code implemented | Live Supabase redirect/callback test |
| Password update | Not implemented | Authenticated password-change test |
| Catalogue artists/releases | Partial | Real data and pagination tests |
| Music playback | Core controls implemented | Physical device, audio focus, headset and process recreation |
| Startup audio | Bundled and CI packaging verified | Audible cold/warm launch test; no visible player UI |
| Videos | Schema foundation added | Native catalogue, Media3 playback and lifecycle tests |
| Favourites | Schema foundation added | UI, repository, user-isolation/RLS tests |
| Playlists | Schema foundation added | Create/edit/order/remove and RLS tests |
| Listening history | Schema foundation added | Playback event persistence and user isolation |
| Offline downloads | Not implemented | WorkManager, checksum, quota, resume and airplane-mode tests |
| Profiles/avatars | Existing SQL foundation only | Native profile UI, storage policies and owner-isolation tests |
| Bookings | Schema foundation added | UI, validation, submission and founder status workflow |
| Founder Studio | Not implemented | Founder-only screens, uploads and ordinary-user denial tests |
| Promotions | Not implemented in native app | Server-controlled scheduling, impressions and playback-order tests |
| Native updates | Not implemented | Signed manifest/hash/version verification and user-approved installer |
| Accessibility/adaptive UI | Partial | Compact phone, tablet, font scaling and TalkBack tests |
| Production release | Not approved | Signing fingerprint match, higher versionCode and install-over proof |

## Release gates

1. Never replace or uninstall the production app during migration.
2. Never reset, recreate or overwrite production Supabase data.
3. Keep service-role credentials and signing secrets out of source control.
4. Enforce founder permissions server-side with RLS and/or trusted server functions.
5. Do not claim a feature complete without backend and device evidence.
6. Flutter Shorebird OTA cannot patch native Kotlin code, manifests, permissions, plugins or bundled audio.
7. Do not publish an in-place update until certificate identity and version upgrade are proven.

**Current assessment:** 3.1 release scope and additive backend schema are established. Native feature implementation and acceptance testing are still in progress.
