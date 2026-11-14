package com.hightechentertainment.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.Signature
import java.security.MessageDigest

/**
 * Production runtime integrity gate.
 *
 * The pin was extracted from the immutable native-v4.0.5-build38 public APK
 * and independently matched against a candidate signed with the existing
 * production keystore in CI. Debug variants are intentionally handled by
 * MainActivity and do not use this production pin.
 */
object HteSecurityFirewall {
    const val EXPECTED_PACKAGE_NAME = "com.hightechentertainment.app"
    const val TRUSTED_CERT_SHA256 = "6bfa59139e569475fd1843a59216f50cc5bb407df5cf48b8007cc96073adaffa"

    fun verifyAppIntegrity(context: Context): Boolean = runCatching {
        if (context.packageName != EXPECTED_PACKAGE_NAME) return false
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val signers: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        if (signers == null || signers.size != 1) return false
        isTrustedFingerprint(sha256Fingerprint(signers.single().toByteArray()))
    }.getOrDefault(false)

    internal fun isTrustedFingerprint(value: String): Boolean =
        value.equals(TRUSTED_CERT_SHA256, ignoreCase = true)

    internal fun sha256Fingerprint(certificateBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificateBytes)
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
