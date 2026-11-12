package com.hightechentertainment.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.eq

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    val slug: String,
    val bio: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class ReleaseDto(
    val id: String,
    val title: String,
    val slug: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val description: String? = null
)

@Serializable
data class VideoDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val artist: String? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

/** Published catalogue queries only. Supabase RLS remains the source of truth for access. */
class EntertainmentCatalogRepository {
    suspend fun getPublishedArtists(): List<ArtistDto> =
        SupabaseClientProvider.client.from("artists")
            .select { filter { eq("is_published", true) } }
            .decodeList<ArtistDto>()

    suspend fun getPublishedReleases(): List<ReleaseDto> =
        SupabaseClientProvider.client.from("releases")
            .select { filter { eq("is_published", true) } }
            .decodeList<ReleaseDto>()

    suspend fun getPublishedVideos(): List<VideoDto> =
        SupabaseClientProvider.client.from("videos")
            .select { filter { eq("is_published", true) } }
            .decodeList<VideoDto>()
}
