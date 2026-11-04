package com.luis.end_security.feature.activity.ui

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.activity.FakeSessionApi
import com.luis.end_security.feature.activity.MutableClock
import com.luis.end_security.feature.activity.domain.LastUsed
import com.luis.end_security.feature.activity.domain.RevokeSessions
import com.luis.end_security.feature.activity.session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val api = FakeSessionApi()
    private val now = Instant.parse("2026-11-04T18:00:00Z")
    private val clock = MutableClock(now)

    private val galaxy = session("Galaxy A54", now.minusSeconds(30), current = true)
    private val pixel = session("Pixel 7", now.minus(Duration.ofDays(2)))
    private val moto = session("Moto g84", now.minus(Duration.ofDays(21)))

    private lateinit var viewModel: DevicesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create(vararg sessions: com.luis.end_security.feature.activity.domain.DeviceSession) {
        api.sessions = ApiResult.Success(sessions.toList())
        viewModel = DevicesViewModel(api, RevokeSessions(api), clock)
        runCurrent()
    }

    private val state get() = viewModel.state.value

    // --- Cargar ---

    @Test
    fun `empieza cargando y luego muestra la lista`() = scope.runTest {
        api.sessions = ApiResult.Success(listOf(galaxy))
        viewModel = DevicesViewModel(api, RevokeSessions(api), clock)
        assertEquals(DevicesPhase.Loading, state.phase)
        runCurrent()
        assertEquals(DevicesPhase.Loaded, state.phase)
        assertEquals(1, state.devices.size)
    }

    @Test
    fun `la lista queda ordenada con la actual primero y luego por uso reciente`() = scope.runTest {
        // El servidor las manda en otro orden.
        create(moto, pixel, galaxy)
        assertEquals(listOf("Galaxy A54", "Pixel 7", "Moto g84"), state.devices.map { it.name })
        assertTrue(state.devices[0].current)
        assertFalse(state.devices[1].current)
    }

    @Test
    fun `la actual es Activo ahora y las demas llevan su tiempo relativo`() = scope.runTest {
        val recent = session("Tablet", now.minus(Duration.ofMinutes(3)))
        val oneHour = session("Laptop", now.minus(Duration.ofMinutes(90)))
        create(galaxy, recent, oneHour, pixel, moto)
        val byName = state.devices.associate { it.name to it.lastUsed }
        assertEquals(LastUsed.Now, byName["Galaxy A54"])
        assertEquals(LastUsed.Now, byName["Tablet"])
        assertEquals(LastUsed.Hours(1), byName["Laptop"])
        assertEquals(LastUsed.Days(2), byName["Pixel 7"])
        assertEquals(LastUsed.Weeks(3), byName["Moto g84"])
    }

    @Test
    fun `sin red muestra Sin conexion y Reintentar vuelve a cargar`() = scope.runTest {
        api.sessions = ApiResult.Failure(ApiError.NetworkUnavailable)
        viewModel = DevicesViewModel(api, RevokeSessions(api), clock)
        runCurrent()
        assertEquals(DevicesPhase.Offline, state.phase)
        api.sessions = ApiResult.Success(listOf(galaxy, pixel))
        viewModel.onAction(DevicesAction.Retry)
        assertEquals(DevicesPhase.Loading, state.phase)
        runCurrent()
        assertEquals(DevicesPhase.Loaded, state.phase)
        assertEquals(2, state.devices.size)
    }

    // --- Cerrar uno ---

    @Test
    fun `tocar la actual no abre nada`() = scope.runTest {
        create(galaxy, pixel)
        viewModel.onAction(DevicesAction.RowClicked(galaxy.id))
        assertNull(state.sheet)
    }

    @Test
    fun `tocar otra abre la hoja con su nombre`() = scope.runTest {
        create(galaxy, pixel)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        assertEquals(DevicesSheet.CloseOne(pixel.id, "Pixel 7"), state.sheet)
    }

    @Test
    fun `cancelar la hoja no cierra nada`() = scope.runTest {
        create(galaxy, pixel)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetDismissed)
        runCurrent()
        assertNull(state.sheet)
        assertTrue(api.revoked.isEmpty())
        assertEquals(2, state.devices.size)
    }

    @Test
    fun `confirmar cierra solo esa sesion, la quita de la lista y avisa`() = scope.runTest {
        create(galaxy, pixel, moto)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertEquals(listOf(pixel.id), api.revoked)
        assertEquals(listOf("Galaxy A54", "Moto g84"), state.devices.map { it.name })
        assertNull(state.sheet)
        assertFalse(state.isClosing)
        assertEquals(DevicesNotice.Closed("Pixel 7", 1), state.notice)
    }

    @Test
    fun `si falla cerrar una, sigue en la lista y avisa`() = scope.runTest {
        create(galaxy, pixel)
        api.revokeResults[pixel.id] = ApiResult.Failure(ApiError.NetworkUnavailable)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertEquals(2, state.devices.size)
        assertEquals(DevicesNotice.OneFailed("Pixel 7", 1), state.notice)
        assertNull(state.sheet)
    }

    @Test
    fun `mientras se cierra la hoja no se puede cerrar ni confirmar otra vez`() = scope.runTest {
        create(galaxy, pixel)
        val gate = CompletableDeferred<Unit>()
        api.revokeGate = gate
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertTrue(state.isClosing)
        viewModel.onAction(DevicesAction.SheetDismissed)
        viewModel.onAction(DevicesAction.SheetConfirmed)
        viewModel.onAction(DevicesAction.RowClicked(galaxy.id))
        assertEquals("la hoja sigue abierta", DevicesSheet.CloseOne(pixel.id, "Pixel 7"), state.sheet)
        gate.complete(Unit)
        runCurrent()
        assertEquals("una sola llamada", listOf(pixel.id), api.revoked)
        assertFalse(state.isClosing)
    }

    @Test
    fun `una sesion que el servidor ya no tenia tambien sale de la lista`() = scope.runTest {
        create(galaxy, pixel)
        api.revokeResults[pixel.id] = ApiResult.Failure(ApiError.NotFound)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertEquals(listOf("Galaxy A54"), state.devices.map { it.name })
        assertTrue(state.notice is DevicesNotice.Closed)
    }

    // --- Cerrar los demás ---

    @Test
    fun `con un solo dispositivo no se puede cerrar los demas`() = scope.runTest {
        create(galaxy)
        assertFalse(state.canCloseOthers)
        viewModel.onAction(DevicesAction.CloseOthersClicked)
        assertNull(state.sheet)
    }

    @Test
    fun `cerrar los demas con exito deja solo este telefono`() = scope.runTest {
        create(galaxy, pixel, moto)
        assertTrue(state.canCloseOthers)
        viewModel.onAction(DevicesAction.CloseOthersClicked)
        assertEquals(DevicesSheet.CloseOthers, state.sheet)
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        // En serie y nunca la sesión propia.
        assertEquals(listOf(pixel.id, moto.id), api.revoked)
        assertEquals(listOf("Galaxy A54"), state.devices.map { it.name })
        assertEquals(DevicesNotice.OthersClosed(1), state.notice)
        assertFalse(state.canCloseOthers)
    }

    @Test
    fun `cerrar los demas con exito parcial deja en la lista los que no se cerraron`() = scope.runTest {
        val tablet = session("Tablet", now.minus(Duration.ofDays(5)))
        create(galaxy, pixel, moto, tablet)
        api.revokeResults[moto.id] = ApiResult.Failure(ApiError.ServerError)
        api.revokeResults[tablet.id] = ApiResult.Failure(ApiError.NetworkUnavailable)
        viewModel.onAction(DevicesAction.CloseOthersClicked)
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertEquals(listOf(pixel.id, tablet.id, moto.id).sortedBy { it.toString() }, api.revoked.sortedBy { it.toString() })
        assertEquals(setOf("Galaxy A54", "Moto g84", "Tablet"), state.devices.map { it.name }.toSet())
        assertEquals(DevicesNotice.SomeFailed(2, 1), state.notice)
        assertTrue("todavía hay otros que cerrar", state.canCloseOthers)
    }

    @Test
    fun `cada aviso lleva un id distinto para que dos iguales se vuelvan a mostrar`() = scope.runTest {
        create(galaxy, pixel, moto)
        api.revokeResults[pixel.id] = ApiResult.Failure(ApiError.NetworkUnavailable)
        api.revokeResults[moto.id] = ApiResult.Failure(ApiError.NetworkUnavailable)
        viewModel.onAction(DevicesAction.RowClicked(pixel.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        val first = state.notice
        viewModel.onAction(DevicesAction.RowClicked(moto.id))
        viewModel.onAction(DevicesAction.SheetConfirmed)
        runCurrent()
        assertTrue(first!!.id != state.notice!!.id)
    }

    @Test
    fun `el estado no guarda nada secreto en su toString`() = scope.runTest {
        create(galaxy, pixel)
        // Solo nombres y horas: nada de tokens ni identificadores de sesión de otros más allá del id de fila.
        assertFalse(state.toString().contains("token", ignoreCase = true))
    }
}
