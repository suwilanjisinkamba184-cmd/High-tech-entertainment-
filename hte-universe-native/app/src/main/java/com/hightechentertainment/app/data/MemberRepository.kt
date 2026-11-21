package com.hightechentertainment.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.UploadData
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val bio: String = "",
    val phone: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("updated_at") val updatedAt: String = java.time.Instant.now().toString()
)

@Serializable
data class BookingDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val email: String,
    @SerialName("booking_type") val bookingType: String,
    @SerialName("requested_date") val requestedDate: String? = null,
    val message: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val body: String,
    val category: String = "announcement",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PlaylistDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PlaylistTrackDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_id") val trackId: String,
    val position: Int = 0
)

@Serializable
data class FavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class AvatarProfileInsertDto(
    val id: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class FavoriteInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String
)

@Serializable
private data class MemberBookingMessageDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val email: String,
    val subject: String = "",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class ListeningHistoryDto(
    val id: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("played_at") val playedAt: String,
    val completed: Boolean = false
)

@Serializable
private data class ListeningHistoryInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val completed: Boolean = false
)

/**
 * Member-facing operations use the signed-in user's JWT. Supabase RLS remains
 * authoritative for profile, booking, favourites, playlists and history access.
 */
class MemberRepository {
    private val client get() = SupabaseClientProvider.client

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: error("Sign in to use your HTE account.")

    suspend fun getMyProfile(): ProfileDto? {
        val user = client.auth.currentUserOrNull()
            ?: error("Sign in to use your HTE account.")
        val userId = user.id
        val storedProfile = client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileDto>()
            .firstOrNull()

        // Google OAuth metadata carries the provider photo even when the member
        // has never uploaded an HTE avatar. Prefer a saved HTE avatar when present.
        val metadata = user.userMetadata
        fun metadataText(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key ->
                metadata?.get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            }
        val googleAvatar = metadataText("avatar_url", "picture", "photo_url", "photoUrl")
            ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
        val googleName = metadataText("full_name", "name", "given_name")

        return when {
            storedProfile != null -> {
                val storedAvatar = storedProfile.avatarUrl.ifBlank { googleAvatar.orEmpty() }
                val resolvedAvatar = when {
                    storedAvatar.isBlank() -> ""
                    storedAvatar.startsWith("https://", ignoreCase = true) -> storedAvatar
                    else -> runCatching {
                        client.storage["hte-avatars"].createSignedUrl(path = storedAvatar, expiresIn = 60.minutes)
                    }.getOrDefault("")
                }
                storedProfile.copy(
                    displayName = storedProfile.displayName.ifBlank { googleName.orEmpty() },
                    avatarUrl = resolvedAvatar
                )
            }
            googleAvatar != null || googleName != null -> ProfileDto(
                id = userId,
                displayName = googleName.orEmpty(),
                avatarUrl = googleAvatar.orEmpty()
            )
            else -> null
        }
    }

    suspend fun updateMyAvatar(input: InputStream, size: Long, mimeType: String): ProfileDto? = coroutineScope {
        val userId = requireUserId()
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
        require(size in 1L..(5L * 1024L * 1024L)) { "Profile photos must be 5 MB or smaller." }
        val extension = when (normalizedMime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("Choose a JPG, PNG or WebP profile photo.")
        }
        val existing = client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileDto>()
            .firstOrNull()
        val path = "$userId/${UUID.randomUUID()}.$extension"
        val channel = ByteChannel(autoFlush = true)
        val producer = launch(Dispatchers.IO) {
            try {
                input.use { stream ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        channel.writeFully(buffer, 0, count)
                    }
                }
                channel.close()
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }
        try {
            client.storage.from("hte-avatars").upload(path, UploadData(channel, size)) {
                contentType = ContentType.parse(normalizedMime)
                upsert = false
            }
            producer.join()
            try {
                val updatedAt = java.time.Instant.now().toString()
                if (existing != null) {
                    // Do not write stale profile fields captured before the upload.
                    client.from("profiles").update({
                        set("avatar_url", path)
                        set("updated_at", updatedAt)
                    }) {
                        filter { eq("id", userId) }
                    }
                } else {
                    // Insert only avatar fields. If another session creates the row
                    // concurrently, retry as an avatar-only update instead of upserting
                    // stale/default profile fields over that session's data.
                    try {
                        client.from("profiles").insert(AvatarProfileInsertDto(userId, path, updatedAt))
                    } catch (insertError: CancellationException) {
                        throw insertError
                    } catch (insertError: Exception) {
                        val isUniqueViolation =
                            (insertError as? PostgrestRestException)?.code == "23505" ||
                                insertError.message.orEmpty().contains("23505")
                        if (!isUniqueViolation) throw insertError

                        // A concurrent session may have inserted the profile after our
                        // initial read. Retry only for the unique-key conflict and require
                        // the returning update to prove that the row was actually changed.
                        val updatedRows = client.from("profiles").update({
                            set("avatar_url", path)
                            set("updated_at", updatedAt)
                        }) {
                            select()
                            filter { eq("id", userId) }
                        }.decodeList<ProfileDto>()
                        if (updatedRows.isEmpty()) throw insertError
                    }
                }
            } catch (error: Throwable) {
                runCatching { client.storage.from("hte-avatars").delete(path) }
                throw error
            }
            getMyProfile()
        } catch (error: Throwable) {
            producer.cancel()
            channel.close()
            throw error
        }
    }

    suspend fun saveMyProfile(displayName: String, bio: String, phone: String, avatarUrl: String? = null) {
        val userId = requireUserId()
        val storedAvatar = if (avatarUrl == null) {
            client.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeList<ProfileDto>()
                .firstOrNull()?.avatarUrl.orEmpty()
                .ifBlank { getMyProfile()?.avatarUrl.orEmpty() }
        } else avatarUrl.trim()
        client.from("profiles").upsert(
            ProfileDto(
                id = userId,
                displayName = displayName.trim(),
                bio = bio.trim(),
                phone = phone.trim(),
                avatarUrl = storedAvatar,
                updatedAt = java.time.Instant.now().toString()
            )
        )
    }

    suspend fun getMyBookings(): List<BookingDto> {
        val userId = requireUserId()
        val legacyBookings = client.from("bookings")
            .select { filter { eq("user_id", userId) } }
            .decodeList<BookingDto>()
        val memberMessages = client.from("contact_messages")
            .select { filter { eq("user_id", userId) } }
            .decodeList<MemberBookingMessageDto>()
            .filter { it.subject.startsWith("Booking request", ignoreCase = true) }
            .map { message ->
                val service = message.subject.substringAfter("Booking request").trim()
                    .trimStart('—', '-')
                    .ifBlank { "Service request" }
                val requestedDate = Regex("""(?m)^Preferred date:\s*(.+)$""")
                    .find(message.message)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeUnless { it.isBlank() || it.equals("Flexible", ignoreCase = true) }
                BookingDto(
                    id = message.id,
                    userId = message.userId ?: userId,
                    name = message.name,
                    email = message.email,
                    bookingType = service,
                    requestedDate = requestedDate,
                    message = message.message,
                    status = "pending",
                    createdAt = message.createdAt
                )
            }
        return (legacyBookings + memberMessages)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun submitBooking(
        name: String,
        email: String,
        bookingType: String,
        requestedDate: String?,
        message: String
    ) {
        requireUserId()
        require(name.isNotBlank()) { "Enter your name." }
        require(email.isNotBlank()) { "Enter your email address." }
        require(bookingType.isNotBlank()) { "Choose a booking type." }
        val bookingMessage = listOf(
            "Service: ${bookingType.trim()}",
            "Preferred date: ${requestedDate?.takeIf(String::isNotBlank) ?: "Flexible"}",
            "Preferred time: Flexible",
            "Phone / WhatsApp: Not provided",
            "",
            "Project details:",
            message.trim()
        ).joinToString("\n")
        client.postgrest.rpc("submit_booking_request", buildJsonObject {
            put("p_name", name.trim())
            put("p_email", email.trim())
            put("p_subject", "Booking request — ${bookingType.trim()}")
            put("p_message", bookingMessage)
        })
    }

    suspend fun getMyPlaylists(): List<PlaylistDto> {
        val userId = requireUserId()
        return client.from("playlists")
            .select { filter { eq("user_id", userId) } }
            .decodeList<PlaylistDto>()
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getPlaylistTrackIds(playlistId: String): List<String> {
        requireUserId()
        return client.from("playlist_tracks")
            .select { filter { eq("playlist_id", playlistId) } }
            .decodeList<PlaylistTrackDto>()
            .sortedBy { it.position }
            .map { it.trackId }
    }

    suspend fun getFavouriteTrackIds(): Set<String> {
        val userId = requireUserId()
        return client.from("user_favorites")
            .select { filter { eq("user_id", userId) } }
            .decodeList<FavoriteDto>()
            .map { it.trackId }
            .toSet()
    }

    suspend fun addFavourite(trackId: String) {
        val userId = requireUserId()
        client.from("user_favorites").insert(FavoriteInsertDto(userId, trackId))
    }

    suspend fun removeFavourite(trackId: String) {
        val userId = requireUserId()
        client.from("user_favorites").delete {
            filter {
                eq("user_id", userId)
                eq("track_id", trackId)
            }
        }
    }

    suspend fun recordListening(trackId: String, completed: Boolean = false) {
        val userId = requireUserId()
        client.from("listening_history").insert(ListeningHistoryInsertDto(userId, trackId, completed))
    }

    suspend fun getMyListeningTrackIds(): List<String> {
        val userId = requireUserId()
        return client.from("listening_history")
            .select { filter { eq("user_id", userId) } }
            .decodeList<ListeningHistoryDto>()
            .sortedByDescending { it.playedAt }
            .distinctBy { it.trackId }
            .take(20)
            .map { it.trackId }
    }

    /** Broadcast and member-specific visibility is enforced by the live RLS policy. */
    suspend fun getNotifications(): List<NotificationDto> =
        client.from("app_notifications").select().decodeList<NotificationDto>()

    /** Role is returned by a server-side security function, never inferred from a profile field. */
    suspend fun getCurrentRole(): String =
        client.postgrest.rpc("hte_my_role").decodeAs<String>()

    suspend fun isHteAdmin(): Boolean =
        client.postgrest.rpc("hte_is_admin").decodeAs<Boolean>()

    suspend fun isFounderOwner(): Boolean = getCurrentRole().equals("owner", ignoreCase = true)
}
