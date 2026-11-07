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

data class NativeDraftItem(val table: String, val id: String, val title: String)

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

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        userId()
        require(newPassword.length >= 8) { "Use a password with at least 8 characters." }
        val response = client.request("PUT", "/auth/v1/user",
            body = JSONObject().put("password", newPassword).toString())
        check(response.isSuccessful) { "Password update failed (HTTP ${response.statusCode})." }
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

    suspend fun uploadVideo(context: android.content.Context, uri: android.net.Uri, contentType: String, extension: String): Result<String> = runCatching {
        userId()
        require(contentType in setOf("video/mp4", "video/webm", "video/quicktime", "video/x-m4v")) {
            "Choose an MP4, WebM or QuickTime video."
        }
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "mp4" }
        val path = "founder/${System.currentTimeMillis()}.$safeExtension"
        val response = client.uploadStorageUri(context, "hte-videos", path, uri, contentType, 50L * 1024L * 1024L)
        check(response.isSuccessful) { "Video upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-videos", path)
    }

    suspend fun uploadAudio(bytes: ByteArray, contentType: String, extension: String): Result<String> = runCatching {
        userId()
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "For phone stability, choose audio files up to 20 MB." }
        require(contentType in setOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/wav", "audio/x-wav", "audio/ogg")) {
            "Choose an MP3, M4A, AAC, WAV or OGG audio file."
        }
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "mp3" }
        val path = "founder/${System.currentTimeMillis()}.$safeExtension"
        val response = client.uploadStorageObject("hte-audio", path, bytes, contentType)
        check(response.isSuccessful) { "Audio upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-audio", path)
    }

    suspend fun uploadAvatar(bytes: ByteArray, contentType: String): Result<String> = runCatching {
        val id = userId()
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "Choose an image smaller than 5 MB." }
        require(contentType in setOf("image/jpeg", "image/png", "image/webp")) { "Use a JPG, PNG or WebP image." }
        val extension = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$id/avatar-${System.currentTimeMillis()}.$extension"
        val response = client.uploadStorageObject("hte-avatars", path, bytes, contentType)
        check(response.isSuccessful) { "Avatar upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-avatars", path)
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

    suspend fun createVideo(title: String, url: String, description: String, artist: String): Result<Unit> = runCatching {
        userId()
        require(title.trim().isNotEmpty() && url.startsWith("https://")) { "Enter a title and secure video URL." }
        val body = JSONObject().put("title", title.trim().take(180))
            .put("video_url", url.trim()).put("description", description.trim().take(3000))
            .put("artist", artist.trim().ifBlank { "High-Tech Entertainment" })
            .put("is_published", false).toString()
        val response = client.post("videos", body, "return=minimal")
        check(response.isSuccessful) { "Could not save video (HTTP ${response.statusCode})." }
    }

    suspend fun publishVideo(id: String, publish: Boolean): Result<Unit> = runCatching {
        userId()
        val response = client.request("PATCH", "/rest/v1/videos", mapOf("id" to "eq.$id"),
            JSONObject().put("is_published", publish).toString(), mapOf("Prefer" to "return=minimal"))
        check(response.isSuccessful) { "Could not update video (HTTP ${response.statusCode})." }
    }

    suspend fun createTrack(title: String, artist: String, audioUrl: String, genre: String): Result<Unit> = runCatching {
        userId()
        require(title.isNotBlank() && artist.isNotBlank() && audioUrl.startsWith("https://")) {
            "Enter a title, artist and secure audio URL."
        }
        val response = client.post("tracks", JSONObject().put("title", title.trim())
            .put("artist", artist.trim()).put("audio_url", audioUrl.trim())
            .put("genre", genre.trim()).put("is_published", false).toString(), "return=minimal")
        check(response.isSuccessful) { "Could not create track (HTTP ${response.statusCode})." }
    }

    suspend fun createArtist(name: String, bio: String): Result<Unit> = runCatching {
        userId()
        require(name.isNotBlank()) { "Artist name is required." }
        val response = client.post("artists", JSONObject().put("name", name.trim())
            .put("bio", bio.trim()).put("is_published", false).toString(), "return=minimal")
        check(response.isSuccessful) { "Could not create artist (HTTP ${response.statusCode})." }
    }

    suspend fun createRelease(title: String, description: String): Result<Unit> = runCatching {
        userId()
        require(title.isNotBlank()) { "Release title is required." }
        val response = client.post("releases", JSONObject().put("title", title.trim())
            .put("description", description.trim()).put("is_published", false).toString(), "return=minimal")
        check(response.isSuccessful) { "Could not create release (HTTP ${response.statusCode})." }
    }

    suspend fun founderDrafts(): Result<List<NativeDraftItem>> = runCatching {
        userId()
        val sources = listOf(
            Triple("tracks", "title", "Track"),
            Triple("artists", "name", "Artist"),
            Triple("releases", "title", "Release"),
            Triple("videos", "title", "Video")
        )
        buildList {
            for ((table, titleColumn, _) in sources) {
                val response = client.get(table,
                    mapOf("is_published" to "eq.false", "order" to "created_at.desc", "limit" to "100"),
                    "id,$titleColumn")
                check(response.isSuccessful) { "Could not load $table drafts (HTTP ${response.statusCode})." }
                val rows = JSONArray(response.body)
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(NativeDraftItem(table, row.optString("id"), row.optString(titleColumn)))
                }
            }
        }
    }

    suspend fun setDraftPublished(table: String, id: String, published: Boolean): Result<Unit> = runCatching {
        userId()
        require(table in setOf("tracks", "artists", "releases", "videos"))
        val response = client.request("PATCH", "/rest/v1/$table", mapOf("id" to "eq.$id"),
            JSONObject().put("is_published", published).toString(), mapOf("Prefer" to "return=minimal"))
        check(response.isSuccessful) { "Could not update $table publication status (HTTP ${response.statusCode})." }
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
