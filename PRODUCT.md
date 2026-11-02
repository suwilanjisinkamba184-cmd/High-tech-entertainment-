# Product brief — High-Tech Entertainment

## Product and audience
High-Tech Entertainment (HTE) is an independent music-production and artist platform based in Zambia. Its audience includes music listeners, artists, producers, prospective studio clients, collaborators, and people discovering HTE releases.

## Primary user goals
- Discover and listen to officially released music.
- Explore artists, releases, and official videos.
- Learn about music production, beats, recording, mixing, mastering, and promotion services.
- Contact HTE or submit a booking enquiry.
- In the Android app, browse the published catalogue and listen to released tracks, including official Spotify embeds.

## Brand personality
Confident, independent, musically literate, ambitious, polished, and welcoming. The brand should feel like a credible independent label and production house—not a generic software dashboard, template storefront, or childish music player.

## Surface modes
- Public website: Persuade and Experience. It should read as an editorial music-label website with a strong visual identity, meaningful whitespace, a clear story, and obvious discovery and contact paths.
- Android app: Operate and Experience. It should be a focused listening product with familiar native navigation, reliable playback, clear track information, and consistent HTE branding.
- Founder Studio/admin: Operate. Prioritize clarity, control, security, and efficient content management over decorative effects.

## Product constraints
- Supabase is the source of truth for catalogue publication and media metadata.
- Only tracks explicitly published and linked to a published release belong in the public mobile listening catalogue.
- Spotify URLs must be handled through Spotify's official embed/player experience; never treat a Spotify page URL as a raw audio stream.
- Direct audio URLs must use HTTPS.
- Keep secrets and service-role credentials out of browser and mobile source code.
