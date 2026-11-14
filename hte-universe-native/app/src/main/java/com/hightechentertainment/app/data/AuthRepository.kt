package com.hightechentertainment.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hightechentertainment.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken

/** Member authentication. Founder access remains enforced by Supabase roles and RLS. */
class AuthRepository {
    suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.isNotEmpty()) { "Enter your password." }
        SupabaseClientProvider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.length >= 8) { "Use a password with at least 8 characters." }
        SupabaseClientProvider.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signInWithGoogle(context: Context): Boolean {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            SupabaseClientProvider.client.auth.signInWith(
                Google,
                redirectUrl = SupabaseClientProvider.AUTH_REDIRECT_URI
            )
            return false
        }
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a supported sign-in credential. Please try again."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        SupabaseClientProvider.client.auth.signInWith(IDToken) {
            idToken = googleCredential.idToken
            provider = Google
        }
        return true
    }

    suspend fun signOut() {
        SupabaseClientProvider.client.auth.signOut()
    }
}
