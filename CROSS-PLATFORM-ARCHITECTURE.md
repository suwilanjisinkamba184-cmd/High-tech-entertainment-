# HTE Universe — cross-platform architecture

## Product decision

Build one maintained Flutter application for Android phones/tablets, iPhone/iPad, Windows PCs and the web. Flutter owns shared presentation, navigation, accessibility, application state and feature workflows. Platform-specific services are isolated behind typed interfaces; platform differences must not leak into feature screens.

The current Kotlin/Jetpack Compose app remains the Android production baseline until the Flutter application reaches feature parity and passes the same acceptance gates. Do not delete or rewrite the native app during the migration.

## Non-negotiable continuity

- Android application ID remains `com.hightechentertainment.app`.
- Preserve the existing Android signing certificate and version-code upgrade path. The installed baseline is 4.3.1 (55); the next Android update must use a higher version code.
- Keep the existing Supabase project, schema, Storage buckets, Edge Functions, authentication users and user data. No backend replacement or destructive schema reset.
- Supabase remains the source of truth. Public catalogue queries expose only published content; Founder permissions are enforced by RLS and server-side authorization, never by UI visibility alone.
- Never ship service-role keys, signing credentials or private API secrets in Flutter source, build arguments visible to clients, logs or web bundles. Use the Supabase publishable key only.
- Preserve the approved High-Tech Entertainment logo and obsidian / metallic-gold / silver visual identity.

## Application layers

1. **Presentation** — responsive Flutter screens, navigation, design tokens, forms, loading/error/empty states and accessible interaction.
2. **Feature state** — explicit state machines for catalogue, authentication, account, player, downloads, AI companion and Founder Studio.
3. **Domain** — typed entities and use cases independent of Flutter widgets and platform APIs.
4. **Data** — Supabase repositories, DTO mapping, cache policy, retry policy and safe error translation.
5. **Platform adapters** — media playback, secure storage, file access, notifications, OAuth callbacks, microphone permissions, downloads and platform lifecycle.

Feature screens depend on interfaces, not Android/iOS/Windows implementation classes.

## Media and provider rules

- Android: retain the known Media3 playback capability behind the shared playback interface during migration.
- iOS/iPadOS: use AVFoundation-backed playback and native audio-session/background modes.
- Windows: use a maintained Flutter media plugin or a small native adapter backed by Windows media APIs; prove codec and packaging support before choosing it.
- Web: use browser media capabilities and official provider embeds.
- Spotify URLs must use Spotify's official embed/app experience. Never treat a Spotify page as a direct audio file or extract its stream.
- YouTube videos must use the official YouTube player/embed path. If embedding fails, show a useful provider error and an explicit external-open action.
- Direct audio/video media URLs must be HTTPS and validated before playback.
- Offline downloads are a platform capability, not a fake UI toggle. Store files in app-private locations, verify integrity, track status, and explain platform restrictions. Do not promise web offline downloads until browser support is verified.

## Responsive behavior

- Phones: touch-first navigation, compact player, one-column content, safe-area handling.
- Tablets/foldables: adaptive navigation rail, wider content columns, persistent player where space allows, orientation-safe layouts.
- Windows: keyboard and pointer support, resizable window layouts, navigation rail/sidebar, hover and focus states, media-key integration where supported.
- Web: responsive layouts, keyboard navigation, browser back/deep links and no dependence on native-only APIs.

## Feature parity gates

| Feature | Required evidence before parity sign-off |
|---|---|
| Authentication | Email/password, Google OAuth where configured, callback handling, session restore, sign-out, expiry and network failure |
| Catalogue | Published-only tracks, artists, releases and videos; pagination/refresh; artwork fallback; empty/error/retry states |
| Music | Play/pause, seek, queue, next/previous, repeat/shuffle, interruption, lock-screen/system controls and background playback |
| Video | Real poster loading, provider playback, fullscreen, orientation, retry and external fallback |
| Downloads | Authorized download, progress, cancellation, integrity, duplicate handling, offline playback and deletion |
| Member account | Profile, avatar, favourites, playlists, history, booking requests and notifications |
| Founder Studio | Founder authorization, uploads, metadata edits, publishing, moderation and auditability |
| AI voice companion | Permission denial/retry, token lifecycle, connection startup, multi-turn conversation, disconnect/reconnect and safe error messages |
| Stability | Offline startup, slow network, timeouts, process recreation, low-memory behavior, crash diagnostics and accessibility |

A feature is not complete because its screen exists. Each row needs automated tests and manual acceptance on its target platforms.

## Migration sequence

1. Stabilize the current Android recovery PR; fix all review findings and preserve the no-public-release lock.
2. Establish Flutter app shell, design tokens, navigation, dependency boundaries and cross-platform CI.
3. Port Supabase configuration, typed repositories, auth and published catalogue with parity tests.
4. Port music playback and player UI through platform adapters; validate Android first, then iOS and Windows.
5. Port videos, downloads, member account and Founder Studio.
6. Integrate AI voice companion only after core media and account flows are stable.
7. Add tablet, Windows and web-specific interaction refinements and accessibility/performance tests.
8. Run release-candidate checks, compare Android signing identity, test on the itel A50 and supported platform devices, then obtain explicit owner approval before distribution.

## Distribution lock

Until all acceptance gates pass, keep public Android releases, installable candidate artifacts and public download links disabled. CI may run analysis, tests and non-installable build validation; it must not publish APKs or signed candidate files.
