package com.luis.end_security.core.di

import com.luis.end_security.core.network.TokenManager
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.SessionApiImpl
import com.luis.end_security.core.network.api.VaultApiImpl
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.activity.domain.SessionApi
import com.luis.end_security.feature.auth.domain.AuthApi
import com.luis.end_security.feature.vault.domain.VaultApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindTokenProvider(impl: TokenManager): TokenProvider

    @Binds
    abstract fun bindAuthApi(impl: AuthApiImpl): AuthApi

    @Binds
    abstract fun bindVaultApi(impl: VaultApiImpl): VaultApi

    @Binds
    abstract fun bindAccountApi(impl: com.luis.end_security.feature.account.data.EmailSyncingAccountApi): AccountApi

    @Binds
    abstract fun bindSessionApi(impl: SessionApiImpl): SessionApi

    // El `ApiTransport` y los códigos de «Modo demostración» los enlaza el módulo de cada variante (`src/demo`, `src/prod`).
}
