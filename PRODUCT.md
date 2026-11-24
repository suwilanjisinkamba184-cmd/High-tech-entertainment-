# Product brief — High-Tech Entertainment

## Product
High-Tech Entertainment (HTE) is an independent music-production and artist platform based in Zambia. It serves listeners, artists, producers, studio clients and collaborators.

## User outcomes
- Discover official HTE artists, releases, tracks and videos.
- Listen to published music and use supported player experiences.
- Learn about production, beats, recording, mixing, mastering and promotion services.
- Contact HTE and submit booking enquiries.
- Manage a member profile, favourites, playlists, listening activity and bookings where supported.
- Give authorized founders a secure workspace to manage catalogue content and platform operations.

## Product surfaces
- **Public website:** Astro application in `website/`, published through GitHub Pages. It is the public discovery, brand, services and contact surface.
- **Member account:** `account/` with shared browser logic in `assets/`. It provides authentication and member-specific features.
- **Founder Studio:** `admin/` with server/database-enforced authorization. Only authorized roles may perform privileged operations.
- **Android:** Kotlin + Jetpack Compose application in `hte-universe-native/`. The legacy Flutter tree in `mobile-app/` is retained for reference and is not the production Android implementation.

## Product constraints
- Supabase is the shared backend. Do not replace the production project or treat local mock data as production data.
- Publication and visibility rules are defined by the current database policies and application queries. Never assume a draft is public or private without checking both.
- Spotify URLs use the official Spotify embed/player flow. Never pass a Spotify page URL to a raw audio player.
- Direct media delivery must use HTTPS and respect the media's rights and access policy.
- Keep provider secrets, service-role credentials and signing material out of browser and mobile code.
- A feature is not “live” merely because UI or source code exists. Verify its backend, provider connection, deployment and user flow.

## Product quality
Prioritize reliable playback, understandable errors, safe authentication, responsive layouts, accessible controls, low-memory Android performance, and recoverable network failures. Keep the public brand editorial and the Founder Studio operationally clear.
