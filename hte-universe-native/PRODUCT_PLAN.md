# HTE Universe — product build plan

This roadmap belongs to the clean Compose rebuild only. It is intentionally independent of the legacy app source.

## Product principles
- One coherent Android application with listener/member experiences and a role-protected Founder Studio.
- New Kotlin + Jetpack Compose implementation; no legacy screens, widgets, layouts, or application modules.
- Preserve the existing production application ID and signing identity for continuity; use a .dev suffix for debug builds.
- Reuse only explicitly approved service credentials and configuration, stored securely outside source control.
- Add new user-requested features through this backlog before implementation so scope and dependencies remain visible.

## Milestones
1. **Foundation and brand** — Compose theme, navigation shell, proper vector iconography, crown logo, 15-second cinematic intro, soundtrack and synchronized narration.
2. **Identity and backend** — Supabase authentication, secure session handling, profile, role-based authorization, storage access.
3. **Music experience** — catalogue, search, genres, artist and release pages, player, background playback, playlists, favourites and downloads.
4. **Video experience** — video catalogue, fullscreen playback, retry/offline states and media controls.
5. **Member experience** — account, downloads, booking requests, feedback and notifications.
6. **Founder Studio** — protected upload and catalogue management, artist/release/video administration, moderation and audit trail.
7. **AI and discovery** — optional voice companion and AI-assisted discovery, only after core product reliability.
8. **Release quality** — automated tests, accessibility, low-memory performance, network failure handling, signing verification and real-device acceptance.

## Startup creative brief
- 15-second branded introduction.
- Crown as the central brand mark; orbiting metallic stars.
- Rotating vector headphones and video-camera symbols.
- Spoken lines and captions timed together:
  - 0–3s: “Beyond the sound…”
  - 4–7s: “Beyond the rhythm…”
  - 8–11s: “Beyond boundaries…”
  - 12–15s: “This is our universe.”
- End card: “HIGH-TECH ENTERTAINMENT — THE PRIDE OF AFRICA.”
- Progress indicator must reflect real initialization where possible; audio failure must never block app entry.
- Final crown artwork and approved audio master are required before the intro is considered complete.

## Feature intake
Add every new user idea as a dated entry under Requested features. Do not silently discard requests; estimate impact and dependencies before implementation.

## Requested features
- Initial brief: rotating headphones and rotating video-camera motifs orbiting the crown during startup.
