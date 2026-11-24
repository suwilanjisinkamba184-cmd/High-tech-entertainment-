# HTE Voice Companion token service

## Purpose
The `elevenlabs-voice-token` Supabase Edge Function issues a short-lived ElevenLabs conversation token to an authenticated HTE app session. The function is configured with JWT verification enabled in `supabase/config.toml`.

## Request flow
1. The Android app sends a request to `POST /functions/v1/elevenlabs-voice-token` with the current Supabase user access token in the Authorization header and the project's publishable key in `apikey`.
2. The Edge Function validates the user session with Supabase Auth.
3. The function requests a short-lived conversation token from the configured ElevenLabs agent using the server-side provider API key.
4. The client receives only the short-lived conversation token and the authenticated user identifier required by the app. It must never receive the provider API key.

The exact request/response fields, agent identifier and provider endpoint are defined by `index.ts`; keep this document aligned with that source when it changes.

## Secrets
The ElevenLabs API key must remain in Supabase Edge Function secrets. Never place it in Android source, Gradle properties committed to Git, GitHub source, website code, app settings or chat. Do not print the value in logs.

Use the canonical secret name expected by the current function source. If a legacy secret alias is still supported for migration, remove it only after the deployed function and secret configuration have been verified.

## Security
- Keep `verify_jwt = true` for this function.
- Reject anonymous or invalid sessions.
- Return short-lived tokens only to the authenticated caller.
- Use `Cache-Control: no-store` for token responses.
- Keep provider credentials server-side.
- Bind the returned user identifier to the authenticated session in the Android client before starting a voice session.

## Agent configuration
Agent model, voice, knowledge base, available client tools and provider settings are managed in the ElevenLabs agent configuration and the checked-in function source. They can change independently of this README. Verify the live agent configuration and a successful authenticated session before documenting a voice/model as available.

## Verification
A complete end-to-end test requires a valid signed-in app session, configured server-side secret, microphone permission, network access and a real SDK conversation. Test anonymous rejection, token issuance, user/session binding, microphone denial, provider outage and session termination. A successful source build does not prove the live Voice Companion works.
