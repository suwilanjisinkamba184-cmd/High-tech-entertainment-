# HTE Universe — Android quality, stability and UI audit

**Audit date:** 16 November 2026  
**Repository baseline inspected:** `main` / native Android application  
**Verified baseline in Gradle:** versionName `4.0.7`, versionCode `41`, applicationId `com.hightechentertainment.app`, minSdk `25`, targetSdk `37`, compileSdk `37`.  
**Important version note:** the repository currently identifies the app as 4.0.7. This audit does not silently relabel it as 4.4.7 or change the version, package, signing identity, Supabase configuration, or stored user data.

## Executive assessment

The project already has a substantial native Android foundation: Jetpack Compose + Material 3, a Media3 `MediaSessionService`, an app-scoped `MediaController`, a responsive bottom-navigation/navigation-rail shell, a compact/full audio player, a separate video player, Supabase repositories, and offline-download handling.

The next release should focus on measurable reliability and finishing the existing architecture rather than replacing working systems wholesale. Priority order:

1. Correct shared playback lifecycle and prove screen-off playback.
2. Finish responsive layout, system-bar insets, player and video behavior.
3. Measure startup, scrolling, memory and battery on low-memory physical devices.
4. Harden network/offline/download and account states.
5. Run automated and manual release gates before publishing.

## Findings from the repository

### P0 — Shared Media3 controller lease accounting

The controller used an identity map to reference-count consumers, but the inspected implementation did not increment the count in `connect()`. That meant the first release could treat a shared future as the last lease and release the MediaController while another screen still held it.

**Change included in this repair branch:** increment the lease count for every caller while holding the same synchronization lock used to create/reuse the shared future. The controller is released only when the final caller releases its lease.

**Required tests:** two consumers connect to the same future; releasing consumer A must not disconnect consumer B; releasing B must release the future once; failed connections must clear the shared future and permit a later retry.

### P0 — Screen-off audio and audio focus

The app already uses Media3 `MediaSessionService`, `AudioAttributes.DEFAULT` with Media3 audio-focus handling enabled, and a local wake mode. These are directionally correct for music playback. The service owns the player outside the Activity, which is the right lifecycle boundary for background audio.

**Required validation:** lock screen, switch apps, incoming call, alarm/assistant interruption, another app requesting focus, audio ducking, Bluetooth connect/disconnect, wired headset unplug, notification controls, hardware media keys, seek/next/previous, and playback resumption. Confirm that the foreground media service—not an Activity—owns the active player and that playback stops/releases cleanly when the session is abandoned.

**Architecture decision:** because HTE serves a catalogue, evaluate Media3 `MediaLibraryService` (rather than assuming `MediaSessionService` is the final architecture). Migrate only when the catalogue can be exposed as a proper browsable hierarchy and the controller/client behavior is tested. Do not perform a risky service migration as an unmeasured cosmetic change.

**Battery guardrail:** wake mode is useful for uninterrupted foreground music, but it must not become an always-on background wake lock. Confirm Media3/service lifecycle releases resources when playback is stopped and monitor excessive partial wake locks.

### P1 — Splash and safe-area layout

The splash previously used a large fixed orbit graphic and large fixed vertical gaps without applying safe-drawing insets to its content. On compact displays this can crowd the available height or place important content close to system bars.

**Change included in this repair branch:** reduce the fixed artwork/spacer dimensions and apply `WindowInsets.safeDrawing` to the splash content while retaining the 15-second intro timing and HTE branding.

**Required validation:** small-height phones, display cutouts, gesture navigation, three-button navigation, font/display scaling, and landscape. Confirm the logo, caption, progress indicator and startup audio remain synchronized and visible.

### P1 — Main shell and adaptive navigation

The current shell already uses a Material 3 `Scaffold`, a bottom navigation bar on phone-sized windows, a navigation rail at widths of 600dp and above, and a dedicated player region on wide layouts. Keep this adaptive structure; avoid duplicating separate phone/tablet implementations.

**Required UI pass:**
- Verify the top bar, bottom navigation, mini-player and full player never overlap one another or Android system bars.
- Check short screens, landscape, 200% font scale, display cutouts, and keyboard-open account/search forms.
- Ensure all interactive targets remain comfortably tappable (aim for at least 48dp), and provide labels/state descriptions for icon-only controls.
- Keep HTE black/obsidian, metallic gold and silver tokens consistent across Home, Discover, Videos, Library, Account, settings, dialogs and player.
- Use consistent spacing, typography, corner radii, loading skeletons, empty states and friendly retry states. Do not expose raw API/stack-trace messages to users.
- Keep reduced-motion preferences respected by animation-heavy surfaces.
- Audit TalkBack labels, focus order, text scaling and contrast; keep custom touch targets at least 48dp (Material controls provide this by default, but custom hit areas still need inspection).

### P1 — Audio player interaction

The existing player includes mini/full modes, waveform seeking, shuffle/repeat, previous/next, buffering indication, playback errors and retry. The compact player now displays a buffering spinner instead of implying that playback is immediately ready.

**Required validation:** rapid play/pause taps, track changes while buffering, missing/expired media URLs, seek before duration is known, stream error/retry, queue boundaries, repeat-one/repeat-all, app recreation and process death. UI state must reflect Media3's actual player state, not optimistic button state.

### P1 — Video player

The app uses Media3 PlayerView inside Compose AndroidView with a separate video player dialog, custom controls and fullscreen toggling.

**Required validation:** rotate while playing, enter/exit fullscreen repeatedly, press Back, dismiss dialog during buffering, background/foreground the app, screen lock, stream failure/retry, seek on unknown duration, and low-memory pressure. Ensure the player and PlayerView are detached/released exactly once and orientation/system bars are restored on every exit path. Avoid creating multiple ExoPlayer instances for the same active video.

### P2 — Compose performance and startup

Do not optimize by guesswork. First benchmark cold start, Home first draw, catalogue scrolling with artwork, Discover search, opening the player, and switching tabs. Use Macrobenchmark/FrameTiming and Android Studio system traces where available.

Then:
- Defer nonessential network/catalogue work until after first draw.
- Use stable item keys and content types in heterogeneous lazy lists.
- Cache expensive transformations; avoid allocations and sorting in frequently recomposed composables.
- Collect rapidly changing state narrowly; avoid making the entire shell recompose on player progress ticks.
- Generate an app-specific Baseline Profile for cold start, tab navigation, catalogue scrolling and opening the player.
- Record TTID and TTFD/reportFullyDrawn; trace Compose recomposition/layout/draw on real user journeys.
- Test optimized release builds with R8/resource shrinking; debug performance is not a release proxy.
- Compare before/after metrics on the same physical device and build type.

### P2 — Network, downloads and offline UX

The shell already catches catalogue failures and shows a friendly retry message, uses IO dispatch for download work, and exposes network/data-saver settings. The inspected download implementation uses Android's system `DownloadManager`, stores metadata in app preferences, and writes into app-specific external files. That is a reasonable fit for straightforward HTTP file downloads and provides system-managed background transfer; it is not the same as Media3 adaptive-stream offline download support.

**Required improvements and tests:**
- Distinguish offline, timeout, authorization/session expiry, unavailable media, and server errors without displaying internal exception text.
- Add retry with bounded backoff for transient requests; do not retry permanent authorization or invalid-URL failures endlessly.
- Verify downloads survive process death, report progress/failure, handle duplicate enqueue, clean up partial files, and respect metered/data-saver settings.
- Confirm offline playback uses local content URIs and does not accidentally fall back to the remote URL.
- Test network loss during streaming/download and recovery after reconnect.

**Download follow-up:** test reboot/process-death recovery, paused/failed/missing download records, storage-full conditions, duplicate downloads, and whether completed files are playable after app updates. For HLS/DASH offline media, evaluate Media3 `DownloadService` + `DownloadManager` + `DownloadIndex` rather than treating a single-file DownloadManager transfer as an adaptive-stream download. Android's Media3 documentation explicitly recommends its DownloadService architecture for downloads that should continue when the app is backgrounded.

### P2 — Security and privacy

The manifest currently declares backup disabled, a non-exported FileProvider, internet/network permissions, foreground media playback, wake lock, microphone and package-install permissions. Production signature verification is present in the Activity.

**Required review:**
- Keep exported components to the minimum and validate all deep-link inputs.
- Keep Supabase service-role secrets out of the Android client; use only the intended publishable key and enforce authorization with database/storage policies.
- Confirm TLS-only endpoints and review cleartext traffic policy.
- Reconfirm the production signing certificate fingerprint before any certificate pinning or signature allow-list update; never guess a fingerprint.
- Review whether `REQUEST_INSTALL_PACKAGES` is strictly necessary for the app's update flow and remove it if not.
- Request microphone permission only when the user activates a real voice feature; handle denial and unavailable speech services honestly.
- Recheck data backup/export behavior and the privacy policy against actual stored data.

## Release acceptance gates

A release is not ready until all applicable gates are green:

1. **Build:** Gradle release build, unit tests, Android lint, dependency checks.
2. **Identity:** package ID, version code/name, production signer SHA-256 and certificate continuity verified.
3. **Audio:** screen-off playback, audio focus/interruption, notification, Bluetooth/wired headset and queue controls pass.
4. **Video:** fullscreen/orientation/lifecycle, seek, buffering and retry pass.
5. **UI:** no clipped/overlapping controls on compact and wide layouts; system bars/cutouts/keyboard handled; accessibility labels and touch targets checked.
6. **Network/offline:** offline, flaky network, expired URL, download restart and local playback pass.
7. **Performance:** startup feedback, no UI-thread network/disk work, smooth catalogue scrolling, memory and wake-lock checks on a lower-memory physical device.
8. **Publication:** APK checksum and public release assets independently verified after upload.

A successful CI build does not replace physical-device validation. If the physical device cannot be tested during this work, report that limitation explicitly rather than marking the device gate passed.

## Research references

- Android core app quality: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Media3 background playback: https://developer.android.com/media/media3/session/background-playback
- Media3 audio focus: https://developer.android.com/media/optimize/audio-focus
- Jetpack Compose performance: https://developer.android.com/develop/ui/compose/performance
- Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose window insets: https://developer.android.com/develop/ui/compose/system/insets
- Android 15 behavior changes / edge-to-edge: https://developer.android.com/about/versions/15/behavior-changes-15
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- App startup analysis: https://developer.android.com/topic/performance/appstartup/analysis-optimization
- Android security best practices: https://developer.android.com/privacy-and-security/security-best-practices
- Android vitals: https://developer.android.com/topic/performance/vitals
- Android accessibility and 48dp touch targets: https://developer.android.com/guide/topics/ui/accessibility/apps
- Media3 offline downloads: https://developer.android.com/media/media3/exoplayer/downloading-media
- Android memory management: https://developer.android.com/topic/performance/memory-overview
- Android app startup and TTID/TTFD: https://developer.android.com/topic/performance/issues/launch-time
