package com.hightechentertainment.app.data

import com.hightechentertainment.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Single application-scoped Supabase client.
 *
 * Only the project URL and publishable key are allowed in the Android client.
 * Never place a service-role or secret key in BuildConfig or the APK.
 */
object SupabaseClientProvider {
    const val AUTH_REDIRECT_URI = "com.hightechentertainment.app://auth-callback"
    val client by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Supabase URL is not configured for this build."
        }
        check(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase publishable key is not configured for this build."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "com.hightechentertainment.app"
                host = "auth-callback"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }
}
