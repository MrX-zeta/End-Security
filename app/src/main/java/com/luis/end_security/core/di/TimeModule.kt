package com.luis.end_security.core.di

import com.luis.end_security.core.common.AndroidDeviceClock
import com.luis.end_security.core.common.DeviceClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.TimeSource

/**
 * Fuente de tiempo monotónica (en las pruebas, el reloj virtual de TestScope) y los relojes del
 * teléfono (en las pruebas, uno falso).
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideTimeSource(): TimeSource = TimeSource.Monotonic

    @Provides
    fun provideDeviceClock(): DeviceClock = AndroidDeviceClock
}
