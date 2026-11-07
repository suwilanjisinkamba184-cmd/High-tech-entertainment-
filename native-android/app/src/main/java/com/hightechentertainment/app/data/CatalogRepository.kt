package com.hightechentertainment.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NativeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String?,
    val coverUrl: String?,
    val genre: String?,
    val durationSeconds: Int?
)

data class CatalogResult<T>(val items: List<T>, val error: String? = null)

class CatalogRepository(
    private val client: SupabaseRestClient = SupabaseRestClient()
) {
    suspend fun publishedTracks(): CatalogResult<NativeTrack> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(
                table = "tracks",
                query = mapOf("is_published" to "eq.true", "order" to "created_at.desc", "limit" to "100"),
                select = "id,title,artist,audio_url,cover_url,genre,duration_seconds"
            )
            if (!response.isSuccessful) {
                return@withContext CatalogResult(
                    emptyList(),
                    "Music catalogue unavailable (HTTP ${response.statusCode}). Please retry."
                )
            }
            val json = JSONArray(response.body)
            val tracks = buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    add(
                        NativeTrack(
                            id = item.optString("id"),
                            title = title,
                            artist = item.optString("artist", "High-Tech Entertainment"),
                            audioUrl = item.optString("audio_url").takeIf { it.startsWith("https://") },
                            coverUrl = item.optString("cover_url").takeIf { it.startsWith("https://") },
                            genre = item.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                            durationSeconds = if (item.isNull("duration_seconds")) null else item.optInt("duration_seconds")
                        )
                    )
                }
            }
            CatalogResult(tracks)
        } catch (_: Exception) {
            CatalogResult(emptyList(), "Could not connect to the music catalogue. Check your internet connection.")
        }
    }
}
