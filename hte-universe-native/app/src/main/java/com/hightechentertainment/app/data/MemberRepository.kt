package com.hightechentertainment.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class FavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class FavoriteInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String
)

@Serializable
private data class BookingInsertDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    val email: String,
    @SerialName("booking_type") val bookingType: String,
    @SerialName("requested_date") val requestedDate: String? = null,
    val message: String = ""
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
        val userId = requireUserId()
        return client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileDto>()
            .firstOrNull()
    }

    suspend fun saveMyProfile(displayName: String, bio: String, phone: String, avatarUrl: String = "") {
        val userId = requireUserId()
        client.from("profiles").upsert(
            ProfileDto(
                id = userId,
                displayName = displayName.trim(),
                bio = bio.trim(),
                phone = phone.trim(),
                avatarUrl = avatarUrl.trim(),
                updatedAt = java.time.Instant.now().toString()
            )
        )
    }

    suspend fun getMyBookings(): List<BookingDto> {
        val userId = requireUserId()
        return client.from("bookings")
            .select { filter { eq("user_id", userId) } }
            .decodeList<BookingDto>()
    }

    suspend fun submitBooking(
        name: String,
        email: String,
        bookingType: String,
        requestedDate: String?,
        message: String
    ) {
        val userId = requireUserId()
        require(name.isNotBlank()) { "Enter your name." }
        require(email.isNotBlank()) { "Enter your email address." }
        require(bookingType.isNotBlank()) { "Choose a booking type." }
        client.from("bookings").insert(
            BookingInsertDto(userId, name.trim(), email.trim(), bookingType, requestedDate, message.trim())
        )
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
