# HTE Universe — Deep Application Audit
**Audit date:** 17 November 2026  
**Audited baseline:** `main` at `d0e6a457056ef128b977b2a30ac22d9052def3e2` (production build 48, version 4.1.2). The fixes are prepared for build code 49 while retaining marketing version 4.1.2.

## Executive summary

The native Android app is a Kotlin/Jetpack Compose application using Media3 for playback and Supabase Kotlin for authentication and data. Build 48's production workflow completed successfully: unit tests, debug/release lint, production signing, package/version/signer verification, certificate continuity, publication and public asset verification all passed.

A successful build does not establish real-device correctness. No physical-device test was performed during this audit. The current workflow runs JVM unit tests and lint; it does not run an Android emulator/device instrumentation suite.

## Additional UI and launch-screen corrections

A focused review after the initial audit found the reported duplicate-logo issue in the Compose splash itself: the H/T/E monogram was drawn over the official HT logo during its fade-in and retained 15% opacity after the transition. This is now removed. The official logo is the only central mark and rotates smoothly inside the gold orbital ring.

The Activity was also using Android 12+ system splash attributes on its post-launch theme without installing the AndroidX SplashScreen compatibility API. The app now has a dedicated starting theme, installs the compatibility API before `super.onCreate()`, and transitions into the Compose intro using the same official logo. This follows Android's migration guidance and is intended to prevent a stacked launch experience on Android 12+ while supporting older Android releases.

The main Home header now uses the actual official HTE logo in a restrained black/gold gradient surface instead of relying only on a generic text mark. This is a focused UI polish, not a claim that every screen has completed its full visual redesign. Startup playback rate is now calculated from the encoded audio duration to target the 15-second Compose intro; if a device rejects playback-rate control, normal-speed playback remains the fallback.

## Source fixes included in this audit branch

1. **Catalogue refresh concurrency:** startup, activity resume and periodic refresh triggers could overlap while mutating one shared loading state. The app now keeps one catalogue refresh active at a time.
2. **Video lifecycle:** the in-dialog ExoPlayer could continue playing after the Activity entered the background. The video player now pauses on `ON_STOP`; it remains separately owned from the background music service and is released when its dialog is disposed.
3. **Splash audio compatibility:** the optional 0.937x playback-rate adjustment was inside the same failure boundary as player creation. A device/codec rejecting that adjustment could suppress the entire splash sound. The adjustment is now best-effort, leaving normal-speed playback available.
4. **Voice-session account binding:** the app now verifies that the authenticated Supabase user ID matches the user ID returned with the short-lived ElevenLabs conversation token before starting the session.

## Verified architecture and controls

- Package: `com.hightechentertainment.app`; audited baseline version 4.1.2, build code 48. The audit patch increments only the Android build code to 49; the public version name remains 4.1.2.
- Production APK signer continuity and runtime certificate pin are checked in CI.
- Supabase client uses PKCE and an encrypted session manager; only the publishable key is embedded in the client.
- Music uses a dedicated Media3 `MediaSessionService`, with the required foreground-service and media-playback permissions declared.
- Video uses Media3 ExoPlayer and a PlayerView in the native Compose dialog, including seek, quality selection, fullscreen, retry and release handling.
- Public catalogue queries are subject to Supabase RLS. All inspected public/private tables reported RLS enabled.
- The ElevenLabs token Edge Function is active with JWT verification enabled and validates the Supabase access token server-side.
- Storage buckets include private `ht storage` and public media/artwork buckets. Public buckets are intentional for public media delivery; private content must not be moved into them.
- Live catalogue counts at audit time: 5 artists (5 published), 4 tracks (4 published), 1 release (1 published), 2 videos (2 published). There are currently no draft artist rows in the database.

## Backend audit findings requiring follow-up

### High priority — Auth configuration
Supabase Security Advisor reports **Leaked Password Protection Disabled**. Enable this in Supabase Authentication password security settings. The connected Supabase management tools available for this audit did not expose an auth-configuration update operation, so this setting was not changed automatically.

### Medium priority — Security-definer function search path
Most inspected security-definer functions in the private schema explicitly pin `search_path` to an empty string and schema-qualify their table references. `public.hte_contact_rate_limit()` is the exception: it pins `search_path` to `public`. Supabase recommends `search_path = ''` and fully qualified references for security-definer functions. Harden this function in a separately tracked database migration after migration-source reconciliation.

### Medium priority — Policy overlap
Supabase Performance Advisor reports multiple permissive SELECT policies on `public.hte_download_branding_settings` for authenticated users. This is a policy-overlap/performance warning, not proof of unauthorized access. A semantics-preserving consolidation should retain: public reads of active settings, owner/admin reads of all settings, and owner/admin insert/update/delete. Do not remove the admin policy or broaden public visibility.

### Low priority — Unused indexes
The advisor reports 16 indexes with no observed usage. Do not drop these based only on the current counters: the database is relatively new and some indexes may support infrequent admin, reporting, or foreign-key workloads. Re-evaluate after representative production traffic and query-plan review.

### Migration-source reconciliation
The live project has a much longer migration history than the canonical `supabase/migrations/` directory currently contains (only two files are present there). Additional historical SQL is distributed across `database/changes/` and root-level SQL files. This makes the intended replay order and a clean-room rebuild harder to verify. Reconcile and document the complete migration history before applying further schema changes.

### Draft artist visibility
The Android repository intentionally requests artist rows without an `is_published` filter, but public visibility is governed by RLS. The current artist SELECT policy exposes published rows publicly and allows owner/admin roles to read drafts. The live database currently has zero draft artist rows. Do not weaken RLS just to make draft content public; if draft profiles must be public, define an explicit public-preview/visibility model and test it.

## Future work recommended

1. Add emulator-based instrumentation/smoke tests for launch, navigation, background music, audio focus, video pause/resume, fullscreen rotation, download completion and offline playback.
2. Add an Android 8/API 26 and current Android API device matrix, with a low-memory Go-device profile.
3. Add integration tests for PKCE callback handling, expired sessions, Google Credential Manager, owner-only Founder Studio, and RLS-backed catalogue reads.
4. Test real media formats used by the catalogue (MP3/AAC, MP4/H.264, HLS/DASH) against actual CDN headers, redirects, MIME types and signed URLs.
5. Add performance baselines for cold start, Compose recomposition, scrolling, memory and battery; use measurement before tuning.
6. Reconcile historical Supabase migrations and then fix the policy/search-path findings in a reviewed migration.
7. Enable leaked-password protection and periodically review Auth, Storage and Database advisors.

## Research references

- Android core app quality: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Android 12+ splash-screen migration: https://developer.android.com/develop/ui/views/launch/splash-screen/migrate
- AndroidX SplashScreen API: https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen
- AndroidX Core release notes (SplashScreen 1.2.0): https://developer.android.com/jetpack/androidx/releases/core
- Media3 background playback: https://developer.android.com/media/media3/session/background-playback
- Media3 ExoPlayer lifecycle: https://developer.android.com/media/media3/exoplayer/hello-world
- Compose performance guidance: https://developer.android.com/develop/ui/compose/performance
- Compose best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Supabase function search-path advisor: https://supabase.com/docs/guides/observability/advisors?queryGroups=lint&lint=0011_function_search_path_mutable
- Supabase PKCE flow: https://supabase.com/docs/guides/auth/sessions/pkce-flow
- Supabase Auth security: https://supabase.com/docs/guides/auth/password-security

## Verification status

- Build 48 production workflow: **passed**.
- Current audit branch: **source changes committed; awaiting PR CI; intended production artifact is 4.1.2 build 49**.
- Device-side playback, audio focus, notifications, Google sign-in, video rotation and offline tests: **not verified on physical hardware during this audit**.
- Supabase leaked-password protection: **action required in dashboard**.
- Database policy consolidation and search-path hardening: **not applied; pending migration-source reconciliation**.
