# HTE Universe — Deep Static Audit

**Audit date:** 2026-11-23  
**Repository:** `suwilanjisinkamba184-cmd/High-tech-entertainment-`  
**Native Android baseline:** `main` at `ef66e2188f08eeeae671796e4c1ec4f986620331`  
**Recovery candidate:** PR #173 at `f195c0aded301c74d7f3dc4d0a4497a1c8836918`  
**Cross-platform foundation:** PR #174 at `f8ac13ea999a012d79237b14bf532fae11d998aa`

## Executive result

- **Native Android:** Conditional pass for the existing Kotlin/Compose architecture. The recovery PR contains valuable fixes, but three issues in its UI path required correction before it should be considered merge-ready.
- **Flutter:** Foundation only; not a replacement for the Android application and not a production release candidate.
- **Backend/security:** Static review of the critical authentication, Founder authorization, RLS migration, voice-token and temporary-download paths found strong defence-in-depth patterns. This does not establish that the same migrations/policies are currently deployed to the live Supabase project.
- **Release:** No merge, signed build, public release, signing change or production backend mutation is part of this audit.

This is a repository-backed static audit plus CI evidence review. It is not a complete dynamic penetration test, an exhaustive audit of every website/Android/Flutter source file, or physical-device certification.

## Confirmed recovery-branch findings and repairs

| ID | Severity | Evidence | Impact | Repair |
|---|---|---|---|---|
| F-01 | P1 | `hte-universe-native/app/src/main/java/com/hightechentertainment/app/HteSplashScreen.kt` — PR #173 changed the splash artwork from `R.drawable.ht_logo` at 142dp with fit scaling to the 96dp launcher icon. | Weakens the branded opening and makes the studio logo appear smaller than the approved artwork. | Restored `ht_logo`, `ContentScale.Fit`, 142dp. The 15-second timeline and audio synchronisation are unchanged. |
| F-02 | P1 | `hte-universe-native/app/src/main/java/com/hightechentertainment/app/UniverseShell.kt` — `VideoPlayerDialog` returned a full-screen loading `Box` before creating its `Dialog`. | The local-download lookup temporarily replaces the Activity's main app surface with a spinner. | The loading state is now presented in a full-window Compose `Dialog`; dismissal still uses the existing callback and the lookup coroutine remains lifecycle-cancelled with the composable. |
| F-03 | P2 | `hte-universe-native/app/src/main/java/com/hightechentertainment/app/Universe2026Home.kt` — recommendations preference was read directly from `SharedPreferences` in the composable body. | Repeated synchronous preference reads during recomposition, avoidable work on lower-memory/low-power devices. | The value is now memoized with `remember(context, recommendationsRevision)`. The existing parent settings revision is forwarded so changes made in Account/Settings refresh the value rather than leaving a stale preference. |

## Other recovery changes reviewed

PR #173 adds or improves YouTube thumbnail fallback candidates, downloaded-video local URI resolution, Media3 noisy-audio-route handling, and ElevenLabs disconnect/error-state recovery. The `java.net.URI` classifier also keeps the pure URL helper independent of Android framework `Uri` during JVM tests. These are useful changes and should be retained, subject to the updated CI and device checks.

The 15-second branded startup is intentional: the animation and startup-audio logic use a 15-second foreground timeline. It must not be shortened as a purported fix. The startup sequence pauses its foreground timer with lifecycle state and resumes it when the app returns.

## Backend and security review

The inspected native trust boundaries show:

- Founder write operations call the server-side `hte_my_role` RPC and require the `owner` role before content management operations. The supplied migration set also changes content/storage write policies to `private.hte_has_role(['owner'])`; database RLS remains the authoritative boundary.
- Member profile, booking, favourites, playlist and history operations use the signed-in user's JWT and user-specific filters, with RLS expected to enforce the final row boundary.
- Supabase session persistence uses AES-GCM with a key held by Android Keystore; the implementation includes a legacy-session migration path.
- The ElevenLabs token Edge Function requires a bearer session, verifies the user through Supabase Auth, and returns a short-lived provider conversation token. Supabase config has JWT verification enabled for this function.
- Temporary media delivery only stages published HTE direct media with owned/licensed rights, validates the HTE storage origin, requires the signed Cloudflare gateway proof, uses one-time nonces and an atomic per-IP hourly quota, stores staged objects in a private bucket, and returns a short-lived signed URL. Cleanup is constrained to a trusted GitHub OIDC workflow.
- SQL regression scripts check client table privilege boundaries, Founder RLS policy shapes, video-table RLS, service-role requirements, download quota and nonce replay behaviour.

**Runtime verification still required:** confirm the migrations and policies are deployed in the live Supabase project; execute the SQL security tests against a staging database; validate storage bucket policies and Edge Function secrets/rotation; and test authenticated and unauthenticated access with real JWTs. Repository migrations alone do not prove live database state.

No direct permanent-storage download fallback should be added to bypass the temporary-download gateway. The current native audio download action uses a browser hand-off to the protected service; integrating offline audio into the in-app library would be a separate product feature and must consume the same signed, rights-checked delivery path.

## Flutter PR #174 — scope and release gate

The PR provides a responsive Flutter shell, theme, Supabase bootstrap/authentication, basic published-catalogue DTO/query code and URL-policy utilities. The `material_ui` package is an external dependency; its being third-party is not, by itself, proof of a vulnerability or a reason to replace it wholesale.

The PR's media ports are contracts rather than working adapters. It does not yet implement feature-parity audio/background playback, video/fullscreen playback, offline download queues, the full Founder Studio publishing workflow or the ElevenLabs AI voice companion. It must remain a foundation branch until real per-platform adapters and user flows are implemented and verified. Do not replace the Kotlin production app with this scaffold yet.

## CI evidence and its limits

Before these three repairs, PR #173's API 34/API 35 Android validation, CodeQL and GitGuardian checks were green. PR #174's Dart analysis, widget tests and web build were green. The main branch also had green native test/lint checks. These results establish successful automated checks for those commits only; they do not test this new commit until its checks finish, and do not establish physical audio/video, signing or Android Go behaviour.

The Android workflow keeps public publishing and signed-candidate creation disabled. Preserve that lock-down.

## Required validation before merge/release

1. Run the Android unit tests, lint and API 34/API 35 instrumentation checks on the updated PR head.
2. On the actual itel A50 / Android 14 Go device, cold-launch repeatedly and verify the original logo, full 15-second foreground sequence and audio sync; repeat after background/resume and with startup sound disabled.
3. Verify that opening a locally downloaded video shows a modal loading state without replacing the app behind it; then verify local playback in airplane mode, external YouTube playback, thumbnail fallback, fullscreen, landscape rotation, back navigation and close.
4. Change the personalised-recommendations option in Settings, return Home and verify the selection refreshes; verify no repeated synchronous preference reads are introduced by recomposition.
5. Exercise background audio, focus loss, unplugged headphones/Bluetooth, incoming interruptions, app process recreation, poor cellular handoff and playback recovery.
6. Verify Google/email sign-in, expired sessions, signed-out AI voice UX, microphone permission denial, provider token failure, WebSocket disconnect/retry, and owner vs member Founder Studio access.
7. Execute the Supabase RLS and quota SQL tests against staging and verify deployed Edge Function configuration without exposing service-role or provider credentials.
8. Verify APK package/version/signer identity against the existing installed baseline before any future distribution. Do not enable signed candidate or public release publishing as part of this PR.

## Decision

Keep PR #173 open as a draft until its updated CI passes and the required physical-device checks are recorded. Keep PR #174 separate and non-production until its feature parity gates are met. No changes in this audit authorize a merge, release, app identity/signing change, or production Supabase mutation.
