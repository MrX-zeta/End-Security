package com.luis.end_security.core.network

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.datastore.StoredTokens
import com.luis.end_security.core.datastore.TokenStore
import com.luis.end_security.feature.account.domain.SessionTokens
import com.luis.end_security.feature.auth.domain.AuthApi
import dagger.Lazy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los tokens de la sesión (DEC-16): en memoria mientras corre la app y cifrados en [TokenStore].
 * Las renovaciones van de una en una con un [Mutex] (regla 9): si dos llamadas reciben
 * `token_expired` a la vez, la segunda usa el token que obtuvo la primera, porque dos
 * renovaciones simultáneas se detectarían como robo y revocarían la sesión.
 */
@Singleton
class TokenManager @Inject constructor(
    private val store: TokenStore,
    // Lazy: AuthApi usa ApiClient, que a su vez usa este TokenManager.
    private val authApi: Lazy<AuthApi>,
) : TokenProvider {

    private val mutex = Mutex()
    private var loaded = false
    private var cached: StoredTokens? = null

    private val _sessionRevoked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite cuando el servidor revoca la sesión: hay que cerrar la sesión local. */
    val sessionRevoked: SharedFlow<Unit> = _sessionRevoked.asSharedFlow()

    suspend fun save(tokens: SessionTokens) = mutex.withLock {
        val stored = StoredTokens(tokens.accessToken, tokens.refreshToken)
        store.save(stored)
        cached = stored
        loaded = true
    }

    suspend fun clear() = mutex.withLock {
        clearLocked()
    }

    suspend fun hasSession(): Boolean = mutex.withLock { current() != null }

    override suspend fun accessToken(): String? = mutex.withLock { current()?.accessToken }

    override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome = mutex.withLock {
        val current = current() ?: return@withLock RefreshOutcome.Revoked
        // Otra llamada ya renovó mientras esta esperaba el candado.
        if (current.accessToken != expiredAccessToken) return@withLock RefreshOutcome.Renewed(current.accessToken)
        // Sin cancelarse a la mitad (hallazgo 3 de la revisión): si el servidor ya rotó el refresh
        // y el par nuevo se perdiera, el siguiente intento usaría el viejo y se vería como reúso.
        val result = withContext(NonCancellable) { authApi.get().refresh(current.refreshToken) }
        when (result) {
            is ApiResult.Success -> {
                val stored = StoredTokens(result.value.accessToken, result.value.refreshToken)
                // Primero en memoria: aunque falle el disco, la sesión sigue con el par nuevo.
                cached = stored
                withContext(NonCancellable) { store.save(stored) }
                RefreshOutcome.Renewed(stored.accessToken)
            }
            is ApiResult.Failure -> when (result.error) {
                ApiError.SessionRevoked -> {
                    clearLocked()
                    _sessionRevoked.tryEmit(Unit)
                    RefreshOutcome.Revoked
                }
                else -> RefreshOutcome.Unavailable
            }
        }
    }

    override suspend fun onSessionRevoked() {
        mutex.withLock { clearLocked() }
        _sessionRevoked.tryEmit(Unit)
    }

    // Con el candado tomado.
    private suspend fun current(): StoredTokens? {
        if (!loaded) {
            cached = store.read()
            loaded = true
        }
        return cached
    }

    private suspend fun clearLocked() {
        cached = null
        loaded = true
        store.clear()
    }
}
