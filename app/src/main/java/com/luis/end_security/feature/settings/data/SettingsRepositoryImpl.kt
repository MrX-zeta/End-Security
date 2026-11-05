package com.luis.end_security.feature.settings.data

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.sync.SyncEngine
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.activity.domain.SessionApi
import com.luis.end_security.feature.settings.domain.AccountProfile
import com.luis.end_security.feature.settings.domain.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val accounts: AccountDao,
    private val items: VaultItemDao,
    private val store: SettingsStore,
    private val accountApi: AccountApi,
    private val sessionApi: SessionApi,
    private val sync: SyncEngine,
) : SettingsRepository {

    override val profile: Flow<AccountProfile?> = accounts.observe().map { row ->
        row?.let { AccountProfile(displayName = it.displayName, email = it.email) }
    }

    override val autolockMinutes: Flow<Int> = store.autolockMinutes

    override suspend fun setAutolockMinutes(minutes: Int) = store.setAutolockMinutes(minutes)

    override suspend fun twoFactorEnabled(): Boolean? =
        (accountApi.getAccount() as? ApiResult.Success)?.value?.twoFactorEnabled

    override suspend fun marketingOptIn(): Boolean? =
        (accountApi.getAccount() as? ApiResult.Success)?.value?.marketingOptIn

    override suspend fun setMarketingOptIn(optIn: Boolean): Boolean? =
        (accountApi.updateMarketingOptIn(optIn) as? ApiResult.Success)?.value?.marketingOptIn

    override suspend fun deviceCount(): Int? = (sessionApi.listSessions() as? ApiResult.Success)?.value?.size

    override suspend fun unsyncedCount(): Int = items.unsyncedCount()

    override suspend fun syncNow(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { sync.sync() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataModule {
    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
