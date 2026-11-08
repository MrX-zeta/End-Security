package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.feature.account.domain.Reauth
import com.luis.end_security.feature.account.domain.ReauthRunner
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.charset.CharacterCodingException
import javax.inject.Inject

class ReauthRunnerImpl @Inject constructor(
    private val accounts: AccountDao,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ReauthRunner {

    override suspend fun <T> run(
        password: CharSequence,
        totpCode: String?,
        block: suspend (Reauth) -> ApiResult<T>,
    ): ApiResult<T> {
        val account = accounts.get() ?: return ApiResult.Failure(ApiError.ValidationFailed)
        // Se normaliza en el hilo que llama: el TextFieldState de la pantalla puede cambiar.
        val normalized = MasterPasswordNormalizer.normalize(password)
        val params = try {
            KdfParams(account.kdfMemoryKiB, account.kdfIterations, account.kdfParallelism)
        } catch (_: IllegalArgumentException) {
            return ApiResult.Failure(ApiError.ReauthRequired)
        }
        // Argon2 fuera del hilo principal y sin cancelarse a la mitad. La `auth_key` se deja en [authKey] desde dentro del
        // bloque y se borra en el `finally`, también si `withContext` lanza `CancellationException` al volver (M1).
        var authKey: SecureBytes? = null
        try {
            try {
                withContext(defaultDispatcher + NonCancellable) {
                    KeyDerivation.deriveMasterKey(normalized, account.kdfSalt, params).use { master ->
                        authKey = KeyDerivation.authKey(master)
                    }
                }
            } catch (_: CharacterCodingException) {
                return ApiResult.Failure(ApiError.ReauthRequired)
            }
            currentCoroutineContext().ensureActive()
            return block(Reauth(checkNotNull(authKey), totpCode))
        } finally {
            authKey?.close()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReauthModule {
    @Binds
    abstract fun bindReauthRunner(impl: ReauthRunnerImpl): ReauthRunner
}
