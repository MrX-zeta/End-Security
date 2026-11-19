package com.luis.end_security.core.di

import android.os.Build
import com.luis.end_security.BuildConfig
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.backend.BackendTransport
import com.luis.end_security.core.network.backend.BackendTranslator
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Variante `prod` (DEC-65): el transporte es el backend real, traducido por [BackendTranslator]. No contiene el backend
 * falso. La URL viene de `BuildConfig.API_BASE_URL` (en release está vacía hasta BR-26: «sin conexión»).
 */
@Module
@InstallIn(SingletonComponent::class)
object ProdNetworkModule {

    @Provides
    @Singleton
    fun provideTransport(): ApiTransport {
        // INV-18: fuera de debug solo HTTPS. TODO(BR-26): pines SPKI (principal y respaldo) en network_security_config.
        check(BuildConfig.DEBUG || BuildConfig.API_BASE_URL.isEmpty() || BuildConfig.API_BASE_URL.startsWith("https://")) {
            "API_BASE_URL de release debe ser HTTPS"
        }
        return BackendTransport(
            baseUrl = BuildConfig.API_BASE_URL,
            client = OkHttpClient.Builder()
                // Sin reintentos de OkHttp: reenviar un POST /auth/refresh ya aplicado parecería robo del token (INV-25).
                .retryOnConnectionFailure(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                // La rotación de llaves y el registro pasan por Argon2 en el servidor.
                .readTimeout(60, TimeUnit.SECONDS)
                .build(),
            translator = BackendTranslator(deviceName = { "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" } }),
        )
    }

    /** Sin «Modo demostración»: nunca hay código de verificación a la vista. */
    @Provides
    @Singleton
    fun provideDebugVerificationCodes(): DebugVerificationCodes = object : DebugVerificationCodes {
        override fun latestCode(email: String): String? = null
    }
}
