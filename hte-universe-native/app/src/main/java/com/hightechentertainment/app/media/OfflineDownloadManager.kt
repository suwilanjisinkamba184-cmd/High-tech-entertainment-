package com.hightechentertainment.app.media

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import java.net.URI
import java.net.URLConnection
import java.security.MessageDigest

data class OfflineDownloadRecord(
    val id: Long,
    val title: String,
    val mediaType: String,
    val status: String,
    val localUri: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val reason: Int? = null
) {
    val progressPercent: Int?
        get() = totalBytes.takeIf { it > 0L }?.let {
            ((downloadedBytes.toDouble() / it.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        }
}

internal fun isSecureMediaDownloadUrl(url: String): Boolean =
    runCatching { URI(url) }.getOrNull()?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } ?: false

internal fun resolveMediaFileExtension(url: String, mediaType: String, mimeType: String? = null): String {
    val mimeExtension = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
        "audio/aac", "audio/aacp" -> "aac"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/opus" -> "opus"
        "audio/webm", "video/webm" -> "webm"
        "video/mp4", "video/x-m4v" -> "mp4"
        "video/quicktime" -> "mov"
        "video/x-matroska" -> "mkv"
        "video/3gpp" -> "3gp"
        else -> null
    }
    if (mimeExtension != null) return mimeExtension
    val path = url.substringBefore('#').substringBefore('?')
    val candidate = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    if (candidate in setOf("mp3","m4a","aac","wav","ogg","flac","opus","webm","mp4","mov","mkv","3gp")) return candidate
    return if (mediaType.equals("video", true) || mediaType.startsWith("video/", true)) "mp4" else "mp3"
}

internal fun buildOfflineDownloadFileName(title: String, url: String, mediaType: String, mimeType: String? = null): String {
    val safeName = title.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(64).ifBlank { "hte_media" }
    val extension = resolveMediaFileExtension(url, mediaType, mimeType)
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "$safeName-$fingerprint.$extension"
}

class OfflineDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("hte_universe_offline_downloads", Context.MODE_PRIVATE)

    fun enqueue(url: String, title: String, mediaType: String, allowMetered: Boolean = true, mimeType: String? = null): Long {
        require(isSecureMediaDownloadUrl(url)) { "Downloads require a valid secure HTTPS media URL." }
        val uri = url.toUri()
        val fileName = buildOfflineDownloadFileName(title, url, mediaType, mimeType)
        val isVideo = mediaType.equals("video", true) || mediaType.startsWith("video/", true)
        val folder = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription("Downloading from HTE Universe")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(allowMetered)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, folder, fileName)
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: URLConnection.guessContentTypeFromName(fileName)
        if (!resolvedMime.isNullOrBlank()) request.setMimeType(resolvedMime)
        val id = manager.enqueue(request)
        preferences.edit { putString(id.toString(), "$mediaType|$title") }
        return id
    }

    fun getDownloads(): List<OfflineDownloadRecord> = preferences.all.mapNotNull { (key, value) ->
        val id = key.toLongOrNull() ?: return@mapNotNull null
        val metadata = value as? String ?: return@mapNotNull null
        val parts = metadata.split('|', limit = 2)
        val mediaType = parts.getOrNull(0) ?: "audio"
        val title = parts.getOrNull(1) ?: "HTE download"
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return@mapNotNull null
        cursor.use {
            if (!it.moveToFirst()) return@mapNotNull OfflineDownloadRecord(id, title, mediaType, "Missing")
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val downloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            val statusCode = if (statusIndex >= 0) it.getInt(statusIndex) else DownloadManager.STATUS_FAILED
            val localUri = if (uriIndex >= 0) it.getString(uriIndex) else null
            val downloadedBytes = if (downloadedIndex >= 0) it.getLong(downloadedIndex) else 0L
            val totalBytes = if (totalIndex >= 0) it.getLong(totalIndex) else -1L
            val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else null
            val status = when (statusCode) {
                DownloadManager.STATUS_PENDING -> "Queued"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
                else -> "Failed"
            }
            OfflineDownloadRecord(id, title, mediaType, status, localUri, downloadedBytes, totalBytes, reason)
        }
    }.sortedByDescending { it.id }

    fun clearAll() {
        val ids = preferences.all.keys.mapNotNull { it.toLongOrNull() }.toLongArray()
        if (ids.isNotEmpty()) manager.remove(*ids)
        preferences.edit { clear() }
    }
    fun remove(id: Long) {
        manager.remove(id)
        preferences.edit { remove(id.toString()) }
    }
}
