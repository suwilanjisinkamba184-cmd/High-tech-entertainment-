package com.hightechentertainment.app

import android.app.Application
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.AuthSessionManager
import com.hightechentertainment.app.data.CatalogRepository
import com.hightechentertainment.app.data.FeatureRepository
import com.hightechentertainment.app.data.SecureSessionStore
import com.hightechentertainment.app.data.UserLibraryRepository
import com.hightechentertainment.app.data.SupabaseRestClient
import com.hightechentertainment.app.data.SponsoredAudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-scoped owners for authentication and catalogue access.
 * Activity recreation must not cancel session refresh or lose the active session.
 */
class HteApplication : Application() {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore by lazy { SecureSessionStore(applicationContext) }
    val authRepository by lazy { AuthRepository(SupabaseRestClient(), sessionStore) }
    val sessionManager by lazy { AuthSessionManager(authRepository, sessionStore, sessionScope) }
    private val authenticatedRestClient by lazy {
        SupabaseRestClient(accessToken = { sessionManager.session.value?.accessToken })
    }
    val catalogRepository by lazy { CatalogRepository(authenticatedRestClient) }
    val featureRepository by lazy { FeatureRepository(authenticatedRestClient) { sessionManager.session.value?.userId } }
    val sponsoredAudioRepository by lazy { SponsoredAudioRepository(authenticatedRestClient) { sessionManager.session.value?.userId } }
    val userLibraryRepository by lazy {
        UserLibraryRepository(authenticatedRestClient) { sessionManager.session.value?.userId }
    }
}
