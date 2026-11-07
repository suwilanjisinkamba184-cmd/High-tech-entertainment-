# High-Tech Entertainment 3.1 — Full Flutter App Migration

## Objective

Migrate the complete Android application experience to Flutter while retaining Kotlin only as the Android platform/integration layer. This is a full application migration, not a second app and not a partial home-screen redesign.

## Target architecture

- **Flutter:** every user-facing screen, navigation flow, design system, forms, accessibility, responsive layouts, onboarding, listener experience, Founder Studio, account/settings, library, and voice companion presentation.
- **Kotlin Android host:** application entry point, lifecycle integration, Android permissions, document/media pickers, secure OAuth callback handling, Media3 playback service, background playback/notifications, downloads, Android share intents, and platform-specific audio/video surfaces only where required by Android.
- **Supabase:** preserve the existing project, schema, authentication/session model, storage buckets, Edge Functions, and row-level security. No parallel backend or replacement database.
- **ElevenLabs:** preserve the existing agent and token endpoint; repair server configuration and integrate it into the Flutter voice experience.
- **Single playback engine:** continue using the existing Media3 service. Flutter controls it through the platform bridge; do not add a second player.

## User-facing feature parity required

### Listener app
- Splash and 15-second startup audio synchronization.
- Email/password and Google authentication, session restore, sign-out, password reset.
- Music catalogue, search/filter, artist catalogue and artist profiles.
- Video catalogue and playback.
- Persistent mini-player, queue, play/pause/next/previous, background playback and audio focus.
- Sponsored audio campaigns and ad impressions without bypassing or disabling the existing ad rules.
- Favourites, playlists, listening history, offline downloads.
- Account profile, avatar upload, settings, sharing, privacy and account deletion.

### Founder Studio
- Server-authorized founder access; client-side visibility is not authorization.
- Dashboard and catalogue counts.
- Upload audio, video, artwork and artist/profile images using existing Supabase Storage buckets and size/type validation.
- Create, edit, preview, publish/unpublish and safely delete eligible drafts.
- Manage releases, tracks, artists and videos.
- Manage bookings and sponsored-audio campaigns.
- Keep all privileged writes protected by Supabase RLS/server-side checks.

### AI Voice Companion
- Flutter voice screen and microphone permission UX.
- Secure token retrieval from the existing Supabase Edge Function.
- Existing ElevenLabs agent, voice and playback/navigation tools.
- Playback ducking/restoration, error states, reconnect and session cleanup.
- No API key embedded in the APK or Flutter assets.

## Migration rules

1. Do not merge this branch into `main` or replace the production APK until release gates pass.
2. Do not delete the existing native implementation while it is still needed as the platform/service layer.
3. Do not leave Flutter buttons as dead ends or silently hand off to the legacy Compose app for normal user journeys.
4. Any temporary native handoff must be documented as a migration blocker and removed when its Flutter screen is complete.
5. Keep Android 8+ support and tune memory, startup, image loading and animations for Android Go devices.
6. Preserve application ID, signing certificate lineage and Supabase configuration.

## Current implementation snapshot (2026-11-07)

- Flutter is the launcher UI hosted by the Kotlin Android app.
- Splash, sign-in/sign-up, catalogue screens, profile/booking, basic Founder Studio and sponsored campaign management are present.
- Google OAuth now starts from the Flutter experience and its deep-link callback is handled by the Flutter host Activity; the legacy Compose sign-in screen is no longer required for this flow.
- Voice Companion now has a Flutter screen; Kotlin hosts the existing ElevenLabs conversation SDK, microphone permission, audio ducking and playback/navigation/catalogue client tools behind the Flutter method channel.
- Playlist CRUD/add-track bridge and Flutter playlist/library interface have just been added; these require CI and device verification.
- Founder Studio now uses the Android document picker and bounded Supabase Storage uploads for audio/video, with profile image upload for signed-in users.
- Media selection is implemented in the Kotlin host through Android's document picker rather than a Flutter picker plugin, avoiding a known Flutter add-to-app plugin-loader/AGP incompatibility and keeping file transfer bounded for low-memory devices.
- Flutter password reset, password update, avatar upload, account-deletion request and app sharing are connected.
- Founder Studio now supports editing and deleting unpublished drafts, with publication-state checks, and editing/deleting unpublished sponsored-audio campaigns. Published campaigns must be unpublished before editing or deletion.
- All Founder Studio mutation bridge calls now check founder access, while Supabase RLS remains the final authority.
- Remaining settings/privacy work includes a fuller privacy/terms view and any account-deletion backend processing beyond the existing verified request workflow.
- AI token endpoint previously logged a missing server configuration error. The actual ElevenLabs API key must be configured as a Supabase Edge Function secret and tested without exposing it to the client.

## Release acceptance gates

- [ ] Flutter analyze and widget tests pass.
- [ ] Native Kotlin unit tests and debug build pass.
- [ ] Signed release candidate builds with the production-compatible signing certificate.
- [ ] No unresolved Android manifest, Gradle, SDK or dependency issues.
- [ ] All method-channel calls have matching native handlers and typed argument/result contracts.
- [ ] Auth, session restore, Google OAuth return, password reset and sign-out verified.
- [ ] Catalogue, artist, video, playback, background controls and sponsored audio verified.
- [ ] Favourites, playlists, downloads and offline playback verified.
- [ ] Founder authorization, uploads, draft lifecycle, bookings and campaigns verified against RLS.
- [ ] Voice token endpoint returns a short-lived token and the existing ElevenLabs agent completes a real conversation.
- [ ] Android 8+ and Android Go smoke tests pass, including low-memory behavior.
- [ ] Install-over upgrade preserves user data and signing compatibility.
- [ ] No production release until the owner approves the tested candidate.
