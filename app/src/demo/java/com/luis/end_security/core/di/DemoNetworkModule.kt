package com.luis.end_security.core.di

import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.fake.FakeTransport
import com.luis.end_security.core.network.fake.FakeVerificationCodes
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Variante `demo` (DEC-61): el transporte es el backend falso y los códigos de verificación se muestran en pantalla. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DemoNetworkModule {

    @Binds
    abstract fun bindDebugVerificationCodes(impl: FakeVerificationCodes): DebugVerificationCodes

    @Binds
    abstract fun bindTransport(impl: FakeTransport): ApiTransport
}
