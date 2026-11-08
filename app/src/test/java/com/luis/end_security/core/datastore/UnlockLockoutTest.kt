package com.luis.end_security.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UnlockLockoutTest {

    @Test
    fun `los primeros 4 fallos no bloquean`() {
        (0..4).forEach { assertEquals(Duration.ZERO, UnlockLockout.durationAfter(it)) }
    }

    @Test
    fun `5 fallos bloquean 1 min y cada fallo siguiente duplica`() {
        assertEquals(1.minutes, UnlockLockout.durationAfter(5))
        assertEquals(2.minutes, UnlockLockout.durationAfter(6))
        assertEquals(4.minutes, UnlockLockout.durationAfter(7))
        assertEquals(8.minutes, UnlockLockout.durationAfter(8))
    }

    @Test
    fun `el bloqueo no pasa de 15 min ni se desborda`() {
        assertEquals(15.minutes, UnlockLockout.durationAfter(9))
        assertEquals(15.minutes, UnlockLockout.durationAfter(40))
        assertEquals(15.minutes, UnlockLockout.durationAfter(Int.MAX_VALUE))
    }

    // Bloqueo de 2 min (6 fallos) que empezó con el reloj de pared en 1 000 000 y
    // elapsedRealtime en 50 000.
    private val failures = 6
    private val untilWall = 1_000_000L + 120_000L
    private val untilElapsed = 50_000L + 120_000L

    private fun remaining(nowWall: Long, nowElapsed: Long) =
        UnlockLockout.remaining(failures, untilWall, untilElapsed, nowWall, nowElapsed)

    @Test
    fun `en el mismo arranque manda elapsedRealtime`() {
        assertEquals(90.seconds, remaining(nowWall = 1_030_000L, nowElapsed = 80_000L))
        assertEquals(Duration.ZERO, remaining(nowWall = 1_200_000L, nowElapsed = 170_000L))
    }

    @Test
    fun `adelantar el reloj de pared no acorta el bloqueo`() {
        // Pasaron 30 s, pero el usuario adelantó la hora un día.
        assertEquals(90.seconds, remaining(nowWall = 1_030_000L + 86_400_000L, nowElapsed = 80_000L))
    }

    @Test
    fun `atrasar el reloj de pared tampoco lo alarga`() {
        assertEquals(90.seconds, remaining(nowWall = 1_030_000L - 86_400_000L, nowElapsed = 80_000L))
    }

    @Test
    fun `tras un reinicio manda el reloj de pared`() {
        // elapsedRealtime volvió a empezar (menor que cuando empezó el bloqueo).
        assertEquals(60.seconds, remaining(nowWall = 1_060_000L, nowElapsed = 10_000L))
        assertEquals(Duration.ZERO, remaining(nowWall = 1_200_000L, nowElapsed = 10_000L))
    }

    @Test
    fun `tras un reinicio con la hora atrasada no se pasa de la espera que toca`() {
        assertEquals(2.minutes, remaining(nowWall = 1_000_000L - 86_400_000L, nowElapsed = 10_000L))
    }

    @Test
    fun `sin fallos suficientes no hay bloqueo aunque haya fechas guardadas`() {
        assertEquals(Duration.ZERO, UnlockLockout.remaining(4, untilWall, untilElapsed, 1_000_000L, 50_000L))
    }
}
