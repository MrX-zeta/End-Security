package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.session.AccountDeletionGate
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.DeleteAccount
import com.luis.end_security.feature.account.domain.DeleteAccountResult
import com.luis.end_security.feature.account.domain.ReauthRunner
import com.luis.end_security.feature.auth.domain.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OP-28 (DEC-18), en este orden:
 * 1. reautenticación: la `auth_key` se deriva con los parámetros de `account` ([ReauthRunner]) y se borra al terminar;
 * 2. EP-18 con `Reauth`;
 * 3. con el 204, la limpieza local ([AuthRepository.wipeAfterAccountDeletion]) y el aviso a la navegación ([AccountDeletionGate]).
 *
 * Desde que se manda EP-18 hasta terminar la limpieza todo corre en [NonCancellable] (como A1 de la tarea 18): si se cancelara
 * entre el 204 y la limpieza, el servidor tendría la cuenta eliminada y este teléfono seguiría con todos los datos.
 */
class DeleteAccountImpl @Inject constructor(
    private val api: AccountApi,
    private val accounts: AccountDao,
    private val items: VaultItemDao,
    private val reauth: ReauthRunner,
    private val auth: AuthRepository,
    private val gate: AccountDeletionGate,
) : DeleteAccount {

    override suspend fun itemCount(): Int = items.visibleCount()

    override suspend fun delete(password: CharSequence, totpCode: String?): DeleteAccountResult {
        val localKeyVersion = accounts.get()?.keyVersion ?: return DeleteAccountResult.Generic
        var cleanupComplete = true
        val result = reauth.run(password, totpCode) { reauthKey ->
            withContext(NonCancellable) {
                val response = api.deleteAccount(reauthKey)
                if (response is ApiResult.Success) {
                    // El bloqueo que provoca la limpieza no debe mandar a Desbloquear; al terminar, la navegación a «Cuenta
                    // eliminada» no depende de la pantalla de Eliminar (que sale de la composición al bloquearse la bóveda).
                    gate.begin()
                    try {
                        cleanupComplete = auth.wipeAfterAccountDeletion()
                    } finally {
                        gate.finish()
                    }
                }
                response
            }
        }
        return when (result) {
            is ApiResult.Success -> DeleteAccountResult.Deleted(cleanupComplete)
            is ApiResult.Failure -> when (val error = result.error) {
                ApiError.ReauthRequired ->
                    if (serverKeyVersionIsNewer(localKeyVersion)) DeleteAccountResult.KeyVersionConflict else DeleteAccountResult.WrongCredentials
                ApiError.InvalidCredentials, is ApiError.InvalidCode -> DeleteAccountResult.WrongCredentials
                is ApiError.RateLimited -> DeleteAccountResult.RateLimited(error.retryAfterSeconds)
                else -> DeleteAccountResult.Generic
            }
        }
    }

    /** Igual que en el cambio de contraseña (tarea 19): una fila local desactualizada no es «contraseña incorrecta». */
    private suspend fun serverKeyVersionIsNewer(localKeyVersion: Int): Boolean =
        when (val response = api.getAccount()) {
            is ApiResult.Success -> response.value.keyVersion > localKeyVersion
            is ApiResult.Failure -> false
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeleteAccountModule {
    @Binds
    abstract fun bindDeleteAccount(impl: DeleteAccountImpl): DeleteAccount
}
