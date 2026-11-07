package com.hightechentertainment.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.hightechentertainment.app.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NativeUpdate(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val apkAssetName: String,
    val sha256: String,
    val certificateSha256: String,
    val releaseNotes: String,
    val apkDownloadUrl: String
)

/** Checks GitHub release manifests and installs only matching, newer, same-signer APKs. */
class NativeUpdateManager(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): Result<NativeUpdate?> = runCatching {
        withContext(Dispatchers.IO) {
            val releasesRequest = Request.Builder()
                .url("https://api.github.com/repos/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases?per_page=20")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "High-Tech-Entertainment-Android")
                .build()
            val releasesResponse = http.newCall(releasesRequest).execute()
            val releasesBody = releasesResponse.use {
                check(it.isSuccessful) { "Could not check updates (HTTP ${it.code})." }
                it.body?.string().orEmpty()
            }
            val releases = JSONArray(releasesBody)
            val currentInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentCode = PackageInfoCompat.getLongVersionCode(currentInfo)
            var best: NativeUpdate? = null

            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                val assets = release.optJSONArray("assets") ?: continue
                val manifestAsset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "manifest.json" } ?: continue
                val manifestRequest = Request.Builder()
                    .url(manifestAsset.optString("browser_download_url"))
                    .header("User-Agent", "High-Tech-Entertainment-Android")
                    .build()
                val manifestResponse = http.newCall(manifestRequest).execute()
                val manifestJson = manifestResponse.use {
                    if (!it.isSuccessful) return@use null
                    it.body?.string()
                } ?: continue
                val manifest = JSONObject(manifestJson)
                val packageName = manifest.optString("packageName")
                val versionCode = manifest.optLong("versionCode")
                if (packageName != context.packageName || versionCode <= currentCode) continue
                val apkName = manifest.optString("apkAssetName")
                val apkAsset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == apkName } ?: continue
                val candidate = NativeUpdate(
                    versionName = manifest.optString("versionName"),
                    versionCode = versionCode,
                    packageName = packageName,
                    apkAssetName = apkName,
                    sha256 = manifest.optString("sha256").lowercase(),
                    certificateSha256 = manifest.optString("certificateSha256").lowercase(),
                    releaseNotes = release.optString("body"),
                    apkDownloadUrl = apkAsset.optString("browser_download_url")
                )
                if (candidate.sha256.matches(Regex("[0-9a-f]{64}")) &&
                    candidate.certificateSha256.matches(Regex("[0-9a-f]{64}")) &&
                    (best == null || candidate.versionCode > best!!.versionCode)
                ) best = candidate
            }
            best
        }
    }

    suspend fun downloadVerifyAndInstall(update: NativeUpdate): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(update.apkDownloadUrl)
                .header("User-Agent", "High-Tech-Entertainment-Android").build()
            val response = http.newCall(request).execute()
            val apkFile = File(context.cacheDir, "hte-update-${update.versionCode}.apk")
            response.use { bodyResponse ->
                check(bodyResponse.isSuccessful) { "APK download failed (HTTP ${bodyResponse.code})." }
                val body = bodyResponse.body ?: error("The release APK was empty.")
                body.byteStream().use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
            }
            check(sha256(apkFile) == update.sha256) { "Downloaded APK checksum does not match the signed release manifest." }
            val archiveInfo = packageInfoFromApk(apkFile) ?: error("The downloaded file is not a valid Android APK.")
            check(archiveInfo.packageName == context.packageName && archiveInfo.packageName == update.packageName) {
                "The APK package identity does not match this installation."
            }
            check(PackageInfoCompat.getLongVersionCode(archiveInfo) == update.versionCode) {
                "APK version code does not match the release manifest."
            }
            check(certificateSha256(archiveInfo) == update.certificateSha256) {
                "APK signing certificate does not match the release manifest."
            }
            val installedInfo = context.packageManager.getPackageInfo(context.packageName, signingFlags())
            check(certificateSha256(installedInfo) == update.certificateSha256) {
                "Update signing certificate does not match the installed app. In-place installation is blocked."
            }
            check(update.versionCode > PackageInfoCompat.getLongVersionCode(installedInfo)) {
                "This update is not newer than the installed version."
            }
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                    error("Allow High-Tech Entertainment to install updates, then return and tap Install update again.")
                }
                installWithPackageInstaller(apkFile, update.versionCode)
            }
        }
    }

    private fun installWithPackageInstaller(apkFile: File, versionCode: Long) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            FileInputStream(apkFile).use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(context, MainActivity::class.java)
                .setAction("com.hightechentertainment.app.UPDATE_INSTALL_STATUS")
                .putExtra("update_version_code", versionCode)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getActivity(context, sessionId, statusIntent, flags)
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun packageInfoFromApk(file: File): PackageInfo? {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    private fun certificateSha256(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val signer = signatures?.firstOrNull() ?: error("APK signing certificate was unavailable.")
        return MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
