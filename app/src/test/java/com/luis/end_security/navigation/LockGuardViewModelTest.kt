package com.luis.end_security.navigation

import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.account.domain.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LockGuardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = VaultSession(object : DeviceClock {
        override fun wallMillis() = 0L
        override fun elapsedRealtimeMillis() = 0L
    })
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun accounts(has: Boolean) = object : AccountRepository {
        override suspend fun hasLocalAccount() = has
    }

    @Test
    fun `bloquear con cuenta lleva a Desbloquear`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(true), com.luis.end_security.core.session.AccountDeletionGate())
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertEquals(LockDestination.Unlock, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `sin cuenta lleva a Iniciar sesion`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(false), com.luis.end_security.core.session.AccountDeletionGate())
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertEquals(LockDestination.Login, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `el bloqueo del arranque no cuenta`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(true), com.luis.end_security.core.session.AccountDeletionGate())
        session.lock()
        assertNull(withTimeoutOrNull(200) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `una pantalla protegida restaurada ya bloqueada lleva a Desbloquear`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(true), com.luis.end_security.core.session.AccountDeletionGate())
        viewModel.onProtectedScreenLocked()
        assertEquals(LockDestination.Unlock, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `eliminando la cuenta el bloqueo de la limpieza no navega`() = runTest(dispatcher) {
        val gate = com.luis.end_security.core.session.AccountDeletionGate()
        val viewModel = LockGuardViewModel(session, accounts(false), gate)
        gate.begin()
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertNull(withTimeoutOrNull(200) { viewModel.lockedEvents.first() })
        // Tampoco la pantalla protegida que se queda sin bóveda mientras tanto.
        viewModel.onProtectedScreenLocked()
        assertNull(withTimeoutOrNull(200) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `al terminar la eliminacion los bloqueos vuelven a navegar`() = runTest(dispatcher) {
        val gate = com.luis.end_security.core.session.AccountDeletionGate()
        val viewModel = LockGuardViewModel(session, accounts(true), gate)
        gate.begin()
        gate.end()
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertEquals(LockDestination.Unlock, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `la cuenta eliminada se avisa aunque los bloqueos esten suprimidos y la puerta no se cierra de golpe`() = runTest(dispatcher) {
        val gate = com.luis.end_security.core.session.AccountDeletionGate()
        val viewModel = LockGuardViewModel(session, accounts(false), gate)
        gate.begin()
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertNull("el bloqueo de la limpieza no navega", withTimeoutOrNull(200) { viewModel.lockedEvents.first() })
        gate.finish()
        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.accountDeleted.first() })
        viewModel.onAccountDeletedHandled()
        // Se reabre un momento después, no de inmediato (el colector del bloqueo de la limpieza es asíncrono). Que se reabre
        // de verdad lo comprueba `LockGuardAccountDeletedTest`, con el reloj real.
        assertEquals(true, gate.active)
    }
}
