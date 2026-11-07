package com.hightechentertainment.app.playback

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.hightechentertainment.app.data.NativeTrack

/** System-managed app-private downloads; no broad storage permission is required. */
class OfflineMusicDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("hte_offline_downloads", Context.MODE_PRIVATE)

    fun enqueue(track: NativeTrack): Long? {
        val url = track.audioUrl?.takeIf { it.startsWith("https://") } ?: return null
        val existing = preferences.getLong(track.id, -1L)
        if (existing > 0L && isComplete(existing)) return existing
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.title)
            .setDescription("High-Tech Entertainment offline music")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MUSIC, "${track.id}.audio")
        val id = manager.enqueue(request)
        preferences.edit().putLong(track.id, id).apply()
        return id
    }

    fun localUri(trackId: String): Uri? {
        val id = preferences.getLong(trackId, -1L)
        if (id <= 0L || !isComplete(id)) return null
        return runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()
    }

    fun isComplete(id: Long): Boolean {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return false
        return cursor.use {
            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                DownloadManager.STATUS_SUCCESSFUL
        }
    }
}
