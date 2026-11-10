# High-Tech Entertainment 4.0 — Controlled Rebuild Plan

## Decision

Use a controlled fresh start inside the existing official repository. Do not create a second production repository. Do not delete or overwrite production branches, repositories, release assets, signing credentials, Supabase resources, or website deployments during the audit and rebuild.

Working branch: `rebuild/4.0-unified-flutter-native`  
Base branch: `main`  
Base commit: `54b6b7c9da729a8dc78066d21bd7724cf67f4927`

## Verified baseline (10 November 2026)

### GitHub and releases
- Official repository: https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-
- Default branch: `main`; repository is public and accessible.
- Main has 86 branches and 81 pull requests across states; the branch and PR inventory requires careful classification before cleanup.
- Latest public Android release is `v3.2.0-SU`, published 8 November 2026: https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/tag/v3.2.0-SU
- Android 3.2.1 is currently an Actions candidate, not a published production release.
- PR #67 (`feature/3.2-premium-sales-and-hardening`) is open. Its signed-candidate, Flutter analysis/tests, and native build passed. The workflow verified package `com.hightechentertainment.app`, version 3.2.1 / code 33, FlutterHostActivity as launcher, bundled logo, and signer equality with the current production APK. The workflow uploaded an Actions artifact and skipped prerelease publication.
- PR #67 adds a separate `flutter-ui/` module while the existing production Flutter app remains in `mobile-app/`. Do not merge it until the two UI codebases and feature parity are reconciled.

### Application architecture
- Existing production Flutter source: `mobile-app/`; current pubspec version is 3.2.0+32.
- `mobile-app/README.md` still claims 2.2.10 build 27, so release documentation is stale and must be corrected.
- Existing native Android workstream: `native-android/`, Kotlin / Media3 / Supabase repositories and platform services.
- PR #67 introduces `flutter-ui/` and a FlutterHostActivity host. Its own README says full settings/privacy, account-deletion processing, live voice configuration, automated upload tests and physical-device acceptance remain incomplete.
- Establish exactly one canonical Flutter UI source. Preserve working production flows and reuse the tested native platform services; do not ship competing UI implementations.

### Supabase
- Production project status: ACTIVE_HEALTHY.
- 23 public tables were listed; all 23 reported RLS enabled.
- Six Edge Functions are active.
- Six Storage buckets exist, including `hte-audio`, `hte-artwork`, `hte-videos`, `hte-video`, `hte-avatars`, and the legacy private `ht storage` bucket.
- Current catalogue query: 5 artists, 4 tracks, 1 release and 2 videos are published. All five artist records, all four track records and the one release record lack artwork URLs. Both videos have thumbnails.
- Security advisor: leaked-password protection is disabled (warning). Performance advisor: 11 indexes are currently unused (informational); do not remove them without workload review.
- No database, auth, storage, function or policy changes were made during this audit.

### Cloudflare
- The connected Cloudflare integration returned error 6103: invalid format for X-Auth-Key. No Cloudflare resource was changed. Reconnect using the correct API-token Bearer flow or legacy email + Global API Key flow; never put an API token into the legacy X-Auth-Key field.

## Product direction

Build a premium, cinematic High-Tech Entertainment experience using the existing black / metallic gold / silver identity:
- Discovery home with strong hero artwork, editorial music cards, categories, releases and artist features.
- Rich artist profiles and release pages with reliable artwork fallbacks.
- Immersive now-playing and full-screen player, queue and playback controls.
- Video discovery and full-screen playback.
- Account, profile, bookings, downloads, playlists, favourites and complete settings/privacy.
- Founder-only Studio with server-authorized publishing and management.
- AI Voice Companion with explicit online/offline availability and graceful failure states.
- Consistent iconography, typography, spacing, motion, loading, empty and offline states.

## Implementation sequence

1. **Unify the source:** reconcile `mobile-app/` and PR #67's `flutter-ui/`; select one canonical Flutter UI source and retain one native Android host/platform layer.
2. **Preserve feature parity:** inventory existing screens and Supabase/native method channels before moving code. Keep auth, user sessions, music, videos, playback, downloads, bookings, Founder Studio, ads and account flows.
3. **Apply the design system:** build shared components and screen-level layouts; do not substitute static mockups for working screens.
4. **Repair content presentation:** define artwork requirements and fallback behavior; add verified artist, track and release artwork rather than fabricated metadata.
5. **Complete missing experiences:** settings, privacy, account deletion workflow, AI voice configuration, Founder Studio and accessible error/retry states.
6. **Validate:** Flutter analysis/tests, native unit/instrumentation tests, signed APK metadata and certificate checks, backend integration checks, and install-over compatibility.
7. **Physical-device acceptance:** test on the itel A50 / Android 14 Go, including startup, auth, navigation, playback, video, downloads, settings, bookings and Founder Studio.
8. **Release only after gates pass:** publish a new signed release only after the candidate is installed and accepted. A successful CI build alone is not release approval.

## Release acceptance gates

- Exactly one Flutter presentation source is used by the APK launcher.
- No regression in authentication, Supabase data access, playback, video, downloads, Founder Studio or sponsored audio.
- Every visible control has a working action; no decorative-only settings or buttons.
- Artwork, icons, empty states and offline/retry states are visually verified.
- Flutter and native tests pass.
- APK package remains `com.hightechentertainment.app`; version code increases; signer matches the installed production app.
- Install-over and physical-device smoke tests pass.
- Production `main`, website and Supabase remain untouched until review and release approval.

## Cleanup policy

Do not bulk-delete the 86 branches or close all open pull requests. First classify each branch by last commit, open PR association, unique commits, release dependency and whether its work is already merged. Archive or delete only confirmed abandoned duplicates after explicit approval.
