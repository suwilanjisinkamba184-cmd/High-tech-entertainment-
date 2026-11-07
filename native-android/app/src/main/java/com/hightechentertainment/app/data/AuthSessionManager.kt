package com.hightechentertainment.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns persisted Supabase session restoration and refresh scheduling.
 *
 * Refreshes before expiry, publishes a nullable authenticated session to the UI,
 * and clears invalid credentials. The caller supplies an application-lifetime
 * scope and must cancel it when the process-level owner is disposed.
 */
class AuthSessionManager(
    private val repository: AuthRepository,
    private val store: SecureSessionStore,
    private val scope: CoroutineScope,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val mutableSession = MutableStateFlow<NativeSession?>(null)
    val session: StateFlow<NativeSession?> = mutableSession.asStateFlow()

    private val mutableStatus = MutableStateFlow("signed_out")
    val status: StateFlow<String> = mutableStatus.asStateFlow()

    private var refreshJob: Job? = null

    suspend fun restore() {
        val saved = store.read()
        if (saved == null) {
            mutableSession.value = null
            mutableStatus.value = "signed_out"
            return
        }
        mutableSession.value = saved
        mutableStatus.value = "restoring"
        if (saved.expiresAtEpochSeconds <= nowSeconds() + REFRESH_EARLY_SECONDS) {
            applyRefresh(repository.refreshSession())
        } else {
            mutableStatus.value = "authenticated"
        }
        if (mutableStatus.value != "refresh_retry" && mutableSession.value != null) {
            scheduleRefresh()
        }
    }

    fun accept(session: NativeSession) {
        mutableSession.value = session
        mutableStatus.value = "authenticated"
        scheduleRefresh()
    }

    suspend fun signOut() {
        refreshJob?.cancel()
        refreshJob = null
        repository.signOut()
        store.clear()
        mutableSession.value = null
        mutableStatus.value = "signed_out"
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        val current = mutableSession.value ?: return
        val waitSeconds = (current.expiresAtEpochSeconds - nowSeconds() - REFRESH_EARLY_SECONDS)
            .coerceAtLeast(1L)
        refreshJob = scope.launch {
            delay(waitSeconds * 1_000L)
            if (!isActive) return@launch
            applyRefresh(repository.refreshSession())
            if (mutableSession.value != null && mutableStatus.value != "refresh_retry") scheduleRefresh()
        }
    }

    private fun applyRefresh(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                mutableSession.value = result.session
                mutableStatus.value = "authenticated"
            }
            is AuthResult.Failure -> {
                // A transient network failure must not erase a still-valid refresh token.
                val stillStored = store.read()
                if (stillStored == null || stillStored.expiresAtEpochSeconds <= nowSeconds()) {
                    store.clear()
                    mutableSession.value = null
                    mutableStatus.value = "signed_out"
                } else {
                    mutableSession.value = stillStored
                    mutableStatus.value = "refresh_retry"
                    scheduleRetry()
                }
            }
            is AuthResult.Notice -> Unit
        }
    }

    private fun scheduleRetry() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(RETRY_DELAY_SECONDS * 1_000L)
            if (isActive) {
                applyRefresh(repository.refreshSession())
                if (mutableSession.value != null && mutableStatus.value != "refresh_retry") scheduleRefresh()
            }
        }
    }

    private companion object {
        const val REFRESH_EARLY_SECONDS = 120L
        const val RETRY_DELAY_SECONDS = 30L
    }
}
