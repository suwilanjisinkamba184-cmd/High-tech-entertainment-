package com.hightechentertainment.app.data

import android.content.Context
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class AudienceEventInsertDto(
    @SerialName("visitor_id") val visitorId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("page_path") val pagePath: String = "/app",
    val source: String = "android"
)

/**
 * Records anonymous, privacy-safe app audience events. It stores random UUIDs,
 * not names, email addresses, phone numbers, IP addresses or device identifiers.
 */
class AudienceAnalyticsRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("hte_audience_analytics", Context.MODE_PRIVATE)

    private val visitorId: String = preferences.getString("visitor_id", null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString("visitor_id", it).apply() }

    private val sessionId: String = UUID.randomUUID().toString()

    suspend fun record(eventType: String, trackId: String? = null) {
        require(eventType in setOf("page_view", "track_play", "track_download"))
        SupabaseClientProvider.client.from("hte_audience_events").insert(
            AudienceEventInsertDto(
                visitorId = visitorId,
                sessionId = sessionId,
                eventType = eventType,
                trackId = trackId
            )
        )
    }
}
