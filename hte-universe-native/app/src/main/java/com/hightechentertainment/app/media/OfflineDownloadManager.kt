package com.hightechentertainment.app.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

 data class OfflineDownloadRecord(
    val id: Long,
    val title: String,
    val mediaType: String,
    val status: String,
    val localUri: String? = null
)

/** Uses Android DownloadManager and app-specific external storage; no broad storage permission is needed. */
class OfflineDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("hte_universe_offline_downloads", Context.MODE_PRIVATE)

    fun enqueue(url: String, title: String, mediaType: String, allowMetered: Boolean = true): Long {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "This media item does not have a valid download URL." }
        val safeName = title.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(64).ifBlank { "hte_media" }
        val extension = if (mediaType == "video") "mp4" else "mp3"
        val folder = if (mediaType == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription("Downloading from HTE Universe")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(allowMetered)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, folder, "$safeName.$extension")
        val id = manager.enqueue(request)
        preferences.edit().putString(id.toString(), "$mediaType|$title").apply()
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
            val statusCode = if (statusIndex >= 0) it.getInt(statusIndex) else DownloadManager.STATUS_FAILED
            val localUri = if (uriIndex >= 0) it.getString(uriIndex) else null
            val status = when (statusCode) {
                DownloadManager.STATUS_PENDING -> "Queued"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
                else -> "Failed"
            }
            OfflineDownloadRecord(id, title, mediaType, status, localUri)
        }
    }.sortedByDescending { it.id }

    fun clearAll() {
        val ids = preferences.all.keys.mapNotNull { it.toLongOrNull() }.toLongArray()
        if (ids.isNotEmpty()) manager.remove(*ids)
        preferences.edit().clear().apply()
    }

    fun remove(id: Long) {
        manager.remove(id)
        preferences.edit().remove(id.toString()).apply()
    }
}
