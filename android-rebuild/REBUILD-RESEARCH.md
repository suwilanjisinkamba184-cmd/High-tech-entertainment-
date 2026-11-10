# Rebuild research and technical decisions

Research checked 10 November 2026 against Android Developers documentation.

## Architecture
Use a single-activity, layered native architecture: Compose/Material 3 UI; ViewModels with immutable state and unidirectional data flow; domain use cases; typed data repositories for Supabase/Auth/Storage and local persistence; infrastructure services for Media3, downloads, encrypted sessions and updates.

Android architecture guidance recommends clear UI/data boundaries, optional domain layer, unidirectional data flow, state holders, coroutines/Flow, dependency injection, adaptive layouts and testability. https://developer.android.com/topic/architecture

## Media
Use AndroidX Media3 ExoPlayer and MediaSessionService for listener audio/video. Keep player lifecycle out of Compose screens; connect UI with MediaController. Media3 is Android's recommended playback stack and supports playlists, streaming, audio focus, sessions and foreground background playback. https://developer.android.com/media/implement/playback-app

## Startup audio diagnosis
The current native candidate's StartupJingle returns immediately whenever AudioManager.isMusicActive is true and does not request audio focus. That is a confirmed silent-failure path whenever Android reports another active music stream; it may not be the only device-level cause.

The replacement must start after the first frame, use the existing bundled 15-second MP3 as a dedicated one-shot resource outside the music/ad queue, request transient audio focus immediately before playback, start only when focus is granted, handle focus loss/completion/decode errors/release, and log safe diagnostic milestones. Do not use isMusicActive as a blanket early-return condition.

Android guidance: request focus before playback, respect focus loss, abandon focus after transient playback. Android 15+ restricts focus requests to the top app or a foreground service. https://developer.android.com/media/optimize/audio-focus and https://developer.android.com/reference/android/media/AudioFocusRequest

## Device and product constraints
Preserve package ID, signing identity, Supabase, Google OAuth clients and live user/content data. Profile for Android Go using paged catalogue loading, bounded image/media caches and adaptive layouts. Test API 26+, target Android Go hardware, current Android, TalkBack and font scaling. Appetize is for rapid UI-flow checks; physical-device checks remain mandatory for audio, Bluetooth, focus, downloads, install-over and Android Go performance.
