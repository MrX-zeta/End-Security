package com.luis.end_security.core.di

import com.luis.end_security.BuildConfig
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.TokenManager
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.api.SessionApiImpl
import com.luis.end_security.core.network.api.VaultApiImpl
import com.luis.end_security.core.network.fake.FakeTransport
import com.luis.end_security.core.network.fake.FakeVerificationCodes
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.activity.domain.SessionApi
import com.luis.end_security.feature.auth.domain.AuthApi
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import com.luis.end_security.feature.vault.domain.VaultApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

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
    abstract fun bindAccountApi(impl: AccountApiImpl): AccountApi

    @Binds
    abstract fun bindSessionApi(impl: SessionApiImpl): SessionApi

    @Binds
    abstract fun bindDebugVerificationCodes(impl: FakeVerificationCodes): DebugVerificationCodes

    companion object {
        /** DEC-61: el backend falso mientras no exista el servidor; Retrofit/OkHttp llega después. */
        @Provides
        fun provideTransport(fake: Provider<FakeTransport>): ApiTransport {
            check(BuildConfig.USE_FAKE_BACKEND) { "Todavía no existe el transporte con Retrofit" }
            return fake.get()
        }
    }
}
