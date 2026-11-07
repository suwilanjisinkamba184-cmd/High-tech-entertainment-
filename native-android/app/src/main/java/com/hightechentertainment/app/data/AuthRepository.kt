package com.hightechentertainment.app.data

import org.json.JSONObject

data class NativeSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String?
)

sealed interface AuthResult {
    data class Success(val session: NativeSession) : AuthResult
    data class Failure(val message: String, val statusCode: Int? = null) : AuthResult
    data class Notice(val message: String) : AuthResult
}

/**
 * Supabase Auth REST adapter. It only uses the public publishable key;
 * authorization and role checks remain enforced by Supabase Auth and RLS.
 */
class AuthRepository(
    private val client: SupabaseRestClient,
    private val sessions: SecureSessionStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    suspend fun signInWithPassword(email: String, password: String): AuthResult {
        if (!email.isValidEmail() || password.isBlank()) {
            return AuthResult.Failure("Enter a valid email address and password.")
        }
        return authenticate(
            path = "/auth/v1/token",
            query = mapOf("grant_type" to "password"),
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
    }

    suspend fun signUp(email: String, password: String): AuthResult {
        if (!email.isValidEmail() || password.length < 8) {
            return AuthResult.Failure("Enter a valid email and a password with at least 8 characters.")
        }
        return authenticate(
            path = "/auth/v1/signup",
            body = JSONObject().put("email", email.trim()).put("password", password),
            allowConfirmationWithoutSession = true
        )
    }

    suspend fun refreshSession(): AuthResult {
        val saved = sessions.read()
            ?: return AuthResult.Failure("Your session has expired. Please sign in again.")
        return authenticate(
            path = "/auth/v1/token",
            query = mapOf("grant_type" to "refresh_token"),
            body = JSONObject().put("refresh_token", saved.refreshToken)
        )
    }

    suspend fun signOut(): AuthResult {
        val saved = sessions.read()
        if (saved == null) {
            sessions.clear()
            return AuthResult.Failure("There is no active session.")
        }
        return try {
            val response = client.request(
                method = "POST",
                path = "/auth/v1/logout",
                headers = mapOf("Authorization" to "Bearer ${saved.accessToken}")
            )
            sessions.clear()
            if (response.statusCode in 200..299) AuthResult.Success(saved)
            else AuthResult.Failure("Sign-out could not be confirmed by the server.", response.statusCode)
        } catch (_: Exception) {
            // Clear local credentials even when offline; the server token will expire or be revoked separately.
            sessions.clear()
            AuthResult.Failure("Signed out on this device. Server confirmation was unavailable.")
        }
    }

    suspend fun requestPasswordReset(email: String): AuthResult {
        if (!email.isValidEmail()) return AuthResult.Failure("Enter a valid email address.")
        return try {
            val response = client.request(
                method = "POST",
                path = "/auth/v1/recover",
                body = JSONObject().put("email", email.trim()).toString()
            )
            if (response.isSuccessful) {
                AuthResult.Notice("If the account exists, password-reset instructions will be sent.")
            } else {
                AuthResult.Failure("Password reset could not be requested. Please try again.", response.statusCode)
            }
        } catch (_: Exception) {
            AuthResult.Failure("Unable to reach account services. Check your connection and retry.")
        }
    }

    private suspend fun authenticate(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject,
        allowConfirmationWithoutSession: Boolean = false
    ): AuthResult = try {
        val response = client.request(
            method = "POST",
            path = path,
            query = query,
            body = body.toString()
        )
        if (!response.isSuccessful) {
            val detail = runCatching { JSONObject(response.body).optString("msg").ifBlank {
                JSONObject(response.body).optString("message")
            } }.getOrNull().orEmpty()
            return AuthResult.Failure(
                detail.takeIf { it.isNotBlank() } ?: "Sign-in failed. Check your details and try again.",
                response.statusCode
            )
        }
        val json = JSONObject(response.body)
        val user = json.optJSONObject("user")
            ?: return AuthResult.Failure("The authentication response did not include a user.")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userId = user.optString("id")
        if (userId.isBlank()) {
            return AuthResult.Failure("The authentication response did not include an account.")
        }
        if (access.isBlank() || refresh.isBlank()) {
            if (allowConfirmationWithoutSession) {
                return AuthResult.Notice("Account created. Check your email to confirm your address, then sign in.")
            }
            return AuthResult.Failure("The authentication response was incomplete.")
        }
        val expiresAt = json.optLong("expires_at").takeIf { it > 0 }
            ?: nowSeconds() + json.optLong("expires_in", 3600L)
        val session = NativeSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = expiresAt,
            userId = userId,
            email = user.optString("email").takeIf { it.isNotBlank() }
        )
        sessions.write(session)
        AuthResult.Success(session)
    } catch (_: Exception) {
        AuthResult.Failure("Unable to reach account services. Check your connection and retry.")
    }

    private fun String.isValidEmail(): Boolean =
        isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(trim()).matches()
}
