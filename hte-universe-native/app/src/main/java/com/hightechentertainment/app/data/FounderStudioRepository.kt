package com.hightechentertainment.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.UploadData
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        storagePath(track.audioUrl, "hte-audio")?.let { path -> runCatching { client.storage.from("hte-audio").delete(path) } }
        storagePath(track.coverUrl, "hte-artwork")?.let { path -> runCatching { client.storage.from("hte-artwork").delete(path) } }
        client.from("tracks").delete { filter { eq("id", track.id) } }
    }

    suspend fun deleteVideo(video: FounderVideoDto) {
        requireOwner()
        storagePath(video.videoUrl, "hte-videos")?.let { path -> runCatching { client.storage.from("hte-videos").delete(path) } }
        storagePath(video.thumbnailUrl, "hte-artwork")?.let { path -> runCatching { client.storage.from("hte-artwork").delete(path) } }
        client.from("videos").delete { filter { eq("id", video.id) } }
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
                channel.close(error)
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
            channel.close(error)
            throw error
        }
    }

    private fun storagePath(url: String?, bucketId: String): String? {
        if (url.isNullOrBlank()) return null
        val marker = "/object/public/$bucketId/"
        return url.substringAfter(marker, "").takeIf { it.isNotBlank() }
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "media" }

    private fun extensionFor(mimeType: String, fallback: String): String = when (mimeType.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/flac" -> "flac"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/mp4", "video/x-m4v" -> "mp4"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> fallback
    }
}
