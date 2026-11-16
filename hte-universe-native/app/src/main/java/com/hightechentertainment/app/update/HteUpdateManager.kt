package com.hightechentertainment.app.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private class RetryableUpdateCheckException(message: String) : java.io.IOException(message)

class HteUpdateManager(private val context: Context) {
    private val updateDirectory = File(context.filesDir, "updates")

    suspend fun checkForUpdate(currentVersion: String): HteUpdateInfo {
        var lastNetworkError: java.io.IOException? = null
        for (attempt in 0..2) {
            currentCoroutineContext().ensureActive()
            try {
                return checkForUpdateOnce(currentVersion)
            } catch (error: java.io.IOException) {
                lastNetworkError = error
                if (attempt == 2 || (!error.isTransientUpdateNetworkError() && error !is RetryableUpdateCheckException)) throw error
                delay(750L * (attempt + 1))
            }
        }
        throw lastNetworkError ?: java.io.IOException("The update check could not reach GitHub. Please retry.")
    }

    private suspend fun checkForUpdateOnce(currentVersion: String): HteUpdateInfo = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HTE-Universe-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            if (code == 408 || code == 429 || code in 500..599) {
                throw RetryableUpdateCheckException(httpError(connection, code, "Update check"))
            }
            if (code !in 200..299) throw IllegalStateException(httpError(connection, code, "Update check"))
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
        canDownload: () -> Boolean = { true },
        onProgress: (HteDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Updates must be downloaded over HTTPS." }
        updateDirectory.mkdirs()
        val partial = File(updateDirectory, "HTE-Universe-update.apk.part")
        val complete = File(updateDirectory, "HTE-Universe-update.apk")
        var lastNetworkError: java.io.IOException? = null
        // Keep partial bytes and resume with HTTP Range after transient disconnects.
        for (attempt in 0..3) {
            currentCoroutineContext().ensureActive()
            check(canDownload()) { "Update download paused because the Wi-Fi-only policy blocks the active metered connection. Your partial download is preserved." }
            val offset = if (partial.exists()) partial.length() else 0L
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HTE-Universe-Android")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                val response = connection.responseCode
                if (response == 416 && offset > 0L) {
                    partial.delete()
                    lastNetworkError = java.io.IOException("Saved update range rejected; restarting from byte zero.")
                    continue
                }
                if (response == 408 || response == 429 || response in 500..599) {
                    throw java.io.IOException(httpError(connection, response, "Update download"))
                }
                if (response != HttpURLConnection.HTTP_OK && response != HttpURLConnection.HTTP_PARTIAL) {
                    throw IllegalStateException(httpError(connection, response, "Update download"))
                }
                val append = offset > 0L && response == HttpURLConnection.HTTP_PARTIAL
                if (!append) partial.delete()
                val effectiveOffset = if (append) offset else 0L
                val responseLength = connection.getHeaderFieldLong("Content-Length", -1L)
                val total = if (append && responseLength > 0L) effectiveOffset + responseLength
                    else if (expectedSize > 0L) expectedSize else responseLength
                var downloaded = effectiveOffset
                connection.inputStream.use { input ->
                    FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            check(canDownload()) { "Update download paused because the Wi-Fi-only policy blocks the active metered connection. Your partial download is preserved." }
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
                if (expectedSize > 0L && partial.length() != expectedSize) {
                    throw java.io.IOException("Update download incomplete; the saved partial file will be resumed.")
                }
                if (!expectedSha256.isNullOrBlank()) {
                    if (!sha256(partial).equals(expectedSha256, ignoreCase = true)) {
                        partial.delete()
                        throw IllegalStateException("Update integrity check failed. The downloaded file was rejected.")
                    }
                }
                if (complete.exists()) complete.delete()
                check(partial.renameTo(complete)) { "The update was downloaded but could not be finalised." }
                return@withContext complete
            } catch (error: java.io.IOException) {
                lastNetworkError = error
                if (attempt >= 3) throw error
                delay(1_000L * (attempt + 1))
            } finally {
                connection.disconnect()
            }
        }
        throw lastNetworkError ?: java.io.IOException("The update could not be downloaded. Please retry.")
    }

    fun cachedUpdate(): File? = File(updateDirectory, "HTE-Universe-update.apk").takeIf { it.exists() && it.length() > 0L }

    /** Reject stale, malformed or foreign APKs before invoking Android's package installer. */
    fun isInstallableUpdate(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= 0L) return false
        val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = context.packageManager.getPackageInfo(context.packageName, signingFlags)
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        val installedCode = PackageInfoCompat.getLongVersionCode(installed)
        val archiveSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.signingInfo?.apkContentsSigners else archive.signatures
        val installedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.signingInfo?.apkContentsSigners else installed.signatures
        val sameSigningIdentity = !archiveSigners.isNullOrEmpty() && !installedSigners.isNullOrEmpty() &&
            archiveSigners.map { it.toCharsString() }.toSet() == installedSigners.map { it.toCharsString() }.toSet()
        archiveCode > installedCode && sameSigningIdentity
    }.getOrDefault(false)
    fun partialDownloadSize(): Long = File(updateDirectory, "HTE-Universe-update.apk.part").takeIf { it.exists() }?.length() ?: 0L
    fun clearPartialDownload() { File(updateDirectory, "HTE-Universe-update.apk.part").delete() }
    fun clearCachedUpdate() { File(updateDirectory, "HTE-Universe-update.apk").delete(); clearPartialDownload() }

    private fun java.io.IOException.isTransientUpdateNetworkError(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is java.net.UnknownHostException ||
                cause is java.net.SocketTimeoutException ||
                cause is java.net.ConnectException ||
                cause is java.net.NoRouteToHostException
            ) return true
            cause = cause.cause
        }
        return false
    }

    private fun httpError(connection: HttpURLConnection, code: Int, operation: String): String {
        val body = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
        val detail = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when (code) {
            403 -> "$operation was temporarily rate-limited by GitHub (HTTP 403). Wait a little and retry. ${detail.take(120)}".trim()
            404 -> "$operation endpoint or release asset was not found (HTTP 404). The release may have been moved or removed."
            416 -> "The update server rejected the saved download range (HTTP 416). Clear the partial update and download again."
            in 500..599 -> "$operation server is temporarily unavailable (HTTP $code). Please retry later."
            else -> "$operation returned HTTP $code. ${detail.take(160)}".trim()
        }
    }

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
