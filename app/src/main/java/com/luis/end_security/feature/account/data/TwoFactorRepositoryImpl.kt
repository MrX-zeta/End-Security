package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.ReauthRunner
import com.luis.end_security.feature.account.domain.TwoFactorRepository
import com.luis.end_security.feature.account.domain.TwoFactorSetup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class TwoFactorRepositoryImpl @Inject constructor(
    private val api: AccountApi,
    private val reauth: ReauthRunner,
) : TwoFactorRepository {

    override suspend fun isEnabled(): Boolean? = (api.getAccount() as? ApiResult.Success)?.value?.twoFactorEnabled

    override suspend fun startSetup(): ApiResult<TwoFactorSetup> = api.startTwoFactorSetup()

    override suspend fun confirm(code: String): ApiResult<List<String>> = api.confirmTwoFactor(code)

    override suspend fun disable(password: CharSequence, totpCode: String): ApiResult<Unit> =
        reauth.run(password, totpCode) { api.disableTwoFactor(it) }

    override suspend fun regenerateRecoveryCodes(password: CharSequence, totpCode: String): ApiResult<List<String>> =
        reauth.run(password, totpCode) { api.regenerateRecoveryCodes(it) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TwoFactorDataModule {
    @Binds
    abstract fun bindTwoFactorRepository(impl: TwoFactorRepositoryImpl): TwoFactorRepository
}
