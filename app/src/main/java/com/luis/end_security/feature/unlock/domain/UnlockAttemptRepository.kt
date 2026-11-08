package com.luis.end_security.feature.unlock.domain

import kotlin.time.Duration

/** Fallos de Desbloquear y su retraso (DEC-08); sobreviven a cerrar la app. */
interface UnlockAttemptRepository {
    /** Lo que falta del bloqueo; [Duration.ZERO] si se puede intentar. */
    suspend fun remainingLockout(): Duration

    /** Cuenta un fallo y devuelve el bloqueo que empieza ahora ([Duration.ZERO] si no hay). */
    suspend fun recordFailure(): Duration

    /** Contraseña correcta: se olvidan los fallos. */
    suspend fun recordSuccess()
}
