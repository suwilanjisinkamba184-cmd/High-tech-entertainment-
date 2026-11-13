# HTE Voice Companion token function

This function is deployed as `elevenlabs-voice-token` and is called by the signed-in Android app.

## Required server-side secret

Configure `ELEVENLABS_API_KEY` in the Supabase project's Edge Function Secrets. Do not put this key in Android resources, Gradle properties, GitHub source, app settings, or a client-side build configuration. Never paste the key into chat.

The function also uses Supabase's runtime-provided `SUPABASE_URL` and publishable/legacy anon key. It validates the caller's Supabase access token with `auth.getUser` before requesting a short-lived ElevenLabs conversation token.

## Security contract

- Supabase Edge Function JWT verification remains enabled (`verify_jwt = true`).
- Only signed-in users can receive a conversation token.
- The ElevenLabs API key is used only in the Edge Function.
- Responses are marked `Cache-Control: no-store`.
- The Android app receives a short-lived conversation token and authenticated user ID, not the provider API key.

## Verification

A successful integration test requires a signed-in app session, the server-side secret, and a real Android microphone/network test. A successful source build alone does not prove that the live voice session works.
