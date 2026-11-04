package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.ChangeEmail
import com.luis.end_security.feature.account.domain.ConfirmEmailResult
import com.luis.end_security.feature.account.domain.ReauthRunner
import com.luis.end_security.feature.account.domain.RequestEmailResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OP-26 (DEC-63). EP-15 con reautenticación y EP-16 con el código. Con el 200 de EP-16, `account.email` se actualiza en Room
 * **dentro de `NonCancellable`** (si se cancelara entre el 200 y Room, el servidor tendría el correo nuevo y este teléfono el viejo
 * hasta la siguiente consulta de la cuenta) y solo para el `userId` de la fila local.
 */
class ChangeEmailImpl @Inject constructor(
    private val api: AccountApi,
    private val accounts: AccountDao,
    private val reauth: ReauthRunner,
    private val sync: AccountEmailSync,
) : ChangeEmail {

    override suspend fun currentEmail(): String? = accounts.get()?.email

    override suspend fun twoFactorEnabled(): Boolean? = (api.getAccount() as? ApiResult.Success)?.value?.twoFactorEnabled

    override suspend fun request(newEmail: String, password: CharSequence, totpCode: String?): RequestEmailResult {
        val localKeyVersion = accounts.get()?.keyVersion ?: return RequestEmailResult.Generic
        val result = reauth.run(password, totpCode) { reauthKey -> api.requestEmailChange(newEmail, reauthKey) }
        return when (result) {
            is ApiResult.Success -> RequestEmailResult.Accepted
            is ApiResult.Failure -> when (val error = result.error) {
                ApiError.ReauthRequired ->
                    if (serverKeyVersionIsNewer(localKeyVersion)) RequestEmailResult.KeyVersionConflict else RequestEmailResult.WrongCredentials
                ApiError.InvalidCredentials, is ApiError.InvalidCode -> RequestEmailResult.WrongCredentials
                is ApiError.RateLimited -> RequestEmailResult.RateLimited(error.retryAfterSeconds)
                // `validation_failed` con un correo que la app ya validó: el servidor lo ve igual al actual (normalización distinta).
                ApiError.ValidationFailed -> RequestEmailResult.SameAsCurrent
                else -> RequestEmailResult.Generic
            }
        }
    }

    override suspend fun confirm(code: String): ConfirmEmailResult = when (val result = api.confirmEmailChange(code)) {
        is ApiResult.Success -> {
            withContext(NonCancellable) { runCatching { sync.apply(result.value) } }
            ConfirmEmailResult.Changed(result.value.email)
        }
        is ApiResult.Failure -> when (val error = result.error) {
            is ApiError.InvalidCode -> ConfirmEmailResult.WrongCode(error.attemptsRemaining ?: 0)
            ApiError.ChallengeExpired -> ConfirmEmailResult.Expired
            is ApiError.RateLimited -> ConfirmEmailResult.RateLimited(error.retryAfterSeconds)
            else -> ConfirmEmailResult.Generic
        }
    }

    private suspend fun serverKeyVersionIsNewer(localKeyVersion: Int): Boolean =
        when (val response = api.getAccount()) {
            is ApiResult.Success -> response.value.keyVersion > localKeyVersion
            is ApiResult.Failure -> false
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChangeEmailModule {
    @Binds
    abstract fun bindChangeEmail(impl: ChangeEmailImpl): ChangeEmail
}
