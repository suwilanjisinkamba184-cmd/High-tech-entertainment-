# HTE UNIVERSE — integrated native source register

The canonical Android implementation is Kotlin + Jetpack Compose in this directory. The compact uploaded starter archive was reviewed but not copied wholesale because it defines duplicate application and Media3 service entry points.

## Integrated components
- Original High-Tech Entertainment logo retained for launcher, round launcher and Android 12+ splash; the custom branded splash displays the same logo and synchronizes the bundled startup recording to a 15-second visual sequence.
- Compose 2026 screens retained, with a single app-scoped Media3 session service and a waveform mini/full player.
- Supabase PKCE authentication and Google Credential Manager sign-in, with a browser fallback when no web client ID is configured.
- Founder uploads remain gated by a server-verified owner role; media upload operations use bounded streaming and existing Supabase storage buckets.
- Offline media downloads use Android DownloadManager and app-specific storage; metered downloads respect the user's network policy and Data Saver setting.
- Voice Companion obtains a short-lived ElevenLabs token from an authenticated Supabase Edge Function; provider credentials remain server-side. Voice choice is saved locally and playback/navigation/search tools return explicit success or failure.
- Native update checks retain the native-release tag filter so a legacy Flutter APK cannot be offered to the Compose app.

## Compatibility and release safety
- Package ID remains `com.hightechentertainment.app`; current version lineage remains 4.0.1 / code 33 (no downgrade from the prior candidate).
- Existing production signing configuration and Supabase project are not rotated or modified by this source merge.
- The uploaded starter's duplicate `HteApp`, `HteRoot` and playback service are not added.
- No destructive database migration is included.
- Release readiness still requires fresh CI compile, unit tests, Android lint, production signing certificate comparison and installation on a physical Android device.
