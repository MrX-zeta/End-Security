package com.luis.end_security.feature.vault.ui

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.vault.domain.VaultSyncRepository
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
class VaultViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = VaultSession(object : DeviceClock {
        override fun wallMillis() = 0L
        override fun elapsedRealtimeMillis() = 0L
    })
    private val auth = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        session.unlock(UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90"), SecureBytes.copyOf(ByteArray(32) { 1 }))
    }

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private class FakeSync(private val result: ApiResult<Unit>) : VaultSyncRepository {
        var downloads = 0

        override suspend fun initialDownload(): ApiResult<Unit> {
            downloads++
            return result
        }
    }

    @Test
    fun `cargando descarga una vez y avisa al terminar`() = runTest(dispatcher) {
        val sync = FakeSync(ApiResult.Success(Unit))
        val viewModel = VaultLoadingViewModel(sync)
        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.done.first() })
        assertEquals(1, sync.downloads)
    }

    @Test
    fun `sin red tambien sigue a la boveda con lo que haya en Room`() = runTest(dispatcher) {
        val viewModel = VaultLoadingViewModel(FakeSync(ApiResult.Failure(ApiError.NetworkUnavailable)))
        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.done.first() })
    }

    @Test
    fun `la boveda muestra los codigos de respaldo que quedan una sola vez`() {
        auth.recoveryCodesNotice = 2
        assertEquals(2, VaultViewModel(auth, session).state.value.recoveryCodesLeft)
        assertNull(VaultViewModel(auth, session).state.value.recoveryCodesLeft)
    }

    @Test
    fun `al bloquearse la boveda vacia su estado`() {
        auth.recoveryCodesNotice = 1
        val viewModel = VaultViewModel(auth, session)
        session.lock()
        assertNull(viewModel.state.value.recoveryCodesLeft)
    }
}
