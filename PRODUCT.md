# Product brief — High-Tech Entertainment

## Product and audience
High-Tech Entertainment (HTE) is an independent music-production and artist platform based in Zambia. Its audience includes music listeners, artists, producers, prospective studio clients, collaborators, and people discovering HTE releases.

## Primary user goals
- Discover and listen to officially released music.
- Explore artists, releases, and official videos.
- Learn about music production, beats, recording, mixing, mastering, and promotion services.
- Contact HTE or submit a booking enquiry.
- In HTE Universe across Android phones/tablets, iPhone/iPad, Windows PCs and web, browse the published catalogue, listen to released tracks, watch official videos, manage a member library and access role-protected Founder Studio tools.

## Brand personality
Confident, independent, musically literate, ambitious, polished, and welcoming. The brand should feel like a credible independent label and production house—not a generic software dashboard, template storefront, or childish music player.

## Surface modes
- Public website: Persuade and Experience. It should read as an editorial music-label website with a strong visual identity, meaningful whitespace, a clear story, and obvious discovery and contact paths.
- Android app: Operate and Experience. It should be a focused listening product with familiar native navigation, reliable playback, clear track information, and consistent HTE branding.
- Founder Studio/admin: Operate. Prioritize clarity, control, security, and efficient content management over decorative effects.

## Product constraints
- Supabase is the source of truth for catalogue publication and media metadata.
- Only tracks explicitly marked is_published = true belong in the public mobile listening catalogue. Draft tracks stay hidden; an optional release association does not hide a published track.
- Spotify URLs must be handled through Spotify's official embed/player experience; never treat a Spotify page URL as a raw audio stream.
- Direct audio URLs must use HTTPS.
- Keep secrets and service-role credentials out of browser and mobile source code.


## Cross-platform application direction

- Flutter is the shared presentation and application-state layer for Android, iOS/iPadOS, Windows and web.
- Platform media, secure storage, OAuth callbacks, downloads, notifications and microphone permissions are isolated behind platform adapters.
- The current Kotlin/Jetpack Compose Android application remains the production baseline until Flutter feature parity and platform acceptance are complete.
- Preserve the existing Supabase project, schema, Storage buckets, Edge Functions, users and Row Level Security policies.
- Android app identity remains `com.hightechentertainment.app`; preserve the existing signing certificate and require a higher version code for any update.
- The Flutter foundation is under development and is not an installable replacement or public release.
