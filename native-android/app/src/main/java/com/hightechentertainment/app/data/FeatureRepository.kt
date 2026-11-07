package com.hightechentertainment.app.data

import org.json.JSONArray
import org.json.JSONObject

data class NativeVideo(
    val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val artist: String
)

data class NativeProfile(
    val displayName: String,
    val bio: String,
    val phone: String,
    val avatarUrl: String
)

data class NativeBooking(
    val id: String,
    val name: String,
    val email: String,
    val type: String,
    val requestedDate: String?,
    val message: String,
    val status: String
)

data class FounderOverview(
    val tracks: Int,
    val artists: Int,
    val releases: Int,
    val videos: Int,
    val bookings: Int
)

/** Authenticated app features. PostgreSQL RLS remains the authority for every private/admin operation. */
class FeatureRepository(
    private val client: SupabaseRestClient,
    private val currentUserId: () -> String?
) {
    private fun userId(): String = currentUserId()?.takeIf(String::isNotBlank)
        ?: error("Sign in to continue.")

    suspend fun publishedVideos(): Result<List<NativeVideo>> = runCatching {
        val response = client.get("videos", mapOf("is_published" to "eq.true", "order" to "created_at.desc", "limit" to "100"),
            "id,title,description,video_url,thumbnail_url,artist")
        check(response.isSuccessful) { "Video catalogue unavailable (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val url = row.optString("video_url")
                if (id.isBlank() || !url.startsWith("https://")) continue
                add(NativeVideo(id, row.optString("title"), row.optString("description"), url,
                    row.optString("thumbnail_url").takeIf { it.startsWith("https://") },
                    row.optString("artist", "High-Tech Entertainment")))
            }
        }
    }

    suspend fun getProfile(): Result<NativeProfile> = runCatching {
        val id = userId()
        val response = client.get("profiles", mapOf("id" to "eq.$id", "limit" to "1"),
            "display_name,bio,phone,avatar_url")
        check(response.isSuccessful) { "Could not load profile (HTTP ${response.statusCode})." }
        val row = JSONArray(response.body).optJSONObject(0)
        NativeProfile(row?.optString("display_name").orEmpty(), row?.optString("bio").orEmpty(),
            row?.optString("phone").orEmpty(), row?.optString("avatar_url").orEmpty())
    }

    suspend fun saveProfile(profile: NativeProfile): Result<Unit> = runCatching {
        val id = userId()
        val body = JSONObject().put("id", id)
            .put("display_name", profile.displayName.trim().take(100))
            .put("bio", profile.bio.trim().take(1000))
            .put("phone", profile.phone.trim().take(40))
            .put("avatar_url", profile.avatarUrl.trim().take(1000))
            .toString()
        val response = client.request("POST", "/rest/v1/profiles", mapOf("on_conflict" to "id"),
            body, mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        check(response.isSuccessful) { "Could not save profile (HTTP ${response.statusCode})." }
    }

    suspend fun myBookings(): Result<List<NativeBooking>> = bookingsFor("user_id" to "eq.${userId()}")

    suspend fun founderBookings(): Result<List<NativeBooking>> = bookingsFor("order" to "created_at.desc")

    private suspend fun bookingsFor(filter: Pair<String, String>): Result<List<NativeBooking>> = runCatching {
        val response = client.get("bookings", mapOf(filter.first to filter.second, "order" to "created_at.desc", "limit" to "100"),
            "id,name,email,booking_type,requested_date,message,status")
        check(response.isSuccessful) { "Could not load bookings (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(NativeBooking(row.optString("id"), row.optString("name"), row.optString("email"),
                    row.optString("booking_type"), row.optString("requested_date").takeIf { it.isNotBlank() && it != "null" },
                    row.optString("message"), row.optString("status")))
            }
        }
    }

    suspend fun updateBookingStatus(id: String, status: String): Result<Unit> = runCatching {
        require(status in setOf("confirmed", "declined", "completed", "cancelled"))
        userId()
        val response = client.request("PATCH", "/rest/v1/bookings",
            mapOf("id" to "eq.$id"),
            JSONObject().put("status", status).toString(),
            mapOf("Prefer" to "return=minimal"))
        check(response.isSuccessful) { "Could not update booking (HTTP ${response.statusCode})." }
    }

    suspend fun founderOverview(): Result<FounderOverview> = runCatching {
        userId()
        suspend fun count(table: String): Int {
            val r = client.get(table, mapOf("limit" to "1000"), "id")
            check(r.isSuccessful) { "Could not load $table (HTTP ${r.statusCode})." }
            return JSONArray(r.body).length()
        }
        FounderOverview(count("tracks"), count("artists"), count("releases"), count("videos"), count("bookings"))
    }
}
