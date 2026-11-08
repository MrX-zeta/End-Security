package com.luis.end_security.core.datastore

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Reglas del retraso tras contraseñas incorrectas en Desbloquear (DEC-08), sin almacenamiento:
 * - tras [FREE_ATTEMPTS] fallos seguidos se espera [FIRST_LOCKOUT];
 * - cada fallo siguiente duplica la espera, hasta [MAX_LOCKOUT];
 * - un acierto lo reinicia (lo hace quien guarda los fallos).
 */
object UnlockLockout {
    const val FREE_ATTEMPTS = 5
    val FIRST_LOCKOUT = 1.minutes
    val MAX_LOCKOUT = 15.minutes

    /** Espera que toca después de [failures] fallos seguidos. */
    fun durationAfter(failures: Int): Duration {
        if (failures < FREE_ATTEMPTS) return Duration.ZERO
        // A partir de 2^4 = 16 min ya se pasa del tope: no hace falta calcular más (ni desbordar).
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(4)
        return (FIRST_LOCKOUT * (1 shl doublings)).coerceAtMost(MAX_LOCKOUT)
    }

    /**
     * Cuánto falta del bloqueo guardado.
     * - En el mismo arranque del teléfono manda `elapsedRealtime`: cambiar la hora no lo acorta.
     * - Si `elapsedRealtime` es menor que cuando empezó el bloqueo, el teléfono se reinició y
     *   manda el reloj de pared.
     *
     * Nunca devuelve más que la espera que toca a [failures]: así un reloj de pared atrasado
     * tampoco alarga el bloqueo sin límite. Un reinicio poco después de empezar el bloqueo
     * puede no notarse; entonces se espera de más, nunca de menos (como máximo [MAX_LOCKOUT]).
     */
    fun remaining(
        failures: Int,
        lockedUntilWallMillis: Long,
        lockedUntilElapsedMillis: Long,
        nowWallMillis: Long,
        nowElapsedMillis: Long,
    ): Duration {
        val duration = durationAfter(failures)
        if (duration == Duration.ZERO) return Duration.ZERO
        val startedAtElapsed = lockedUntilElapsedMillis - duration.inWholeMilliseconds
        val left = if (nowElapsedMillis >= startedAtElapsed) {
            lockedUntilElapsedMillis - nowElapsedMillis
        } else {
            lockedUntilWallMillis - nowWallMillis
        }
        return left.milliseconds.coerceIn(Duration.ZERO, duration)
    }
}
