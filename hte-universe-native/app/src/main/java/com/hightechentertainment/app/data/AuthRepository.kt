package com.hightechentertainment.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Authentication operations for member accounts.
 * Founder access is enforced by trusted Supabase roles and RLS, not by client-side flags.
 */
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

    suspend fun signInWithGoogle() {
        SupabaseClientProvider.client.auth.signInWith(Google)
    }

    suspend fun signOut() {
        SupabaseClientProvider.client.auth.signOut()
    }
}
