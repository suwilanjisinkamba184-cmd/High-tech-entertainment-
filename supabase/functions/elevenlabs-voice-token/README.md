# HTE Voice Companion token service

This Supabase Edge Function is deployed as `elevenlabs-voice-token`. The signed-in Android app calls it before starting the private ElevenLabs WebRTC conversation.

## Runtime API flow

1. Android calls `POST /functions/v1/elevenlabs-voice-token` with the current Supabase user access token in `Authorization: Bearer <user-access-token>` and the project's publishable key in `apikey`.
2. The Edge Function validates the user with Supabase Auth. Anonymous or invalid sessions are rejected.
3. The function requests a short-lived ElevenLabs conversation token from:

   `GET https://api.elevenlabs.io/v1/convai/conversation/token?agent_id=agent_2601m6tt0dszfqaa7ec4jefe96ww`

   It authenticates server-to-server using the `xi-api-key` header.
4. Android receives only `{ "token": "...", "user_id": "..." }` and starts the ElevenLabs Android SDK session with its registered client tools.

## Required server-side secrets

- `ELEVENLABS_API_KEY` — canonical secret name. Store the key only in Supabase Edge Function secrets. Never put it in Android resources, Gradle properties, GitHub source, app settings, or a client-side build configuration.
- `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEYS` (or the legacy `SUPABASE_ANON_KEY`) are supplied by the Supabase project runtime.

### Legacy secret-name compatibility

The existing project currently has a legacy secret named `ELEVENLABS_API_KEY,` (with a trailing comma). The function temporarily checks the canonical `ELEVENLABS_API_KEY` first and falls back to that legacy name so the current deployment can continue to work. When the canonical secret is added in Supabase Dashboard → Edge Functions → Secrets, remove the trailing-comma secret. Do not paste the key into chat or commit it to GitHub.

## Agent configuration

- Agent: `agent_2601m6tt0dszfqaa7ec4jefe96ww`
- LLM: `gemini-2.5-flash`
- Speech recognition: `scribe_realtime`
- Conversational TTS model: `eleven_v4_turbo`
- Current voice: `cjVigY5qzO86Huf0OWal` (Eric — Smooth, Trustworthy)
- Authentication: enabled
- Knowledge document: `HTE Universe — Official Voice Companion Guide`
- Registered Android client tools: `control_playback`, `navigate_app`, `search_catalogue`

A separate Text-to-Speech API call is not required for live Voice Companion conversations; ElevenLabs Conversational AI handles ASR, turn-taking, tool calls, and speech output. Use the standalone TTS API only for separately generated voice audio.

## Security contract

- Supabase Edge Function JWT verification remains enabled (`verify_jwt = true`).
- Only signed-in users can receive a conversation token.
- The ElevenLabs API key is used only inside the Edge Function.
- Responses include `Cache-Control: no-store`.
- The Android app receives a short-lived conversation token and authenticated user ID, never the provider API key.

## Verification

A complete end-to-end test requires a signed-in app session, the server-side key, microphone permission, network access, and a real Android SDK conversation. An anonymous smoke test should be rejected with HTTP 401; a successful source build alone does not prove that a live voice session works.
