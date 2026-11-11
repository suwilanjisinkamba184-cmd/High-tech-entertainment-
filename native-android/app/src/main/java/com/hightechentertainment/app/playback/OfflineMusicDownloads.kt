package com.hightechentertainment.app.playback

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.hightechentertainment.app.data.NativeTrack

enum class OfflineDownloadState { PENDING, RUNNING, PAUSED, COMPLETE, FAILED, UNKNOWN }

data class OfflineDownload(
    val trackId: String,
    val title: String,
    val downloadId: Long,
    val state: OfflineDownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int,
    val localUri: Uri?
) {
    val progressPercent: Int
        get() = if (totalBytes > 0L) ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
}

/**
 * System-managed app-private downloads. No broad storage permission is required.
 * DownloadManager owns transfer retries and network constraints; this class exposes
 * a stable app-level queue snapshot and explicit cancellation/removal operations.
 */
class OfflineMusicDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("hte_offline_downloads", Context.MODE_PRIVATE)

    fun enqueue(track: NativeTrack): Long? {
        val url = track.audioUrl?.takeIf { it.startsWith("https://") } ?: return null
        val existing = preferences.getLong(track.id, -1L)
        if (existing > 0L) {
            val snapshot = query(existing, track.id, track.title)
            if (snapshot?.state == OfflineDownloadState.COMPLETE ||
                snapshot?.state == OfflineDownloadState.PENDING ||
                snapshot?.state == OfflineDownloadState.RUNNING ||
                snapshot?.state == OfflineDownloadState.PAUSED
            ) return existing
            manager.remove(existing)
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.title)
            .setDescription("High-Tech Entertainment offline music")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MUSIC, "${track.id}.audio")
        val id = manager.enqueue(request)
        preferences.edit()
            .putLong(track.id, id)
            .putString(titleKey(track.id), track.title)
            .apply()
        return id
    }

    fun downloads(): List<OfflineDownload> =
        preferences.all.entries.mapNotNull { (key, value) ->
            if (key.startsWith(TITLE_PREFIX) || value !is Long || value <= 0L) return@mapNotNull null
            val title = preferences.getString(titleKey(key), key) ?: key
            query(value, key, title)
        }.sortedByDescending { it.downloadId }

    fun download(trackId: String): OfflineDownload? {
        val id = preferences.getLong(trackId, -1L)
        if (id <= 0L) return null
        return query(id, trackId, preferences.getString(titleKey(trackId), trackId) ?: trackId)
    }

    fun remove(trackId: String): Boolean {
        val id = preferences.getLong(trackId, -1L)
        if (id <= 0L) return false
        manager.remove(id)
        preferences.edit().remove(trackId).remove(titleKey(trackId)).apply()
        return true
    }

    fun localUri(trackId: String): Uri? =
        download(trackId)?.takeIf { it.state == OfflineDownloadState.COMPLETE }?.localUri

    fun downloadedBytes(): Long = downloads()
        .filter { it.state == OfflineDownloadState.COMPLETE }
        .sumOf { it.totalBytes.coerceAtLeast(0L) }

    fun isComplete(id: Long): Boolean =
        query(id, "", "")?.state == OfflineDownloadState.COMPLETE

    private fun query(id: Long, trackId: String, title: String): OfflineDownload? {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val state = when (status) {
                DownloadManager.STATUS_PENDING -> OfflineDownloadState.PENDING
                DownloadManager.STATUS_RUNNING -> OfflineDownloadState.RUNNING
                DownloadManager.STATUS_PAUSED -> OfflineDownloadState.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> OfflineDownloadState.COMPLETE
                DownloadManager.STATUS_FAILED -> OfflineDownloadState.FAILED
                else -> OfflineDownloadState.UNKNOWN
            }
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            OfflineDownload(
                trackId = trackId,
                title = title,
                downloadId = id,
                state = state,
                bytesDownloaded = downloaded.coerceAtLeast(0L),
                totalBytes = total.coerceAtLeast(0L),
                reason = reason,
                localUri = if (state == OfflineDownloadState.COMPLETE) {
                    runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()
                } else null
            )
        }
    }

    private fun titleKey(trackId: String) = TITLE_PREFIX + trackId

    private companion object {
        const val TITLE_PREFIX = "title:"
    }
}
