package com.hightechentertainment.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class SponsoredAudioAd(
    val id: String,
    val title: String,
    val audioUrl: String,
    val startsAt: String?,
    val endsAt: String?,
    val priority: Int
)

/** Reads only active campaigns; RLS authorizes founder campaign management and impression writes. */
class SponsoredAudioRepository(
    private val client: SupabaseRestClient,
    private val currentUserId: () -> String?
) {
    suspend fun nextActiveAd(): Result<SponsoredAudioAd?> = runCatching {
        val response = client.get("audio_ads",
            mapOf("is_published" to "eq.true", "order" to "priority.desc,created_at.desc", "limit" to "50"),
            "id,title,audio_url,starts_at,ends_at,priority")
        check(response.isSuccessful) { "Sponsored audio unavailable (HTTP ${response.statusCode})." }
        val now = Instant.now()
        val rows = JSONArray(response.body)
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.firstNotNullOfOrNull { row ->
            val start = row.optString("starts_at").takeIf { it.isNotBlank() && it != "null" }
            val end = row.optString("ends_at").takeIf { it.isNotBlank() && it != "null" }
            val validStart = start?.let { runCatching { Instant.parse(it).isAfter(now) }.getOrDefault(true) } ?: false
            val validEnd = end?.let { runCatching { !Instant.parse(it).isAfter(now) }.getOrDefault(true) } ?: false
            val url = row.optString("audio_url")
            if (validStart || validEnd || !url.startsWith("https://")) null
            else SponsoredAudioAd(row.optString("id"), row.optString("title"), url, start, end, row.optInt("priority"))
        }
    }

    suspend fun recordImpression(adId: String): Result<Unit> = runCatching {
        val user = currentUserId()?.takeIf(String::isNotBlank) ?: error("Sign in to record an ad impression.")
        val response = client.post("ad_impressions",
            JSONObject().put("ad_id", adId).put("user_id", user).toString(), "return=minimal")
        check(response.isSuccessful) { "Could not record sponsored audio impression (HTTP ${response.statusCode})." }
    }

    suspend fun createCampaign(title: String, audioUrl: String, startsAt: String?, endsAt: String?, priority: Int): Result<Unit> = runCatching {
        require(currentUserId() != null) { "Sign in as an administrator." }
        require(title.trim().isNotEmpty() && audioUrl.startsWith("https://")) { "Enter a campaign title and secure audio URL." }
        val body = JSONObject().put("title", title.trim().take(150))
            .put("audio_url", audioUrl.trim()).put("is_published", false)
            .put("priority", priority.coerceIn(0, 1000))
        startsAt?.takeIf(String::isNotBlank)?.let { body.put("starts_at", it) }
        endsAt?.takeIf(String::isNotBlank)?.let { body.put("ends_at", it) }
        val response = client.post("audio_ads", body.toString(), "return=minimal")
        check(response.isSuccessful) { "Could not save campaign (HTTP ${response.statusCode})." }
    }

    suspend fun campaigns(): Result<List<SponsoredAudioAd>> = runCatching {
        val response = client.get("audio_ads", mapOf("order" to "created_at.desc", "limit" to "100"),
            "id,title,audio_url,starts_at,ends_at,priority")
        check(response.isSuccessful) { "Could not load campaigns (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(SponsoredAudioAd(row.optString("id"), row.optString("title"), row.optString("audio_url"),
                    row.optString("starts_at").takeIf { it.isNotBlank() && it != "null" },
                    row.optString("ends_at").takeIf { it.isNotBlank() && it != "null" }, row.optInt("priority")))
            }
        }
    }

    suspend fun setCampaignPublished(id: String, published: Boolean): Result<Unit> = runCatching {
        require(currentUserId() != null)
        val response = client.request("PATCH", "/rest/v1/audio_ads", mapOf("id" to "eq.$id"),
            JSONObject().put("is_published", published).toString(), mapOf("Prefer" to "return=minimal"))
        check(response.isSuccessful) { "Could not update campaign (HTTP ${response.statusCode})." }
    }
}
