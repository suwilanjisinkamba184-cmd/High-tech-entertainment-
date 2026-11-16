package com.hightechentertainment.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.UploadData
import io.ktor.http.ContentType
import io.ktor.client.call.body
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import java.util.UUID

@Serializable
data class FounderTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FounderVideoDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val artist: String? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class TrackInsertDto(
    val title: String,
    val artist: String,
    val genre: String? = null,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false
)

@Serializable
private data class VideoInsertDto(
    val title: String,
    val description: String? = null,
    val artist: String? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false
)

@Serializable
data class FounderArtistDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    val bio: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FounderReleaseDto(
    val id: String,
    val title: String,
    val slug: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("spotify_url") val spotifyUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FounderInboxMessageDto(
    val id: String,
    val name: String,
    val email: String,
    val subject: String? = null,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FounderAudioAdDto(
    val id: String,
    val title: String,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FounderMarketingCampaignDto(
    val id: String,
    val title: String,
    @SerialName("campaign_type") val campaignType: String,
    val status: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val content: JsonObject? = null
)

@Serializable
data class FounderSocialConnectionDto(
    val platform: String,
    @SerialName("account_label") val accountLabel: String? = null,
    @SerialName("connection_status") val connectionStatus: String? = null,
    @SerialName("granted_scopes") val grantedScopes: List<String>? = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null
)

@Serializable
data class FounderVoiceDto(
    @SerialName("voice_id") val voiceId: String,
    val name: String,
    val category: String? = null,
    @SerialName("available_for_tiers") val availableForTiers: List<String>? = null
)

@Serializable
private data class FounderVoicesResponse(val voices: List<FounderVoiceDto>? = null)

@Serializable
private data class FounderAdGenerationResponse(val message: String? = null)

@Serializable
private data class ArtistInsertDto(
    val name: String,
    val slug: String,
    val bio: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false
)

@Serializable
private data class ReleaseInsertDto(
    val title: String,
    val slug: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("spotify_url") val spotifyUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false
)

@Serializable
private data class MarketingCampaignInsertDto(
    val title: String,
    @SerialName("campaign_type") val campaignType: String,
    val status: String = "draft",
    @SerialName("created_by") val createdBy: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    val content: JsonObject
)

/** Owner-only content management. Both app checks and database RLS enforce the owner role. */
class FounderStudioRepository {
    private val client get() = SupabaseClientProvider.client

    private suspend fun requireOwner() {
        check(client.auth.currentUserOrNull() != null) { "Sign in to open Founder Studio." }
        val role = client.postgrest.rpc("hte_my_role").decodeAs<String>()
        check(role.equals("owner", ignoreCase = true)) { "Founder Studio is restricted to the owner account." }
    }

    suspend fun getTracks(): List<FounderTrackDto> {
        requireOwner()
        return client.from("tracks").select().decodeList<FounderTrackDto>()
    }

    suspend fun getVideos(): List<FounderVideoDto> {
        requireOwner()
        return client.from("videos").select().decodeList<FounderVideoDto>()
    }

    suspend fun uploadArtwork(input: InputStream, size: Long, mimeType: String, fileName: String): String {
        requireOwner()
        require(size in 1L..(20L * 1024L * 1024L)) { "Artwork files must be between 1 byte and 20 MB." }
        require(mimeType.substringBefore(';').trim().lowercase() in setOf("image/jpeg", "image/png", "image/webp")) {
            "Choose a JPG, PNG or WebP image."
        }
        val path = "covers/${UUID.randomUUID()}_${safeName(fileName)}"
        return uploadStream("hte-artwork", path, input, size, mimeType)
    }

    suspend fun uploadTrack(
        title: String,
        artist: String,
        genre: String,
        input: InputStream,
        size: Long,
        mimeType: String,
        coverUrl: String? = null
    ) {
        requireOwner()
        require(title.isNotBlank()) { "Enter a track title." }
        require(size > 0L && size <= 100L * 1024L * 1024L) { "Audio files must be between 1 byte and 100 MB." }
        val extension = extensionFor(mimeType, "mp3")
        val path = "tracks/${UUID.randomUUID()}.$extension"
        val audioUrl = uploadStream("hte-audio", path, input, size, mimeType)
        try {
            client.from("tracks").insert(
                TrackInsertDto(title.trim(), artist.trim().ifBlank { "High-Tech Entertainment" }, genre.trim().takeIf { it.isNotEmpty() }, audioUrl, coverUrl, false)
            )
        } catch (error: Throwable) {
            runCatching { client.storage.from("hte-audio").delete(path) }
            throw error
        }
    }

    suspend fun uploadVideo(
        title: String,
        artist: String,
        description: String,
        input: InputStream,
        size: Long,
        mimeType: String,
        thumbnailUrl: String? = null
    ) {
        requireOwner()
        require(title.isNotBlank()) { "Enter a video title." }
        require(size > 0L && size <= 500L * 1024L * 1024L) { "Video files must be between 1 byte and 500 MB." }
        val extension = extensionFor(mimeType, "mp4")
        val path = "videos/${UUID.randomUUID()}.$extension"
        val videoUrl = uploadStream("hte-videos", path, input, size, mimeType)
        try {
            client.from("videos").insert(
                VideoInsertDto(title.trim(), description.trim().takeIf { it.isNotEmpty() }, artist.trim().takeIf { it.isNotEmpty() }, videoUrl, thumbnailUrl, false)
            )
        } catch (error: Throwable) {
            runCatching { client.storage.from("hte-videos").delete(path) }
            throw error
        }
    }

    suspend fun setTrackPublished(trackId: String, published: Boolean) {
        requireOwner()
        client.from("tracks").update({ set("is_published", published) }) {
            filter { eq("id", trackId) }
        }
    }

    suspend fun setVideoPublished(videoId: String, published: Boolean) {
        requireOwner()
        client.from("videos").update({ set("is_published", published) }) {
            filter { eq("id", videoId) }
        }
    }

    suspend fun deleteTrack(track: FounderTrackDto) {
        requireOwner()
        // Retain the database row whenever any Storage cleanup fails, so the owner can retry.
        storagePath(track.coverUrl, "hte-artwork")?.let { deleteStorageObject("hte-artwork", it, "track artwork") }
        storagePath(track.audioUrl, "hte-audio")?.let { deleteStorageObject("hte-audio", it, "track audio") }
        client.from("tracks").delete { filter { eq("id", track.id) } }
    }

    suspend fun deleteVideo(video: FounderVideoDto) {
        requireOwner()
        storagePath(video.thumbnailUrl, "hte-artwork")?.let { deleteStorageObject("hte-artwork", it, "video thumbnail") }
        storagePath(video.videoUrl, "hte-videos")?.let { deleteStorageObject("hte-videos", it, "video file") }
        client.from("videos").delete { filter { eq("id", video.id) } }
    }

    fun currentOwnerEmail(): String? = client.auth.currentUserOrNull()?.email

    suspend fun getArtists(): List<FounderArtistDto> {
        requireOwner()
        return client.from("artists").select().decodeList<FounderArtistDto>()
    }

    suspend fun createArtist(name: String, bio: String, imageUrl: String?) {
        requireOwner()
        require(name.isNotBlank()) { "Enter an artist name." }
        val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        client.from("artists").insert(ArtistInsertDto(name.trim(), slug, bio.trim().takeIf(String::isNotEmpty), imageUrl, false))
    }

    suspend fun setArtistPublished(artistId: String, published: Boolean) {
        requireOwner()
        client.from("artists").update({ set("is_published", published) }) { filter { eq("id", artistId) } }
    }

    suspend fun deleteArtist(artist: FounderArtistDto) {
        requireOwner()
        storagePath(artist.imageUrl, "hte-artwork")?.let { deleteStorageObject("hte-artwork", it, "artist artwork") }
        client.from("artists").delete { filter { eq("id", artist.id) } }
    }

    suspend fun getReleases(): List<FounderReleaseDto> {
        requireOwner()
        return client.from("releases").select().decodeList<FounderReleaseDto>()
    }

    suspend fun createRelease(title: String, releaseDate: String, spotifyUrl: String, coverUrl: String?) {
        requireOwner()
        require(title.isNotBlank()) { "Enter a release title." }
        val slug = title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        client.from("releases").insert(
            ReleaseInsertDto(title.trim(), slug, coverUrl, releaseDate.trim().takeIf(String::isNotEmpty), spotifyUrl.trim().takeIf(String::isNotEmpty), false)
        )
    }

    suspend fun setReleasePublished(releaseId: String, published: Boolean) {
        requireOwner()
        client.from("releases").update({ set("is_published", published) }) { filter { eq("id", releaseId) } }
    }

    suspend fun deleteRelease(release: FounderReleaseDto) {
        requireOwner()
        storagePath(release.coverUrl, "hte-artwork")?.let { deleteStorageObject("hte-artwork", it, "release artwork") }
        client.from("releases").delete { filter { eq("id", release.id) } }
    }

    suspend fun getInboxMessages(): List<FounderInboxMessageDto> {
        requireOwner()
        return client.from("contact_messages").select().decodeList<FounderInboxMessageDto>()
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun getAudioAds(): List<FounderAudioAdDto> {
        requireOwner()
        return client.from("audio_ads").select().decodeList<FounderAudioAdDto>()
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun getAdVoices(): List<FounderVoiceDto> {
        requireOwner()
        val response = client.functions.invoke(
            function = "elevenlabs-ad-studio",
            body = buildJsonObject { put("action", "voices") }
        )
        return response.body<FounderVoicesResponse>().voices.orEmpty().filter { "free" in it.availableForTiers.orEmpty() }
    }

    suspend fun generateAudioAd(title: String, script: String, voiceId: String, modelId: String): String {
        requireOwner()
        require(title.isNotBlank() && script.isNotBlank() && voiceId.isNotBlank()) { "Complete the title, script and presenter voice." }
        require(script.length <= 2500) { "Keep the advertisement script under 2,500 characters." }
        val response = client.functions.invoke(
            function = "elevenlabs-ad-studio",
            body = buildJsonObject {
                put("action", "generate")
                put("title", title.trim())
                put("script", script.trim())
                put("voice_id", voiceId)
                put("model_id", modelId)
            }
        )
        return response.body<FounderAdGenerationResponse>().message
            ?: "Voice advertisement generated and saved as an unpublished draft."
    }

    suspend fun setAudioAdPublished(adId: String, published: Boolean) {
        requireOwner()
        client.from("audio_ads").update({ set("is_published", published) }) { filter { eq("id", adId) } }
    }

    suspend fun deleteAudioAd(adId: String) {
        requireOwner()
        client.from("audio_ads").delete { filter { eq("id", adId) } }
    }

    suspend fun getSocialConnections(): List<FounderSocialConnectionDto> {
        requireOwner()
        return client.from("hte_social_connections").select().decodeList<FounderSocialConnectionDto>()
            .sortedBy { it.platform }
    }

    suspend fun getMarketingCampaigns(): List<FounderMarketingCampaignDto> {
        requireOwner()
        return client.from("hte_marketing_campaigns").select().decodeList<FounderMarketingCampaignDto>()
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun saveMarketingCampaignDraft(title: String, message: String, campaignType: String, channels: List<String>) {
        requireOwner()
        require(title.isNotBlank() && message.isNotBlank()) { "Enter a campaign title and message." }
        require(channels.isNotEmpty()) { "Select at least one campaign channel." }
        val userId = client.auth.currentUserOrNull()?.id ?: error("Sign in to save a campaign draft.")
        val content = buildJsonObject {
            put("message", message.trim())
            put("channels", JsonArray(channels.filter { it != "whatsapp_status" }.map { JsonPrimitive(it) }))
            put("whatsapp_status", JsonPrimitive("whatsapp_status" in channels))
            put("brand", "High-Tech Entertainment")
            put("tagline", "Music. Beats. Production. Beyond.")
        }
        client.from("hte_marketing_campaigns").insert(
            MarketingCampaignInsertDto(title.trim(), campaignType, "draft", userId, null, content)
        )
    }

    private suspend fun deleteStorageObject(bucketId: String, path: String, description: String) {
        try {
            client.storage.from(bucketId).delete(path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException(
                "Could not delete $description from storage. The content record was kept; check the connection and retry.",
                error
            )
        }
    }

    private suspend fun uploadStream(bucketId: String, path: String, input: InputStream, size: Long, mimeType: String): String = coroutineScope {
        require(size > 0L) { "The selected file is empty." }
        val channel = ByteChannel(autoFlush = true)
        val producer = launch(Dispatchers.IO) {
            try {
                input.use { stream ->
                    val buffer = ByteArray(64 * 1024)
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
            client.storage.from(bucketId).upload(path, UploadData(channel, size)) {
                contentType = ContentType.parse(mimeType)
                upsert = false
            }
            producer.join()
            client.storage.from(bucketId).publicUrl(path)
        } catch (error: Throwable) {
            producer.cancel()
            channel.close()
            throw error
        }
    }

    private fun storagePath(url: String?, bucketId: String): String? {
        if (url.isNullOrBlank()) return null
        val marker = "/object/public/$bucketId/"
        return url.substringAfter(marker, "").takeIf { it.isNotBlank() }
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "media" }

    private fun extensionFor(mimeType: String, fallback: String): String = when (mimeType.substringBefore(';').trim().lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/flac" -> "flac"
        "audio/webm" -> "webm"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/mp4", "video/x-m4v" -> "mp4"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        else -> fallback
    }
}
