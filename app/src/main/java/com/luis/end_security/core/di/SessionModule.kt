package com.luis.end_security.core.di

import com.luis.end_security.core.datastore.SettingsStore
import com.luis.end_security.core.security.ClipboardSettings
import com.luis.end_security.core.session.AutoLockSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    abstract fun bindAutoLockSettings(impl: SettingsStore): AutoLockSettings

    @Binds
    abstract fun bindClipboardSettings(impl: SettingsStore): ClipboardSettings
}
