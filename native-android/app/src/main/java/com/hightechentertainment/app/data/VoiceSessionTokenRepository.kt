package com.hightechentertainment.app.data

import org.json.JSONObject

/** Mints a short-lived ElevenLabs conversation token through the authenticated Supabase Edge Function. */
class VoiceSessionTokenRepository(private val client: SupabaseRestClient) {
    suspend fun fetchToken(): String {
        val response = client.request(
            method = "POST",
            path = "/functions/v1/elevenlabs-voice-token"
        )
        val body = runCatching { JSONObject(response.body) }.getOrNull()
        if (!response.isSuccessful) {
            val message = body?.optString("error").orEmpty()
            throw IllegalStateException(message.ifBlank { "Voice session could not be started (${response.statusCode})." })
        }
        return body?.optString("token")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Voice service returned an invalid session token.")
    }
}
