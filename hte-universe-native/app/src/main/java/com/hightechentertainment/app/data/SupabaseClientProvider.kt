package com.hightechentertainment.app.data

import android.content.Context
import com.hightechentertainment.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    const val AUTH_REDIRECT_URI = "com.hightechentertainment.app://auth-callback"
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val client by lazy {
        val context = checkNotNull(appContext) {
            "SupabaseClientProvider.initialize(applicationContext) must run before the client is accessed."
        }
        check(BuildConfig.SUPABASE_URL.isNotBlank()) { "Supabase URL is not configured for this build." }
        check(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) { "Supabase publishable key is not configured for this build." }
        createSupabaseClient(supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "com.hightechentertainment.app"
                host = "auth-callback"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                sessionManager = EncryptedSupabaseSessionManager(context)
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}
