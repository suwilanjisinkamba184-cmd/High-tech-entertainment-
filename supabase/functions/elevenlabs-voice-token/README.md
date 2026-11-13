# HTE Voice Companion token service

This Supabase Edge Function is deployed as `elevenlabs-voice-token`. The signed-in Android app calls it before starting the private ElevenLabs WebRTC conversation.

## Runtime API flow

1. Android calls `POST /functions/v1/elevenlabs-voice-token` with the current Supabase user access token in `Authorization: Bearer <user-access-token>` and the project's publishable key in `apikey`.
2. The Edge Function validates the user with Supabase Auth. Anonymous or invalid sessions are rejected.
3. The function requests a short-lived ElevenLabs conversation token from `GET https://api.elevenlabs.io/v1/convai/conversation/token?agent_id=agent_2601m6tt0dszfqaa7ec4jefe96ww` using the server-side `xi-api-key` header.
4. Android receives only `{ "token": "...", "user_id": "..." }` and starts the official ElevenLabs Android SDK session with registered client tools.

## Required server-side secret

- `ELEVENLABS_API_KEY` is the canonical Supabase Edge Function secret name. The current function also temporarily supports the legacy `ELEVENLABS_API_KEY,` name (trailing comma) so existing deployments continue to work. Migrate to the canonical name in Supabase Dashboard → Edge Functions → Secrets, then remove the legacy alias. Never put the key in Android, Gradle, GitHub source, app settings, or chat.
- Supabase supplies the project URL and publishable/legacy anon key to the runtime.

## Agent configuration

- Agent: `agent_2601m6tt0dszfqaa7ec4jefe96ww`
- LLM: Gemini 2.5 Flash
- ASR: Scribe Realtime
- Conversational TTS: `eleven_v4_turbo`
- Default voice: Eric — Smooth & Trustworthy
- Selectable voices: Eric, Sarah — Warm & Professional, and Roger — Relaxed & Casual
- Authentication: enabled
- HTE knowledge document: `HTE Universe — Official Voice Companion Guide`
- Android client tools: `control_playback`, `navigate_app`, `search_catalogue`

A separate Text-to-Speech request is not required for live Voice Companion conversations. Conversational AI handles recognition, turn-taking, tool calls and spoken output. The Android app passes the selected approved voice ID through the SDK's per-session TTS override.

## Security contract

- Supabase Edge Function JWT verification remains enabled (`verify_jwt = true`).
- Only signed-in users receive conversation tokens.
- The ElevenLabs API key is used only inside the Edge Function.
- Responses include `Cache-Control: no-store`.
- The Android app receives a short-lived conversation token and authenticated user ID, never the provider API key.

## Verification

A complete end-to-end test requires a signed-in app session, a valid server-side secret, microphone permission, network access and a real Android SDK conversation. Anonymous smoke tests should be rejected with HTTP 401. A successful source build alone does not prove a live voice session works.
