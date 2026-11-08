package com.luis.end_security.feature.unlock.data

import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.feature.unlock.domain.UnlockSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UnlockSettingsRepositoryImpl @Inject constructor(
    settingsStore: SettingsStore,
    private val items: VaultItemDao,
) : UnlockSettingsRepository {

    override suspend fun unsyncedCount(): Int = items.unsyncedCount()

    override val biometricEnabled: Flow<Boolean> = settingsStore.biometricEnabled
}
