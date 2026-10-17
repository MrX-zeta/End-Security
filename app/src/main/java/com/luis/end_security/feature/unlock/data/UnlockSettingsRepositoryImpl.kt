package com.luis.end_security.feature.unlock.data

import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.feature.unlock.domain.UnlockSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UnlockSettingsRepositoryImpl @Inject constructor(
    settingsStore: SettingsStore,
) : UnlockSettingsRepository {

    override val biometricEnabled: Flow<Boolean> = settingsStore.biometricEnabled
}
