package com.luis.end_security.core.session

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockerTest {

    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val minutes = MutableStateFlow(5)
    private val settings = object : AutoLockSettings {
        override val autolockMinutes = minutes
    }

    /** elapsedRealtime falso: avanza con el reloj virtual y además se puede saltar (sueño, congelado). */
    private class FakeClock(private val scope: TestScope) : DeviceClock {
        var extra = 0L
        override fun wallMillis() = 0L
        override fun elapsedRealtimeMillis() = scope.testScheduler.currentTime + extra
    }

    private lateinit var session: VaultSession

    /** La app en primer plano con la bóveda abierta. */
    private fun TestScope.newLocker(clock: FakeClock = FakeClock(this), unlocked: Boolean = true): AutoLocker {
        session = VaultSession(clock)
        val locker = AutoLocker(session, settings, clock, backgroundScope)
        locker.onForeground()
        if (unlocked) session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 3 }))
        runCurrent()
        return locker
    }

    @Test
    fun `a los 4_59 no bloquea y a los 5_00 si`() = runTest {
        val locker = newLocker()
        locker.onBackground()
        advanceTimeBy(4 * 60_000L + 59_000L)
        runCurrent()
        assertEquals(VaultState.Unlocked(userId), session.state.value)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `volver a los 4_59 cancela el bloqueo`() = runTest {
        val locker = newLocker()
        locker.onBackground()
        advanceTimeBy(4 * 60_000L + 59_000L)
        locker.onForeground()
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(VaultState.Unlocked(userId), session.state.value)
    }

    @Test
    fun `volver despues del plazo bloquea aunque el temporizador no corriera`() = runTest {
        val clock = FakeClock(this)
        val locker = newLocker(clock)
        locker.onBackground()
        // El proceso estuvo congelado: elapsedRealtime avanzó 6 min, el reloj de las corrutinas no.
        clock.extra = 6 * 60_000L
        locker.onForeground()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `con 0 bloquea al pasar a segundo plano`() = runTest {
        minutes.value = 0
        val locker = newLocker()
        locker.onBackground()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `el ajuste se lee del DataStore, con 1 minuto bloquea al minuto`() = runTest {
        minutes.value = 1
        val locker = newLocker()
        locker.onBackground()
        advanceTimeBy(60_001L)
        runCurrent()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `bloqueada no programa nada`() = runTest {
        val locker = newLocker()
        session.lock()
        locker.onBackground()
        locker.onForeground()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `si se desbloquea con la app ya en segundo plano, la cuenta empieza al desbloquear`() = runTest {
        val locker = newLocker(unlocked = false)
        locker.onBackground()
        advanceTimeBy(1_000L)
        // El desbloqueo terminó después de salir de la app.
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 3 }))
        runCurrent()
        advanceTimeBy(5 * 60_000L + 1L)
        runCurrent()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `desbloquear en segundo plano con 0 bloquea en el acto`() = runTest {
        minutes.value = 0
        val locker = newLocker(unlocked = false)
        locker.onBackground()
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 3 }))
        runCurrent()
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun `un valor fuera de las opciones se trata como 5 minutos`() = runTest {
        minutes.value = 100_000
        val locker = newLocker()
        locker.onBackground()
        advanceTimeBy(5 * 60_000L + 1L)
        runCurrent()
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals(5, AutoLockSettings.sanitize(-3))
        assertEquals(15, AutoLockSettings.sanitize(15))
    }

    @Test
    fun `el plazo queda en la sesion aunque el temporizador no corra`() = runTest {
        val clock = FakeClock(this)
        val locker = newLocker(clock)
        locker.onBackground()
        clock.extra = 6 * 60_000L
        // Un trabajo en segundo plano despierta el proceso pasado el plazo.
        assertThrows(VaultLockedException::class.java) { session.withVaultKey { it.size } }
        assertEquals(VaultState.Locked, session.state.value)
        locker.onForeground()
    }
}
