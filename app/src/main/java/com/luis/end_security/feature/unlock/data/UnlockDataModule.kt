package com.luis.end_security.feature.unlock.data

import com.luis.end_security.feature.unlock.domain.UnlockSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UnlockDataModule {

    @Binds
    abstract fun bindUnlockSettingsRepository(impl: UnlockSettingsRepositoryImpl): UnlockSettingsRepository
}
