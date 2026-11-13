package com.hightechentertainment.app.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class HteUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val downloadSize: Long,
    val sha256: String?,
    val updateAvailable: Boolean
)

data class HteDownloadProgress(val downloaded: Long, val total: Long) {
    val fraction: Float get() = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class HteUpdateManager(private val context: Context) {
    private val updateDirectory = File(context.cacheDir, "updates")

    suspend fun checkForUpdate(currentVersion: String): HteUpdateInfo = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HTE-Universe-Android")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Update service returned HTTP $code." }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            // The repository still has a legacy Flutter/Shorebird release stream. Never
            // let that APK be offered as an update to the native Kotlin/Compose app.
            val nativeRelease = (0 until releases.length())
                .map { releases.getJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(NATIVE_RELEASE_TAG_PREFIX) }
                .reduceOrNull { best, candidate ->
                    val bestVersion = best.optString("tag_name").removePrefix(NATIVE_RELEASE_TAG_PREFIX)
                    val candidateVersion = candidate.optString("tag_name").removePrefix(NATIVE_RELEASE_TAG_PREFIX)
                    if (compareVersions(candidateVersion, bestVersion) > 0) candidate else best
                }
                ?: throw IllegalStateException("No published native HTE release is available yet.")
            val tag = nativeRelease.optString("tag_name")
            val latest = tag.removePrefix(NATIVE_RELEASE_TAG_PREFIX).trim()
            require(latest.isNotBlank()) { "The native release service returned no version." }
            val assets = nativeRelease.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            var digest: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true) && name.contains("HTE", ignoreCase = true).or(name.contains("High-Tech", ignoreCase = true))) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        apkSize = asset.optLong("size", 0L)
                        digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                        break
                    }
                }
            }
            HteUpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latest,
                releaseNotes = nativeRelease.optString("body").take(6000),
                downloadUrl = apkUrl,
                downloadSize = apkSize,
                sha256 = digest,
                updateAvailable = compareVersions(latest, currentVersion) > 0
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadUpdate(
        url: String,
        expectedSize: Long = 0L,
        expectedSha256: String? = null,
        onProgress: (HteDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Updates must be downloaded over HTTPS." }
        updateDirectory.mkdirs()
        val partial = File(updateDirectory, "HTE-Universe-update.apk.part")
        val complete = File(updateDirectory, "HTE-Universe-update.apk")
        var offset = if (partial.exists()) partial.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HTE-Universe-Android")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val response = connection.responseCode
            require(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) {
                "Download interrupted by the server (HTTP $response). Your partial download is kept so you can retry."
            }
            val append = offset > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (!append) {
                offset = 0L
                partial.delete()
            }
            val responseLength = connection.getHeaderFieldLong("Content-Length", -1L)
            val total = if (append && responseLength > 0L) offset + responseLength else if (expectedSize > 0L) expectedSize else responseLength
            var downloaded = offset
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(HteDownloadProgress(downloaded, total))
                    }
                    output.fd.sync()
                }
            }
            require(partial.exists() && partial.length() > 0L) { "The update download was empty." }
            if (expectedSize > 0L) require(partial.length() == expectedSize) {
                "The downloaded file is incomplete. Resume the download to continue."
            }
            if (!expectedSha256.isNullOrBlank()) {
                val actual = sha256(partial)
                require(actual.equals(expectedSha256, ignoreCase = true)) { "Update integrity check failed. The downloaded file was rejected." }
            }
            if (complete.exists()) complete.delete()
            check(partial.renameTo(complete)) { "The update was downloaded but could not be finalised." }
            complete
        } finally {
            connection.disconnect()
        }
    }

    fun cachedUpdate(): File? = File(updateDirectory, "HTE-Universe-update.apk").takeIf { it.exists() && it.length() > 0L }
    fun partialDownloadSize(): Long = File(updateDirectory, "HTE-Universe-update.apk.part").takeIf { it.exists() }?.length() ?: 0L
    fun clearPartialDownload() { File(updateDirectory, "HTE-Universe-update.apk.part").delete() }
    fun clearCachedUpdate() { File(updateDirectory, "HTE-Universe-update.apk").delete(); clearPartialDownload() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String) = Regex("\\d+").findAll(value.substringBefore('-')).map { it.value.toIntOrNull() ?: 0 }.toList()
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private const val NATIVE_RELEASE_TAG_PREFIX = "native-v"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases?per_page=100"
    }
}
