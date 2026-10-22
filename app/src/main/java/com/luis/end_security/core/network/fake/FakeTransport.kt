package com.luis.end_security.core.network.fake

import android.content.Context
import androidx.core.util.AtomicFile
import com.luis.end_security.BuildConfig
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.network.ApiJson
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.feature.auth.domain.DebugVerificationCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Datos fijos del backend falso para las pruebas del equipo (`docs/api/backend-falso.md`). */
object FakeBackendDebug {
    /** Secreto TOTP de las cuentas `+2fa` (Base32, 20 bytes). */
    const val TOTP_SECRET = "ENDSECURITYDEBUGTOTPSECRETKEY234"

    /** Los 10 códigos de respaldo de las cuentas `+2fa`, ya normalizados (sin guion). */
    val RECOVERY_CODES = (1..10).map { "DBGA" + it.toString().padStart(4, '0') }

    /** Un correo con `+2fa` en la parte local (p. ej. `ana+2fa@correo.com`) se crea con 2FA activo. */
    fun isTwoFactorDebugEmail(email: String): Boolean = BuildConfig.DEBUG && email.substringBefore('@').contains("+2fa")
}

/**
 * Último código de verificación de cada correo, solo en memoria y solo mientras `USE_FAKE_BACKEND`
 * sea `true` (también en release): Verificar correo lo muestra como «Modo demostración · Código».
 * Nunca va a logcat ni a disco.
 */
@Singleton
class FakeVerificationCodes @Inject constructor() : DebugVerificationCodes {
    private val codes = mutableMapOf<String, String>()

    @Synchronized
    fun remember(email: String, code: String) {
        if (BuildConfig.USE_FAKE_BACKEND) codes[email] = code
    }

    @Synchronized
    override fun latestCode(email: String): String? =
        if (BuildConfig.USE_FAKE_BACKEND) codes[email.trim().lowercase()] else null
}

/**
 * [ApiTransport] del backend falso (DEC-61): aplica [FakeServer] a cada petición, con una latencia
 * de 300–800 ms para que se vean los estados de carga, y guarda el estado en
 * `filesDir/fake_server.json` para que cerrar la app no borre las cuentas.
 */
@Singleton
class FakeTransport @Inject constructor(
    @ApplicationContext context: Context,
    private val codes: FakeVerificationCodes,
) : ApiTransport {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val mutex = Mutex()
    private var state: FakeState? = null

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        delay(RandomBytes.nextInt(MIN_LATENCY_MS, MAX_LATENCY_MS + 1).toLong())
        mutex.withLock {
            val current = state ?: load().also { state = it }
            val response = FakeServer(current, System::currentTimeMillis, codes::remember).handle(request)
            save(current)
            response
        }
    }

    private fun load(): FakeState = try {
        ApiJson.decodeFromString(FakeState.serializer(), file.readFully().decodeToString())
    } catch (_: IOException) {
        newState()
    } catch (_: SerializationException) {
        newState()
    } catch (_: IllegalArgumentException) {
        newState()
    }

    private fun save(current: FakeState) {
        val stream = file.startWrite()
        try {
            stream.write(ApiJson.encodeToString(FakeState.serializer(), current).encodeToByteArray())
            file.finishWrite(stream)
        } catch (error: IOException) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val FILE_NAME = "fake_server.json"
        const val MIN_LATENCY_MS = 300
        const val MAX_LATENCY_MS = 800

        fun newState() = FakeState(
            preloginSecret = Base64Url.encode(RandomBytes.next(32)),
            pepper = Base64Url.encode(RandomBytes.next(32)),
        )
    }
}
