package com.luis.end_security.core.session

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.SecureBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Estado de la bóveda en este proceso. No lleva la llave: solo si está abierta y de quién. */
sealed interface VaultState {
    data object Locked : VaultState

    data class Unlocked(val userId: UUID) : VaultState
}

/** Se pidió la `vault_key` con la bóveda bloqueada (o se bloqueó mientras se usaba). */
class VaultLockedException : RuntimeException()

/**
 * La bóveda abierta (DEC-05, DEC-27, INV-07): la `vault_key` vive solo aquí, en memoria, dentro
 * de un [SecureBytes] privado, y se borra al bloquear. Nunca va en el [state], en un
 * `SavedStateHandle` ni en disco.
 *
 * - [unlock] se queda con el [SecureBytes] que recibe (no lo copia): quien llama ya no debe usarlo.
 * - [withVaultKey] presta la llave **solo para leer** ([SecretReader]) a un bloque corto. Como la
 *   lectura y el borrado de [SecureBytes] se excluyen, [lock] espera a que termine un cifrado en
 *   curso; si el bloqueo llega antes, el bloque recibe [VaultLockedException], nunca ceros.
 * - El autobloqueo deja un plazo ([setLockDeadline]); [withVaultKey] lo revisa antes de prestar
 *   la llave. Así, aunque el temporizador no corra (teléfono dormido, app congelada), un trabajo
 *   que despierte el proceso pasado el plazo encuentra la bóveda bloqueada.
 */
@Singleton
class VaultSession @Inject constructor(
    private val clock: DeviceClock,
) {

    private val _state = MutableStateFlow<VaultState>(VaultState.Locked)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    // Protegidos por el monitor de esta instancia.
    private var vaultKey: SecureBytes? = null
    private var lockDeadline: Long? = null

    /**
     * Abre la bóveda. Con otra cuenta abierta no se permite: primero [lock], para que la
     * navegación vea el paso por [VaultState.Locked].
     */
    fun unlock(userId: UUID, vaultKey: SecureBytes) {
        require(!vaultKey.isWiped) { "La vault_key ya está borrada" }
        val previous = synchronized(this) {
            val current = _state.value
            check(current !is VaultState.Unlocked || current.userId == userId) { "Hay otra cuenta abierta" }
            val old = this.vaultKey
            this.vaultKey = vaultKey
            lockDeadline = null
            _state.value = VaultState.Unlocked(userId)
            old
        }
        if (previous !== vaultKey) previous?.wipe()
    }

    /** Borra la llave y pasa a [VaultState.Locked]. Se puede llamar aunque ya esté bloqueada. */
    fun lock() {
        val key = synchronized(this) {
            val old = vaultKey
            vaultKey = null
            lockDeadline = null
            _state.value = VaultState.Locked
            old
        }
        // Fuera del monitor: wipe() espera a que termine un withBytes en curso, y ese bloque no
        // debe quedarse esperando el monitor.
        key?.wipe()
    }

    /** Plazo de autobloqueo en `elapsedRealtime` (ms), o `null` sin plazo (app en primer plano). */
    fun setLockDeadline(elapsedRealtimeMillis: Long?) {
        synchronized(this) {
            if (vaultKey != null) lockDeadline = elapsedRealtimeMillis
        }
    }

    /** @throws VaultLockedException si está bloqueada, venció el plazo o se bloquea mientras corre [block]. */
    fun <R> withVaultKey(block: (SecretReader) -> R): R {
        val (key, expired) = synchronized(this) {
            val deadline = lockDeadline
            vaultKey to (deadline != null && clock.elapsedRealtimeMillis() >= deadline)
        }
        if (key == null) throw VaultLockedException()
        if (expired) {
            lock()
            throw VaultLockedException()
        }
        try {
            return block(ReadOnlyKey(key))
        } catch (error: IllegalStateException) {
            // SecureBytes lanza IllegalStateException si se lee después de borrarse.
            if (key.isWiped) {
                if (state.value is VaultState.Unlocked) lock()
                throw VaultLockedException()
            }
            throw error
        }
    }

    /** Envoltura de solo lectura: ni con un cast se puede borrar o cerrar la llave de la sesión. */
    private class ReadOnlyKey(private val key: SecureBytes) : SecretReader {
        override val size: Int get() = key.size

        override fun <R> withBytes(block: (ByteArray) -> R): R = key.withBytes(block)

        override fun toString(): String = "SecretReader(***)"
    }
}
