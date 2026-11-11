package com.hightechentertainment.app.data

import org.json.JSONArray
import org.json.JSONObject

data class NativePlaylist(
    val id: String,
    val name: String,
    val description: String
)

data class LibraryOperationResult(
    val successful: Boolean,
    val message: String? = null,
    val payload: String? = null
)

/**
 * User-scoped library operations. The authenticated user ID is supplied from the
 * current Supabase session; RLS independently enforces ownership on every request.
 */
class UserLibraryRepository(
    private val client: SupabaseRestClient,
    private val currentUserId: () -> String?
) {
    suspend fun favouriteTrackIds(): Result<List<String>> = runCatching {
        val userId = requireUser()
        val response = client.get(
            table = "user_favorites",
            query = mapOf("user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "500"),
            select = "track_id"
        )
        check(response.isSuccessful) { "Could not load favourites (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.optString("track_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }
    }

    suspend fun addFavourite(trackId: String): LibraryOperationResult {
        if (trackId.isBlank()) return LibraryOperationResult(false, "Select a valid track.")
        return request {
            val userId = requireUser()
            client.request(
                method = "POST",
                path = "/rest/v1/user_favorites",
                query = mapOf("on_conflict" to "user_id,track_id"),
                body = JSONObject().put("user_id", userId).put("track_id", trackId).toString(),
                headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }
    }

    suspend fun removeFavourite(trackId: String): LibraryOperationResult {
        if (trackId.isBlank()) return LibraryOperationResult(false, "Select a valid track.")
        return request {
            val userId = requireUser()
            client.request(
                method = "DELETE",
                path = "/rest/v1/user_favorites",
                query = mapOf("user_id" to "eq.$userId", "track_id" to "eq.$trackId")
            )
        }
    }

    suspend fun followedArtistIds(): Result<List<String>> = runCatching {
        val userId = requireUser()
        val response = client.get(
            "artist_follows",
            mapOf("user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "500"),
            "artist_id"
        )
        check(response.isSuccessful) { "Could not load followed artists (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                rows.optJSONObject(i)?.optString("artist_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }
    }

    suspend fun followArtist(artistId: String): LibraryOperationResult {
        if (artistId.isBlank()) return LibraryOperationResult(false, "Select a valid artist.")
        return request {
            val userId = requireUser()
            client.request(
                "POST", "/rest/v1/artist_follows",
                mapOf("on_conflict" to "user_id,artist_id"),
                JSONObject().put("user_id", userId).put("artist_id", artistId).toString(),
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }
    }

    suspend fun unfollowArtist(artistId: String): LibraryOperationResult {
        if (artistId.isBlank()) return LibraryOperationResult(false, "Select a valid artist.")
        return request {
            val userId = requireUser()
            client.request(
                "DELETE", "/rest/v1/artist_follows",
                mapOf("user_id" to "eq.$userId", "artist_id" to "eq.$artistId")
            )
        }
    }

    suspend fun recordListening(trackId: String, progressSeconds: Int = 0): LibraryOperationResult {
        if (trackId.isBlank()) return LibraryOperationResult(false, "Select a valid track.")
        return request {
            val userId = requireUser()
            client.request(
                method = "POST",
                path = "/rest/v1/listening_history",
                body = JSONObject()
                    .put("user_id", userId)
                    .put("track_id", trackId)
                    .toString(),
                headers = mapOf("Prefer" to "return=minimal")
            )
        }
    }

    suspend fun listeningHistory(limit: Int = 50): Result<List<String>> = runCatching {
        val userId = requireUser()
        val response = client.get(
            table = "listening_history",
            query = mapOf(
                "user_id" to "eq.$userId",
                "order" to "played_at.desc",
                "limit" to limit.coerceIn(1, 100).toString()
            ),
            select = "track_id"
        )
        check(response.isSuccessful) { "Could not load listening history (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.optString("track_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }
    }

    suspend fun playlists(): Result<List<NativePlaylist>> = runCatching {
        val userId = requireUser()
        val response = client.get(
            table = "playlists",
            query = mapOf("user_id" to "eq.$userId", "order" to "updated_at.desc", "limit" to "100"),
            select = "id,name,description"
        )
        check(response.isSuccessful) { "Could not load playlists (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                add(NativePlaylist(id, row.optString("name"), row.optString("description")))
            }
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Result<NativePlaylist> = runCatching {
        val cleanName = name.trim()
        require(cleanName.length in 1..80) { "Playlist name must be between 1 and 80 characters." }
        val userId = requireUser()
        val response = client.request(
            method = "POST",
            path = "/rest/v1/playlists",
            body = JSONObject().put("user_id", userId)
                .put("name", cleanName)
                .put("description", description.trim().take(500))
                .toString(),
            headers = mapOf("Prefer" to "return=representation")
        )
        check(response.isSuccessful) { "Could not create playlist (HTTP ${response.statusCode})." }
        val row = JSONArray(response.body).optJSONObject(0)
            ?: error("The server did not return the created playlist.")
        NativePlaylist(row.getString("id"), row.getString("name"), row.optString("description"))
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, position: Int = 0): LibraryOperationResult {
        if (playlistId.isBlank() || trackId.isBlank()) return LibraryOperationResult(false, "Select a valid playlist and track.")
        return request {
            requireUser()
            client.request(
                method = "POST",
                path = "/rest/v1/playlist_tracks",
                query = mapOf("on_conflict" to "playlist_id,track_id"),
                body = JSONObject().put("playlist_id", playlistId)
                    .put("track_id", trackId)
                    .put("position", position.coerceAtLeast(0))
                    .toString(),
                headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }
    }

    suspend fun playlistTrackIds(playlistId: String): Result<List<String>> = runCatching {
        requireUser()
        val response = client.get("playlist_tracks",
            mapOf("playlist_id" to "eq.$playlistId", "order" to "position.asc", "limit" to "500"),
            "track_id")
        check(response.isSuccessful) { "Could not load playlist tracks (HTTP ${response.statusCode})." }
        val rows = JSONArray(response.body)
        buildList {
            for (i in 0 until rows.length()) {
                rows.optJSONObject(i)?.optString("track_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): LibraryOperationResult =
        request {
            requireUser()
            client.request("DELETE", "/rest/v1/playlist_tracks",
                mapOf("playlist_id" to "eq.$playlistId", "track_id" to "eq.$trackId"))
        }

    /** Atomically rewrites playlist positions using one PostgREST upsert request. */
    suspend fun reorderPlaylist(playlistId: String, orderedTrackIds: List<String>): LibraryOperationResult {
        if (playlistId.isBlank() || orderedTrackIds.any(String::isBlank) ||
            orderedTrackIds.distinct().size != orderedTrackIds.size
        ) return LibraryOperationResult(false, "The playlist order is invalid.")
        return request {
            requireUser()
            val rows = JSONArray()
            orderedTrackIds.forEachIndexed { index, trackId ->
                rows.put(JSONObject()
                    .put("playlist_id", playlistId)
                    .put("track_id", trackId)
                    .put("position", index))
            }
            client.request(
                method = "POST",
                path = "/rest/v1/playlist_tracks",
                query = mapOf("on_conflict" to "playlist_id,track_id"),
                body = rows.toString(),
                headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }
    }

    suspend fun removePlaylist(playlistId: String): LibraryOperationResult = request {
        val userId = requireUser()
        client.request("DELETE", "/rest/v1/playlists",
            mapOf("id" to "eq.$playlistId", "user_id" to "eq.$userId"))
    }

    suspend fun submitBooking(
        name: String,
        email: String,
        bookingType: String,
        requestedDate: String?,
        message: String
    ): LibraryOperationResult {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val cleanType = bookingType.trim()
        if (cleanName.length !in 1..100 || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() ||
            cleanType.length !in 2..80 || message.length > 3000
        ) return LibraryOperationResult(false, "Check the booking details and try again.")
        return request {
            val userId = requireUser()
            val body = JSONObject()
                .put("user_id", userId)
                .put("name", cleanName)
                .put("email", cleanEmail)
                .put("booking_type", cleanType)
                .put("message", message.trim())
            requestedDate?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let {
                body.put("requested_date", it)
            }
            client.request(
                method = "POST",
                path = "/rest/v1/bookings",
                body = body.toString(),
                headers = mapOf("Prefer" to "return=minimal")
            )
        }
    }

    private fun requireUser(): String =
        currentUserId()?.takeIf { it.isNotBlank() }
            ?: error("Sign in to use your personal library.")

    private suspend fun request(call: suspend () -> SupabaseRestClient.Response): LibraryOperationResult =
        try {
            val response = call()
            if (response.isSuccessful) LibraryOperationResult(true)
            else LibraryOperationResult(false, "Request failed (HTTP ${response.statusCode}).")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            LibraryOperationResult(false, "Unable to reach your library. Check your connection and retry.")
        }
}
