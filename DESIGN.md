# Design system — High-Tech Entertainment

## Brand direction
An editorial independent music and production label: obsidian, brushed gold and restrained silver. The website should feel like a credible music brand, not a generic SaaS dashboard. The Android app should retain the same identity while using native Android interaction patterns.

## Core palette
- Obsidian: `#090A0B`
- Deep surface: `#111214`
- Primary text: `#F3EFE6`
- Secondary text: `#AAA69D`
- Brushed gold: `#D4B36D`
- Warm highlight: `#F5E7BF`
- Silver accent: `#B9B7B1`
- Fine divider: `rgba(212, 179, 109, 0.22)`

Use semantic tokens rather than scattering literal colors. Maintain contrast in every state, including disabled, hover, focus, selected and error states.

## Typography and composition
Use expressive display typography with a reliable system fallback and a highly readable interface/body face. Establish hierarchy through scale, weight, spacing and alignment. Prefer generous whitespace, clear content grouping and purposeful asymmetry. Avoid excessive pill shapes, nested cards, glass effects and decorative gradients.

## Surface-specific rules
- **Public website:** Astro source lives in `website/`. Prioritize editorial storytelling, music discovery, artist/release content, service information and clear contact/booking paths.
- **Member account:** `account/` and shared assets provide authentication, profile, library and booking experiences. Prioritize clarity, legibility and predictable form feedback.
- **Founder Studio:** `admin/` and shared assets provide privileged content operations. Optimize for accurate status, safe actions, clear validation and role-aware access—not decoration.
- **Android:** `hte-universe-native/` is the production Kotlin/Compose app. Use native navigation, accessible touch targets, system-bar behavior and clear playback states. Reuse the HTE palette and approved logo assets.

## Media and playback
Use genuine approved artist photography and release artwork. Do not fabricate artist portraits or represent placeholder art as official content. Spotify links must use Spotify's official embed/player experience; a Spotify page URL is not a raw audio stream. Direct audio URLs must use HTTPS. Preserve separate lifecycle and authorization boundaries for music playback and video playback.

## Motion and accessibility
Motion must communicate state or support orientation. Keep it brief, interruptible and nonessential to task completion. Respect reduced-motion preferences. Provide visible focus, semantic structure, accessible names, sufficient contrast, scalable text and responsive layouts.
