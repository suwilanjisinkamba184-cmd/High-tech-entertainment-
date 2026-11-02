# Design system — High-Tech Entertainment

## Visual direction
An editorial independent music label: obsidian, brushed gold, and restrained silver. Use generous whitespace, strong typography, confident photography/artwork, and restrained transitions. The public site is a website first—not an app shell or dashboard.

## Palette
- Obsidian background: #090A0B
- Deep surface: #111214
- Primary text: #F3EFE6
- Secondary text: #AAA69D
- Brushed gold: #D4B36D
- Warm gold highlight: #F5E7BF
- Fine divider: rgba(212, 179, 109, 0.22)
- Silver accent: #B9B7B1

## Typography
- Display/headlines: Playfair Display, with Georgia fallback.
- Interface/body: DM Sans, with Arial/system fallback.
- Headlines use expressive scale and restrained tracking; body copy remains readable and calm.

## Website principles
- Editorial hierarchy, generous section spacing, asymmetric hero layout, concise navigation, and meaningful content.
- Prefer square or subtly rounded surfaces; avoid excessive pill shapes, nested cards, glassmorphism, neon gradients, and dashboard-like density.
- Use real release artwork and artist photography when available; do not fabricate artist portraits or release covers.
- Buttons should be clear, restrained, and accessible.
- Preserve the existing functional Supabase catalogue, booking, account, and admin flows.

## Android app principles
- Reuse the same HT crest SVG and gold/obsidian palette.
- Use native Android navigation, clear touch targets, readable text, and predictable playback feedback.
- Keep the app more functional and compact than the website, but not childish or overly decorative.
- Spotify catalogue entries open the official Spotify embedded player in an in-app WebView; direct HTTPS audio uses the native audio engine.

## Motion and accessibility
- Motion is purposeful, brief, and interruptible; never required to understand or operate the interface.
- Respect prefers-reduced-motion on the website.
- Maintain visible keyboard focus, semantic headings, accessible labels, adequate contrast, and responsive layouts.
