package com.luis.end_security.feature.unlock.domain

import kotlinx.coroutines.flow.Flow
import javax.crypto.Cipher

/** Qué dice el teléfono de la biometría fuerte (huella): `BIOMETRIC_STRONG`, nunca el PIN del teléfono. */
enum class BiometricStatus {
    /** Hay sensor y al menos una huella registrada. */
    Available,

    /** No hay sensor: la fila de Ajustes no se muestra. */
    NoHardware,

    /** Hay sensor pero ninguna huella registrada. */
    NoneEnrolled,

    /** El sensor no se puede usar ahora (ocupado, bloqueado, sin actualizar…). */
    Unavailable,
}

interface BiometricAvailability {
    fun status(): BiometricStatus
}

/** Resultado de preparar la llave. */
sealed interface BiometricPreparation {
    /** Listo: se muestra el `BiometricPrompt` con este `Cipher` (el Keystore exige la huella en ese uso). */
    class Ready(val cipher: Cipher) : BiometricPreparation

    /** No hay huellas registradas: no se activa. */
    data object NoneEnrolled : BiometricPreparation

    /** No se pudo (sin sensor, sin cuenta abierta, error del Keystore). */
    data object Failed : BiometricPreparation

    /** Solo al desbloquear: la huella cambió; ya se desactivó todo. */
    data object Invalidated : BiometricPreparation

    /** Solo al desbloquear: la huella no está activada. */
    data object NotEnabled : BiometricPreparation
}

enum class BiometricResult { Done, Invalidated, Failed }

/**
 * Desbloqueo con huella (DEC-09, OP-35 y OP-20). La `vault_key` se guarda envuelta con una llave del Keystore que
 * exige la huella en cada uso; el `Cipher` autenticado lo entrega la pantalla tras el `BiometricPrompt`.
 * **No** toca los intentos fallidos de la contraseña.
 */
interface BiometricUnlock {
    /** Si la huella está activada (`biometric_enabled`). */
    val enabled: Flow<Boolean>

    /** OP-35, paso 1: con la bóveda abierta, crea la llave y devuelve el `Cipher` de cifrado. */
    suspend fun prepareEnable(): BiometricPreparation

    /** OP-35, paso 2: con el `Cipher` ya autenticado, envuelve la `vault_key` y deja la huella activada. */
    suspend fun completeEnable(cipher: Cipher): BiometricResult

    /** OP-35: borra la llave y el envoltorio y apaga la opción. Sin confirmación. */
    suspend fun disable()

    /** OP-20, paso 1: `Cipher` de descifrado para el `BiometricPrompt`, o por qué no se puede. */
    suspend fun prepareUnlock(): BiometricPreparation

    /** OP-20, paso 2: con el `Cipher` ya autenticado, abre la bóveda. */
    suspend fun completeUnlock(cipher: Cipher): BiometricResult
}
