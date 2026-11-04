package com.luis.end_security.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.session.AccountDeletionGate
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.account.domain.AccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * El cableado que faltaba (revisión de la tarea 20): `LockGuard` vive en `AppNavHost`, fuera de la pantalla de Eliminar, y navega
 * a «Cuenta eliminada» aunque esa pantalla ya haya salido de la composición porque la limpieza bloqueó la bóveda.
 */
@RunWith(AndroidJUnit4::class)
class LockGuardAccountDeletedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = VaultSession(object : DeviceClock {
        override fun wallMillis() = 0L

        override fun elapsedRealtimeMillis() = 0L
    })
    private val gate = AccountDeletionGate()
    private val accounts = object : AccountRepository {
        override suspend fun hasLocalAccount() = false
    }

    @Test
    fun alTerminarLaLimpiezaNavegaACuentaEliminadaYReabreLaPuerta() {
        val viewModel = LockGuardViewModel(session, accounts, gate)
        var locked = 0
        var deleted = 0
        composeRule.setContent {
            LockGuard(onLocked = { locked++ }, onAccountDeleted = { deleted++ }, viewModel = viewModel)
        }
        // Lo que hace `DeleteAccountImpl` tras el 204: abre la puerta, la limpieza bloquea la bóveda y avisa al terminar.
        session.unlock(UUID.randomUUID(), SecureBytes.copyOf(ByteArray(32) { 1 }))
        composeRule.waitForIdle()
        gate.begin()
        session.lock()
        gate.finish()
        composeRule.waitUntil(5_000) { deleted == 1 }
        composeRule.waitUntil(5_000) { !gate.active }
        assertEquals("el bloqueo de la limpieza no mandó a Iniciar sesión ni a Desbloquear", 0, locked)
        assertEquals(1, deleted)
        assertFalse("la puerta se reabrió tras navegar", gate.active)
    }

    @Test
    fun unBloqueoNormalSigueMandandoAIniciarSesionSinCuenta() {
        val viewModel = LockGuardViewModel(session, accounts, gate)
        var destination: LockDestination? = null
        composeRule.setContent { LockGuard(onLocked = { destination = it }, viewModel = viewModel) }
        session.unlock(UUID.randomUUID(), SecureBytes.copyOf(ByteArray(32) { 1 }))
        // Sin esperar, el desbloqueo y el bloqueo seguidos se fusionan en el StateFlow y el guardia nunca ve «abierta».
        composeRule.waitForIdle()
        session.lock()
        composeRule.waitUntil(5_000) { destination != null }
        assertTrue(destination == LockDestination.Login)
    }
}
