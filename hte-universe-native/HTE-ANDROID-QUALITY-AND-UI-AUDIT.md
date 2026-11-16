# HTE Universe — Android UI integration and quality audit

**Audit date:** 16 November 2026  
**Application:** HTE Universe / Vision 4.0.9  
**Current PR:** [#129 — complete UI integration, players, profiles and Settings](https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/pull/129)  
**Implementation branch:** `fix/vision-4.0.9-mini-player-google-avatar`  
**Verified Gradle identity:** `applicationId=com.hightechentertainment.app`, `versionName=4.0.9`, `versionCode=43`, `minSdk=25`, `targetSdk=37`, `compileSdk=37`.

## Implementation scope

This repair extends the original mini-player and Google-profile fixes into a coordinated integration of the existing native Compose application. It preserves the Media3 playback engine, Supabase repositories, current package identity and production signing configuration.

### Implemented in PR #129

- **Navigation and shell:** compact mini-player uses the actual Scaffold bottom inset; Home and the shared top bar expose Settings without adding a cramped sixth bottom-navigation destination.
- **Audio player:** expanded phone playback hides Android system bars; transient bars can be revealed by swipe; bars and behavior are restored when leaving immersive playback. Safe-drawing padding is removed only for the immersive player surface. Wide layouts retain their in-app player panel.
- **Video player:** retains Media3 and adds a branded central play/buffering control; existing seek, quality, fullscreen and retry controls remain.
- **Account:** Google OAuth photo metadata is used as a profile-avatar fallback and displayed in the account surface.
- **Artists:** Home artist cards are actionable; Discover searches artists as well as tracks; artist profiles show biography and associated tracks with playback, favourite and download actions. The app-side query no longer filters draft artist records; Supabase row-level security remains authoritative.
- **Settings:** shared Settings hub is accessible from Home and the shared top bar; Reduce Motion updates the theme immediately. The download-quality control is presented as unavailable until alternate downloadable masters exist, rather than storing a no-op selection.
- **Error handling:** normal Account, Founder Studio, Settings and Voice Companion screens show friendly actionable messages instead of raw service exceptions. Technical details remain for diagnostics.

## Existing application destinations

| Destination | Integration expectation |
|---|---|
| Home | Catalogue shelves, artist cards, search/voice/profile/settings actions |
| Discover | Track and artist search, genre filtering, refresh and empty/error states |
| Videos | Media3 playback, responsive fullscreen, seeking and retry |
| Library | Favourites, downloads and local playback |
| Account | Authentication, Google avatar, profile, bookings and Settings |
| Founder Studio | Founder-authorized uploads and catalogue management |
| Audio player | Compact and expanded modes, queue controls and background playback |
| Settings | Real preference consumers, live accessibility behavior and honest unavailable states |

## Verification status

- The existing unit-test suite passed on commit `08f5d27`; three additional regression tests now cover generic error redaction, nested network failures and permission failures. Those new tests await the final PR-head run.
- The latest application/workflow changes are committed on PR #129. The final PR-head run must pass unit tests, lint, debug APK assembly and package/version identity verification before merge.
- Pull-request verification no longer decodes production signing secrets or uploads signed production APKs. PR runs build an unsigned/debug APK and retain it for 3 days; production signing and 90-day signed-artifact retention are limited to explicit `native-v*` release tags.
- A Cloudflare Pages preview check is separate from the Android build and is not evidence of Android correctness.
- **Physical-device acceptance has not been completed.** The itel A50 / Android 14 Go still needs testing for gesture and three-button navigation, system-bar reveal/restore, mini-player docking, Google avatar persistence, video orientation, audio background playback, settings behavior, offline recovery, memory and battery.

## Release and compatibility safeguards

- Keep the visible application version at `4.0.9`.
- Current internal `versionCode` remains `43`; do not claim an in-place update over build 43 is possible without a higher code. Before publishing an update for existing installations, obtain approval to use code 44 while retaining visible versionName `4.0.9`.
- Preserve `com.hightechentertainment.app`, the production signing certificate, Supabase backend and existing user data.
- No public release, tag, or historical APK has been deleted or repointed. Cleanup of public releases `4.0.8` and below still requires the owner's explicit choice.
- Existing historical Actions artifacts remain untouched. The new retention policy applies to future runs only; deletion of existing artifacts or public releases still requires approval.

## Acceptance gates before publication

1. Final PR-head Android unit tests and lint pass.
2. Signed APK assembles and passes package/version/signer continuity checks.
3. No developer stack traces or raw provider messages are shown in ordinary UI error states.
4. All app destinations and interactive controls are exercised, including artist profiles and Founder Studio authorization.
5. Physical-device acceptance passes on the target low-memory Android phone.
6. Release versionCode and historical-release cleanup decisions are confirmed before publishing or deleting anything.

A successful CI build is necessary but does not replace physical-device validation.
