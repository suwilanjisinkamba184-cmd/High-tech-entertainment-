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

data class NativeArtist(
    val id: String,
    val name: String,
    val slug: String?,
    val bio: String,
    val imageUrl: String?
)

data class NativeRelease(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
    val releaseDate: String?,
    val spotifyUrl: String?
)

data class NativeProfile(
    val displayName: String,
    val bio: String,
    val phone: String,
    val avatarUrl: String
)

data class NativeAppPreferences(
    val autoplay: Boolean = true,
    val shuffle: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val audioQuality: String = "high",
    val wifiOnlyDownloads: Boolean = true,
    val releaseNotifications: Boolean = true,
    val artistNotifications: Boolean = true,
    val promotionalNotifications: Boolean = false
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

    suspend fun publishedArtists(): Result<List<NativeArtist>> = runCatching {
        val response = client.get(
            "artists",
            mapOf("is_published" to "eq.true", "order" to "name.asc", "limit" to "100"),
            "id,name,slug,bio,image_url"
        )
        check(response.isSuccessful) { "Artist catalogue unavailable (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val name = row.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                add(NativeArtist(
                    id = id,
                    name = name,
                    slug = row.optString("slug").takeIf { it.isNotBlank() && it != "null" },
                    bio = row.optString("bio").takeIf { it != "null" }.orEmpty(),
                    imageUrl = row.optString("image_url").takeIf { it.startsWith("https://") }
                ))
            }
        }
    }

    suspend fun publishedReleases(): Result<List<NativeRelease>> = runCatching {
        val response = client.get(
            "releases",
            mapOf("is_published" to "eq.true", "order" to "release_date.desc.nullslast,created_at.desc", "limit" to "100"),
            "id,title,description,cover_url,release_date,spotify_url"
        )
        check(response.isSuccessful) { "Release catalogue unavailable (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val title = row.optString("title").trim()
                if (id.isBlank() || title.isBlank()) continue
                add(NativeRelease(
                    id = id,
                    title = title,
                    description = row.optString("description").takeUnless { it == "null" }.orEmpty(),
                    coverUrl = row.optString("cover_url").takeIf { it.startsWith("https://") },
                    releaseDate = row.optString("release_date").takeIf { it.isNotBlank() && it != "null" },
                    spotifyUrl = row.optString("spotify_url").takeIf { it.startsWith("https://") }
                ))
            }
        }
    }

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

    suspend fun getAppPreferences(): Result<NativeAppPreferences> = runCatching {
        val id = userId()
        val response = client.get(
            "app_preferences",
            mapOf("user_id" to "eq.$id", "limit" to "1"),
            "autoplay,shuffle,crossfade_seconds,audio_quality,wifi_only_downloads,release_notifications,artist_notifications,promotional_notifications"
        )
        check(response.isSuccessful) { "Could not load app settings (HTTP ${response.statusCode})." }
        val row = JSONArray(response.body).optJSONObject(0) ?: return@runCatching NativeAppPreferences()
        NativeAppPreferences(
            autoplay = row.optBoolean("autoplay", true),
            shuffle = row.optBoolean("shuffle", false),
            crossfadeSeconds = row.optInt("crossfade_seconds", 0).coerceIn(0, 12),
            audioQuality = row.optString("audio_quality", "high").takeIf { it in setOf("data_saver", "normal", "high") } ?: "high",
            wifiOnlyDownloads = row.optBoolean("wifi_only_downloads", true),
            releaseNotifications = row.optBoolean("release_notifications", true),
            artistNotifications = row.optBoolean("artist_notifications", true),
            promotionalNotifications = row.optBoolean("promotional_notifications", false)
        )
    }

    suspend fun saveAppPreferences(preferences: NativeAppPreferences): Result<Unit> = runCatching {
        val id = userId()
        require(preferences.audioQuality in setOf("data_saver", "normal", "high"))
        require(preferences.crossfadeSeconds in 0..12)
        val body = JSONObject()
            .put("user_id", id)
            .put("autoplay", preferences.autoplay)
            .put("shuffle", preferences.shuffle)
            .put("crossfade_seconds", preferences.crossfadeSeconds)
            .put("audio_quality", preferences.audioQuality)
            .put("wifi_only_downloads", preferences.wifiOnlyDownloads)
            .put("release_notifications", preferences.releaseNotifications)
            .put("artist_notifications", preferences.artistNotifications)
            .put("promotional_notifications", preferences.promotionalNotifications)
        val response = client.request(
            "POST", "/rest/v1/app_preferences", mapOf("on_conflict" to "user_id"),
            body.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
        check(response.isSuccessful) { "Could not save app settings (HTTP ${response.statusCode})." }
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

    suspend fun uploadAudio(
        context: android.content.Context,
        uri: android.net.Uri,
        contentType: String,
        extension: String
    ): Result<String> = runCatching {
        userId()
        require(contentType in setOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/wav", "audio/x-wav", "audio/ogg")) {
            "Choose an MP3, M4A, AAC, WAV or OGG audio file."
        }
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "mp3" }
        val path = "founder/${System.currentTimeMillis()}.$safeExtension"
        val response = client.uploadStorageUri(
            context, "hte-audio", path, uri, contentType, 20L * 1024L * 1024L
        )
        check(response.isSuccessful) { "Audio upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-audio", path)
    }

    suspend fun uploadAvatar(
        context: android.content.Context,
        uri: android.net.Uri,
        contentType: String,
        extension: String
    ): Result<String> = runCatching {
        val id = userId()
        require(contentType in setOf("image/jpeg", "image/png", "image/webp")) {
            "Use a JPG, PNG or WebP image."
        }
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "jpg" }
        val path = "$id/avatar-${System.currentTimeMillis()}.$safeExtension"
        val response = client.uploadStorageUri(
            context, "hte-avatars", path, uri, contentType, 5L * 1024L * 1024L
        )
        check(response.isSuccessful) { "Profile picture upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-avatars", path)
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

    suspend fun uploadArtwork(
        context: android.content.Context,
        uri: android.net.Uri,
        contentType: String,
        extension: String
    ): Result<String> = runCatching {
        userId()
        require(contentType in setOf("image/jpeg", "image/png", "image/webp", "image/avif")) {
            "Choose a JPG, PNG, WebP or AVIF image."
        }
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "jpg" }
        val path = "releases/${System.currentTimeMillis()}.$safeExtension"
        val response = client.uploadStorageUri(
            context, "hte-artwork", path, uri, contentType, 20L * 1024L * 1024L
        )
        check(response.isSuccessful) { "Artwork upload failed (HTTP ${response.statusCode})." }
        client.publicStorageUrl("hte-artwork", path)
    }

    suspend fun createRelease(
        title: String,
        description: String,
        coverUrl: String = "",
        releaseDate: String = "",
        spotifyUrl: String = ""
    ): Result<Unit> = runCatching {
        userId()
        require(title.isNotBlank()) { "Release title is required." }
        require(coverUrl.isBlank() || coverUrl.startsWith("https://")) { "Artwork URL must use HTTPS." }
        require(spotifyUrl.isBlank() || spotifyUrl.startsWith("https://open.spotify.com/")) {
            "Enter a valid Spotify release URL."
        }
        val body = JSONObject().put("title", title.trim())
            .put("description", description.trim()).put("is_published", false)
        coverUrl.takeIf(String::isNotBlank)?.let { body.put("cover_url", it) }
        releaseDate.takeIf(String::isNotBlank)?.let { body.put("release_date", it) }
        spotifyUrl.takeIf(String::isNotBlank)?.let { body.put("spotify_url", it) }
        val response = client.post("releases", body.toString(), "return=minimal")
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

    suspend fun draftDetails(table: String, id: String): Result<Map<String, String>> = runCatching {
        userId()
        val columns = when (table) {
            "tracks" -> "id,title,artist,genre,audio_url"
            "artists" -> "id,name,bio"
            "releases" -> "id,title,description"
            "videos" -> "id,title,artist,description,video_url"
            else -> error("Unsupported draft type.")
        }
        val response = client.get(table, mapOf("id" to "eq.$id", "is_published" to "eq.false", "limit" to "1"), columns)
        check(response.isSuccessful) { "Could not load draft (HTTP ${response.statusCode})." }
        val row = JSONArray(response.body).optJSONObject(0) ?: error("Draft not found or already published.")
        buildMap {
            for (key in columns.split(",").filter { it != "id" }) {
                put(key, row.optString(key).takeUnless { it == "null" }.orEmpty())
            }
        }
    }

    suspend fun updateDraft(
        table: String,
        id: String,
        title: String,
        artist: String,
        description: String,
        mediaUrl: String,
        genre: String
    ): Result<Unit> = runCatching {
        userId()
        val body = JSONObject()
        when (table) {
            "tracks" -> {
                require(title.isNotBlank() && artist.isNotBlank() && mediaUrl.startsWith("https://")) {
                    "Track title, artist and secure audio URL are required."
                }
                body.put("title", title.trim()).put("artist", artist.trim())
                    .put("genre", genre.trim()).put("audio_url", mediaUrl.trim())
            }
            "artists" -> {
                require(title.isNotBlank()) { "Artist name is required." }
                body.put("name", title.trim()).put("bio", description.trim())
            }
            "releases" -> {
                require(title.isNotBlank()) { "Release title is required." }
                body.put("title", title.trim()).put("description", description.trim())
            }
            "videos" -> {
                require(title.isNotBlank() && mediaUrl.startsWith("https://")) {
                    "Video title and secure video URL are required."
                }
                body.put("title", title.trim()).put("artist", artist.trim())
                    .put("description", description.trim()).put("video_url", mediaUrl.trim())
            }
            else -> error("Unsupported draft type.")
        }
        val response = client.request(
            "PATCH", "/rest/v1/$table",
            mapOf("id" to "eq.$id", "is_published" to "eq.false"),
            body.toString(), mapOf("Prefer" to "return=representation")
        )
        check(response.isSuccessful) { "Could not update draft (HTTP ${response.statusCode})." }
        check(JSONArray(response.body).length() > 0) { "Draft not found, or it has already been published." }
    }

    suspend fun deleteDraft(table: String, id: String): Result<Unit> = runCatching {
        userId()
        require(table in setOf("tracks", "artists", "releases", "videos")) { "Unsupported draft type." }
        val response = client.request(
            "DELETE", "/rest/v1/$table",
            mapOf("id" to "eq.$id", "is_published" to "eq.false"),
            headers = mapOf("Prefer" to "return=representation")
        )
        check(response.isSuccessful) { "Could not delete draft (HTTP ${response.statusCode})." }
        check(JSONArray(response.body).length() > 0) { "Draft not found, or it has already been published." }
    }

    suspend fun setDraftPublished(table: String, id: String, published: Boolean): Result<Unit> = runCatching {
        userId()
        require(table in setOf("tracks", "artists", "releases", "videos"))
        val response = client.request("PATCH", "/rest/v1/$table", mapOf("id" to "eq.$id"),
            JSONObject().put("is_published", published).toString(), mapOf("Prefer" to "return=minimal"))
        check(response.isSuccessful) { "Could not update $table publication status (HTTP ${response.statusCode})." }
    }

    suspend fun hasFounderAccess(): Result<Boolean> = runCatching {
        userId()
        val response = client.request("POST", "/rest/v1/rpc/is_hte_admin", body = "{}")
        check(response.isSuccessful) { "Could not verify Founder Studio access (HTTP ${response.statusCode})." }
        response.body.trim().toBooleanStrictOrNull()
            ?: error("Founder access check returned an invalid response.")
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
