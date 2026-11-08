package com.luis.end_security.feature.auth.data

import android.content.ComponentCallbacks2
import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Llaves que esperan en memoria entre dos pantallas del acceso (nunca en disco ni en
 * `SavedStateHandle`, regla 6):
 * - la `vault_key` recién generada, de Crear cuenta a Verificar correo;
 * - la `enc_key` derivada al entrar, mientras se completa el 2FA o la verificación del correo.
 *
 * Cada una se borra al usarse, al empezar otro flujo, al cancelar, al vencer su plazo (con un
 * temporizador, aunque nadie la vuelva a pedir) y si el sistema pide memoria en segundo plano.
 * No se borran solo por salir de la app: el usuario sale a leer el código del correo o del 2FA.
 */
@Singleton
class PendingAuth @Inject constructor(
    private val clock: DeviceClock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    class Registration(val email: String, val userId: UUID, val vaultKey: SecureBytes, val expiresAt: Long)

    class Login(val email: String, val encKey: SecureBytes, val challengeToken: String?, val expiresAt: Long)

    private var registration: Registration? = null
    private var login: Login? = null
    private var expiry: Job? = null

    @Synchronized
    fun setRegistration(email: String, userId: UUID, vaultKey: SecureBytes) {
        clear()
        registration = Registration(email, userId, vaultKey, clock.elapsedRealtimeMillis() + REGISTRATION_TTL)
        expireAfter(REGISTRATION_TTL)
    }

    @Synchronized
    fun setLogin(email: String, encKey: SecureBytes, challengeToken: String?) {
        clear()
        val ttl = if (challengeToken != null) CHALLENGE_TTL else REGISTRATION_TTL
        login = Login(email, encKey, challengeToken, clock.elapsedRealtimeMillis() + ttl)
        expireAfter(ttl)
    }

    /** La registración pendiente de [email], si sigue vigente. Sigue siendo de esta clase hasta [clear]. */
    @Synchronized
    fun registrationFor(email: String): Registration? {
        dropExpired()
        return registration?.takeIf { it.email == email }
    }

    @Synchronized
    fun loginFor(email: String? = null): Login? {
        dropExpired()
        return login?.takeIf { email == null || it.email == email }
    }

    /**
     * Suelta la registración sin borrar su `vault_key`, porque pasa a `VaultSession`. Lo demás
     * pendiente sí se borra.
     */
    @Synchronized
    fun handOverRegistration() {
        registration = null
        clear()
    }

    /** DEC-26: memoria escasa con la app en segundo plano (`TRIM_MEMORY_BACKGROUND` o más). */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) clear()
    }

    /** Borra todas las llaves pendientes. */
    @Synchronized
    fun clear() {
        expiry?.cancel()
        expiry = null
        registration?.vaultKey?.wipe()
        login?.encKey?.wipe()
        registration = null
        login = null
    }

    // Con el monitor tomado. Lo vencido se borra, no solo se oculta.
    private fun dropExpired() {
        val now = clock.elapsedRealtimeMillis()
        if ((registration?.expiresAt ?: Long.MAX_VALUE) <= now || (login?.expiresAt ?: Long.MAX_VALUE) <= now) clear()
    }

    // Con el monitor tomado. `delay` no avanza con el teléfono dormido; al despertar, dropExpired() lo cubre.
    private fun expireAfter(ttl: Long) {
        expiry = scope.launch {
            delay(ttl)
            synchronized(this@PendingAuth) { dropExpired() }
        }
    }

    private companion object {
        /** Lo que dura un código de correo (15 min en el backend falso). */
        const val REGISTRATION_TTL = 15 * 60_000L

        /** El reto del 2FA vale 5 min; se deja un margen para escribir el código. */
        const val CHALLENGE_TTL = 10 * 60_000L
    }
}
