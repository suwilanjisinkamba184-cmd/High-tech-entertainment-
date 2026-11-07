package com.hightechentertainment.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only encrypted session material. The AES key is non-exportable and held by Android Keystore.
 * If the key becomes unavailable or data is corrupted, credentials are discarded and the user signs in again.
 */
class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "hte_secure_session_v1",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun write(session: NativeSession) {
        val plain = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("expires", session.expiresAtEpochSeconds)
            .put("user", session.userId)
            .put("email", session.email)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(SESSION_KEY, payload).commit()) {
            "Unable to persist the encrypted session."
        }
    }

    @Synchronized
    fun read(): NativeSession? {
        return runCatching {
        val payload = preferences.getString(SESSION_KEY, null) ?: return null
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        NativeSession(
            accessToken = json.getString("access"),
            refreshToken = json.getString("refresh"),
            expiresAtEpochSeconds = json.getLong("expires"),
            userId = json.getString("user"),
            email = json.optString("email").takeIf { it.isNotBlank() && it != "null" }
        )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun writeOAuthVerifier(verifier: String) {
        require(verifier.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(verifier.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(OAUTH_VERIFIER_KEY, payload).commit()) {
            "Unable to persist the secure OAuth verifier."
        }
    }

    @Synchronized
    fun consumeOAuthVerifier(): String? {
        val payload = preferences.getString(OAUTH_VERIFIER_KEY, null) ?: return null
        preferences.edit().remove(OAUTH_VERIFIER_KEY).commit()
        return runCatching {
            val parts = payload.split(":", limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(SESSION_KEY).remove(OAUTH_VERIFIER_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.hightechentertainment.app.session.v1"
        const val SESSION_KEY = "encrypted_session"
        const val OAUTH_VERIFIER_KEY = "encrypted_oauth_pkce_verifier"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
