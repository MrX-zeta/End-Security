package com.luis.end_security.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.TimeSource

/** Fuente de tiempo monotónica; en las pruebas se usa el reloj virtual de TestScope. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideTimeSource(): TimeSource = TimeSource.Monotonic
}
