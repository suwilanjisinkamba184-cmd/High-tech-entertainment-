package com.hightechentertainment.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artist: String = "Unknown artist",
    val genre: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null
)

/** Read-only public catalogue access; writes remain restricted to Founder-authorized flows. */
class TrackRepository {
    suspend fun getPublishedTracks(): List<TrackDto> =
        SupabaseClientProvider.client
            .from("tracks")
            .select {
                filter { eq("is_published", true) }
            }
            .decodeList<TrackDto>()
}
