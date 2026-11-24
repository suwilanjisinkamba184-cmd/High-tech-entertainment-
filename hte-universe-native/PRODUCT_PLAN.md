# HTE Universe — product roadmap

This roadmap describes product direction for the current Kotlin + Jetpack Compose Android application in this directory. It is not evidence that every listed capability is complete or released. Use source code, tests and release records to establish implementation status.

## Product principles
- One coherent Android application for listeners and members, with Founder Studio operations protected by trusted server-side authorization.
- Preserve the established application ID, production signing identity, Supabase project and existing user data.
- Use native Android patterns, accessible controls, predictable playback and clear offline/error recovery.
- Reuse approved HTE brand assets; do not invent official artist/release artwork.
- Keep legacy Flutter code in `mobile-app/` separate from the current native application.

## Workstreams
1. **Launch and identity:** branded launcher/splash, approved startup audio, session restoration and recoverable initialization.
2. **Authentication:** email/password and configured OAuth flows, secure session refresh, profile and role-aware access.
3. **Music:** published catalogue, search, genres, artist/release views, Media3 playback, queue, playlists, favourites and history.
4. **Video:** catalogue, official YouTube playback, HTE-hosted media playback, fullscreen, retry and lifecycle handling.
5. **Member tools:** account, downloads, bookings, feedback and notifications.
6. **Founder Studio:** secure catalogue upload/edit/publish operations, artist/release/video management and moderation.
7. **Voice and AI:** optional authenticated Voice Companion and AI-assisted tools; provider credentials remain server-side.
8. **Release quality:** unit/instrumentation tests, accessibility, low-memory performance, network recovery, signer continuity and real-device acceptance.

## Acceptance requirements
For each workstream, record the relevant source files, automated tests, backend policies and manual test evidence. A UI control is not complete if its action is a no-op, an error is silently swallowed, or authorization exists only in the UI.

## Release safety
Do not publish while the Android workflow's recovery gate disables release publication. A release requires an explicit approved workflow configuration, successful CI, a signed APK, matching package/version/signer verification, SHA-256 manifest, install-over test and physical-device acceptance.
