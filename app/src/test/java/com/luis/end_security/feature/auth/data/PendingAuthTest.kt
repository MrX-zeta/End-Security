package com.luis.end_security.feature.auth.data

import android.content.ComponentCallbacks2
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PendingAuthTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    /** El reloj del teléfono es el reloj virtual del planificador. */
    private val clock = object : DeviceClock {
        override fun wallMillis() = dispatcher.scheduler.currentTime
        override fun elapsedRealtimeMillis() = dispatcher.scheduler.currentTime
    }
    private val pending = PendingAuth(clock, scope)
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")

    private fun key() = SecureBytes.copyOf(ByteArray(32) { 7 })

    private fun SecureBytes.isWiped() = runCatching { withBytes { } }.isFailure

    @Test
    fun `la llave del registro se borra sola a los 15 min aunque nadie la pida`() = scope.runTest {
        val vaultKey = key()
        pending.setRegistration("ana@correo.com", userId, vaultKey)
        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertFalse(vaultKey.isWiped())

        advanceTimeBy(1)
        runCurrent()
        assertThrows(IllegalStateException::class.java) { vaultKey.withBytes { } }
        assertNull(pending.registrationFor("ana@correo.com"))
    }

    @Test
    fun `la enc_key del 2FA se borra sola a los 10 min`() = scope.runTest {
        val encKey = key()
        pending.setLogin("ana@correo.com", encKey, challengeToken = "reto")
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertNull(pending.loginFor())
        assertEquals(true, encKey.isWiped())
    }

    @Test
    fun `memoria escasa en segundo plano borra lo pendiente`() {
        val encKey = key()
        pending.setLogin("ana@correo.com", encKey, challengeToken = "reto")
        @Suppress("DEPRECATION")
        pending.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertNotNull("ocultar la UI no basta: el usuario fue por su código", pending.loginFor())
        pending.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertNull(pending.loginFor())
        assertEquals(true, encKey.isWiped())
    }

    @Test
    fun `entregar la registracion no borra la llave que pasa a la sesion`() {
        val vaultKey = key()
        pending.setRegistration("ana@correo.com", userId, vaultKey)
        assertSame(vaultKey, pending.registrationFor("ana@correo.com")?.vaultKey)
        pending.handOverRegistration()
        assertFalse(vaultKey.isWiped())
        assertNull(pending.registrationFor("ana@correo.com"))
        vaultKey.close()
    }

    @Test
    fun `empezar otro flujo borra el anterior`() {
        val first = key()
        pending.setLogin("ana@correo.com", first, challengeToken = "reto")
        pending.setRegistration("otra@correo.com", userId, key())
        assertEquals(true, first.isWiped())
        assertNull(pending.loginFor())
    }
}
