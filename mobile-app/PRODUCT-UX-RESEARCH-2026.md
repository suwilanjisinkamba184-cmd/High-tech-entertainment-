# High-Tech Entertainment — UX research and product priorities (2026)

## Executive summary

The highest-value improvements are not a large pile of novelty features. Listeners consistently reward **reliable playback, fast and predictable controls, useful discovery, control over their queue/library, dependable offline access, and a clean interface**. A music app should make listening effortless before adding social or AI features.

This is a directional synthesis of current 2025–2026 product coverage, community discussions and public user complaints. Reddit/community posts are qualitative evidence, not representative surveys. The priorities below should be validated with High-Tech listener feedback and app telemetry before large feature investment.

## What listeners repeatedly ask for

1. **Playback that never gets in the way.** Android Authority reported 2026 Spotify Android complaints about lag, frozen playback screens and unresponsive controls. Review analysis also highlights playback stopping/skipping, broken settings, wrong tracks and shuffle/order faults. Reliability is a retention feature, not just engineering hygiene.
2. **Predictable queue and shuffle behaviour.** YouTube Music community users specifically ask for manually queued tracks to play before auto-generated recommendations, playlist-only shuffle, multi-select playlist actions and the ability to block songs/artists.
3. **Recommendations that listen to the listener.** People want familiar favourites and genuinely related discovery—not repeated skipped artists or unwanted synthetic/low-quality uploads. Provide clear “Not interested”, “Don't play this artist” and “Play more like this” controls when recommendation data exists.
4. **Offline listening users can trust.** Community feedback repeatedly describes downloads appearing to disappear or failing when connectivity is lost. Make download state visible, distinguish local files from streamed tracks, and provide an offline-only view. Never auto-download large files without consent.
5. **Search that is immediate and easy to reach.** YouTube Music moved Search into the bottom navigation in 2026, explicitly improving reachability and matching common streaming-app navigation patterns. Search should support recent searches, clear results and fast access from the primary navigation.
6. **A strong now-playing experience.** Listeners value visible artwork, queue access, lyrics where rights/data permit, clear playback status, repeat/shuffle controls and easy sharing. Music-video switching is a differentiator when the catalogue supports it.
7. **Respect the listener's data, battery and storage.** Make download size, Wi-Fi/mobile-data choice, cache size and offline availability understandable. Keep animations optional/lightweight and avoid blocking playback with network calls.
8. **Keep the product focused.** Community feedback also criticises clutter and unnecessary feature growth. Put advanced tools in secondary screens; keep Home, Search, Library and Now Playing obvious.

## Evidence and source notes

- Android Authority, 13 Aug 2026 — Spotify Android complaints about lag, frozen UI and unresponsive playback controls: https://www.androidauthority.com/spotify-android-app-lag-performance-issues-3698268/
- OneStar, Oct 2026 — cross-service review analysis; recurring issues include playback bugs, shuffle/order faults, library/playlist problems and recommendations: https://onestar.sh/vs/spotify-vs-apple-music-vs-youtube-music-what-listeners-complain-about-2fzlj
- YouTube Music Community, 13 Mar 2026 — requests for queue ordering, playlist-only shuffle, multi-add and artist/song blocking: https://support.google.com/youtubemusic/thread/416719048/app-is-not-user-friendly-and-needs-a-lot-of-improvement
- YouTube Music Community, 16 May 2026 — complaints about repetitive recommendations and unreliable offline downloads; requests for lyrics correction and listening insights: https://support.google.com/youtubemusic/thread/434167536/youtube-music-needs-better-recommendations-downloads-lyrics-and-web-experience
- 9to5Google, 10 Jun 2026 — Search moved to the bottom navigation for easier one-handed access: https://9to5google.com/2026/06/10/youtube-music-search-bottom-bar-wide/
- Reddit r/YouTubeMusic, 8 Mar 2026 — listeners discuss recommendation quality, value and navigation preferences: https://www.reddit.com/r/YoutubeMusic/comments/1ro1ucp/what_do_you_guys_prefer_youtube_music_or_spotify/
- web.dev — offline UX guidance: make Save offline explicit, show what is available offline and explain storage: https://web.dev/articles/offline-ux-design-guidelines
- Shorebird official docs — automatic patches download in the background and load on the next process start; a Flutter widget/engine rebuild is not a substitute for a process restart: https://docs.shorebird.dev/code-push/update-strategies/ and https://docs.shorebird.dev/code-push/faq/

## Current High-Tech app audit

### Already present in the inspected Android code

- Home, Discover, Releases, Library, Profile and More navigation.
- Shared player bar, full player, seek, previous/next, shuffle, repeat and queue entry point.
- Favourites revision state, local/offline media library, downloads and media sharing.
- Artist and release pages, video previews, Spotify embed hand-off.
- Smart autoplay, appearance settings, startup jingle, notifications and diagnostics.
- Separate automatic Shorebird OTA channel and full APK installer path.

### Priority gaps to validate/fix

| Priority | Work | Why it matters | Acceptance criteria |
|---|---|---|---|
| P0 | One authoritative update flow | Avoid confusing OTA patches with full APK upgrades | OTA patch and full APK are clearly distinguished; pending patch state is accurate; restart is a real process restart |
| P0 | Playback regression suite | Basic playback reliability is the core promise | Play/pause/seek/next/previous/queue/ad transitions survive network loss and app backgrounding |
| P1 | Queue semantics | Queue order and shuffle are common pain points | Manual “Play next” queue is separate from autoplay; shuffle never silently changes the user's source playlist |
| P1 | Offline trust | Downloads are valuable in intermittent connectivity | Each item shows downloading/saved/failed; offline library plays without Supabase; retries preserve completed files |
| P1 | Search reachability | Search is a primary music task | Search is one tap from primary navigation, with recent searches and clear empty/error states |
| P1 | Listener controls | Repeated unwanted artists erode trust | Add “Not interested” and block controls only when the underlying preference/filter model is implemented end-to-end |
| P2 | Lyrics and music-video pairing | Improves the listening session | Show synced lyrics only with licensed/supported data; offer video switch only when an official video exists |
| P2 | Social listening | Can improve sharing and discovery | Shareable track/playlist links first; collaborative listening only after identity, presence and moderation are designed |
| P2 | Audio/data controls | Better for different devices and data budgets | Add explicit stream-quality/data-saver controls only when the delivery backend supports real quality variants |

## Implementation decision for this pass

- Keep Shorebird's built-in automatic updater enabled. It downloads Stable patches in the background and loads them on the next cold process start.
- Remove the duplicate manual OTA request from the splash sequence. A Flutter root-widget rebuild is not a process restart and cannot activate a Shorebird patch.
- Make the user-requested restart a genuine Android process restart.
- When a user manually checks OTA, compare the running patch with the staged patch. If a patch is ready, offer **Restart now** or **Later**; never interrupt a song with an unannounced forced restart.
- Preserve the full APK installer as a separate fallback for native Android changes, Flutter engine/SDK upgrades, permissions, plugins or other changes Shorebird cannot patch.

## Not in scope for a blind one-pass implementation

Do not add pretend lyrics, unsupported lossless audio, fake AI recommendations, automatic mobile-data downloads, or artist blocking that only hides a card but still allows the artist through autoplay/search. These require real catalogue metadata, storage policy, user preference persistence and backend query changes. Implement each only with end-to-end tests and a clear user control.