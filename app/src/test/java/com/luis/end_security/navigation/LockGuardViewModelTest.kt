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
        val viewModel = LockGuardViewModel(session, accounts(true))
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertEquals(LockDestination.Unlock, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `sin cuenta lleva a Iniciar sesion`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(false))
        session.unlock(userId, SecureBytes.copyOf(ByteArray(32) { 1 }))
        session.lock()
        assertEquals(LockDestination.Login, withTimeoutOrNull(1_000) { viewModel.lockedEvents.first() })
    }

    @Test
    fun `el bloqueo del arranque no cuenta`() = runTest(dispatcher) {
        val viewModel = LockGuardViewModel(session, accounts(true))
        session.lock()
        assertNull(withTimeoutOrNull(200) { viewModel.lockedEvents.first() })
    }
}
