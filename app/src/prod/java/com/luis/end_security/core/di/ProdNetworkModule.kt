package com.luis.end_security.core.di

import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Singleton

/**
 * Variante `prod`: no contiene el backend falso. Mientras no esté el transporte real (Retrofit/OkHttp contra el servidor),
 * toda petición responde «sin conexión». El APK `prod` no se publica hasta la integración (`docs/api/brechas-backend.md`).
 */
@Module
@InstallIn(SingletonComponent::class)
object ProdNetworkModule {

    // TODO(T-integración): reemplazar por el transporte real contra el backend.
    @Provides
    @Singleton
    fun provideTransport(): ApiTransport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = throw IOException("Sin conexión con el servidor")
    }

    /** Sin «Modo demostración»: nunca hay código de verificación a la vista. */
    @Provides
    @Singleton
    fun provideDebugVerificationCodes(): DebugVerificationCodes = object : DebugVerificationCodes {
        override fun latestCode(email: String): String? = null
    }
}
