package com.luis.end_security.feature.activity.ui

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.feature.activity.FakeSessionApi
import com.luis.end_security.feature.activity.FixedZone
import com.luis.end_security.feature.activity.MutableClock
import com.luis.end_security.feature.activity.domain.DayLabel
import com.luis.end_security.feature.activity.event
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val api = FakeSessionApi()
    private val mexico = ZoneId.of("America/Mexico_City")
    private val zone = FixedZone(mexico)

    // 10:45 del 4 de noviembre en Ciudad de México.
    private val clock = MutableClock(Instant.parse("2026-11-04T16:45:00Z"))

    private lateinit var viewModel: ActivityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = ActivityViewModel(api, clock, zone)
        runCurrent()
    }

    private val state get() = viewModel.state.value

    /** [count] eventos con ids 1..count, uno por hora hacia atrás desde las 10:00 locales del día 4. */
    private fun manyEvents(count: Int) = (1..count).map {
        event(it.toLong(), Instant.parse("2026-11-04T16:00:00Z").minusSeconds(3_600L * (count - it)))
    }

    // --- Primera página ---

    @Test
    fun `empieza cargando y luego muestra los eventos agrupados`() = scope.runTest {
        api.events = manyEvents(3)
        viewModel = ActivityViewModel(api, clock, zone)
        assertEquals(ActivityPhase.Loading, state.phase)
        runCurrent()
        assertEquals(ActivityPhase.Loaded, state.phase)
        assertEquals(3, state.events.size)
        assertEquals(listOf(3L, 2L, 1L), state.events.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun `agrupa por dia local y un evento a las 2359 y otro a las 0001 son dias distintos`() = scope.runTest {
        api.events = listOf(
            event(4, Instant.parse("2026-11-04T16:30:00Z")), // 10:30 del 4
            event(3, Instant.parse("2026-11-04T06:01:00Z")), // 00:01 del 4
            event(2, Instant.parse("2026-11-04T05:59:00Z")), // 23:59 del 3
            event(1, Instant.parse("2026-11-03T20:00:00Z")), // 14:00 del 3
        )
        create()
        assertEquals(listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3)), state.days.map { it.date })
        assertEquals(listOf(4L, 3L), state.days[0].events.map { it.id })
        assertEquals(listOf(2L, 1L), state.days[1].events.map { it.id })
        assertEquals(LocalDate.of(2026, 11, 4), state.today)
        assertEquals(DayLabel.Today, DayLabel.of(state.days[0].date, state.today))
        assertEquals(DayLabel.Yesterday, DayLabel.of(state.days[1].date, state.today))
    }

    @Test
    fun `al pasar la medianoche local Hoy pasa a ser Ayer`() = scope.runTest {
        api.events = listOf(event(2, Instant.parse("2026-11-04T16:30:00Z")), event(1, Instant.parse("2026-11-03T20:00:00Z")))
        // 23:50 del 4 (hora local) = 05:50Z del 5.
        clock.now = Instant.parse("2026-11-05T05:50:00Z")
        create()
        assertEquals(LocalDate.of(2026, 11, 4), state.today)
        assertEquals(DayLabel.Today, DayLabel.of(state.days[0].date, state.today))
        // Pasan 20 minutos: 00:10 del 5.
        clock.now = Instant.parse("2026-11-05T06:10:00Z")
        viewModel.onAction(ActivityAction.DayChanged)
        assertEquals(LocalDate.of(2026, 11, 5), state.today)
        assertEquals(DayLabel.Yesterday, DayLabel.of(state.days[0].date, state.today))
        assertEquals(DayLabel.Date(LocalDate.of(2026, 11, 3)), DayLabel.of(state.days[1].date, state.today))
    }

    @Test
    fun `un cambio de zona horaria reagrupa al avisar del cambio de dia`() = scope.runTest {
        api.events = listOf(event(1, Instant.parse("2026-11-04T03:00:00Z")))
        create()
        assertEquals(LocalDate.of(2026, 11, 3), state.days.single().date)
        zone.zone = ZoneId.of("UTC")
        viewModel.onAction(ActivityAction.DayChanged)
        assertEquals(LocalDate.of(2026, 11, 4), state.days.single().date)
    }

    @Test
    fun `los fallidos y los tipos desconocidos llegan tal cual a la pantalla`() = scope.runTest {
        api.events = listOf(
            event(3, Instant.parse("2026-11-04T16:30:00Z"), type = "login", success = false, device = null),
            event(2, Instant.parse("2026-11-04T16:20:00Z"), type = "evento_del_futuro"),
            event(1, Instant.parse("2026-11-04T16:10:00Z"), type = "two_factor_enabled"),
        )
        create()
        val (failed, unknown, ok) = state.events
        assertFalse(failed.success)
        assertEquals(null, failed.deviceName)
        assertEquals(com.luis.end_security.feature.activity.domain.ActivityKind.Unknown, unknown.kind)
        assertTrue(ok.success)
    }

    // --- Paginación ---

    @Test
    fun `con mas de una pagina pide la siguiente con before igual al id del ultimo`() = scope.runTest {
        api.events = manyEvents(120)
        create()
        assertEquals(50, state.events.size)
        assertTrue(state.hasMore)
        assertEquals(listOf<Long?>(null), api.activityCalls)
        assertEquals(71L, state.events.last().id)
        viewModel.onAction(ActivityAction.LoadMore)
        assertTrue(state.loadingMore)
        runCurrent()
        assertEquals(listOf<Long?>(null, 71L), api.activityCalls)
        assertEquals(100, state.events.size)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        assertEquals(120, state.events.size)
        assertFalse(state.hasMore)
        assertEquals((1L..120L).toList().reversed(), state.events.map { it.id })
    }

    @Test
    fun `una pagina que repite eventos no los duplica`() = scope.runTest {
        api.events = manyEvents(70)
        api.overlap = 3
        create()
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        val ids = state.events.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(70, ids.size)
        assertFalse(state.hasMore)
    }

    @Test
    fun `no pide otra pagina mientras una esta en curso ni cuando no hay mas`() = scope.runTest {
        api.events = manyEvents(120)
        create()
        viewModel.onAction(ActivityAction.LoadMore)
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        assertEquals("una sola llamada para la segunda página", 2, api.activityCalls.size)
        api.events = manyEvents(10)
        create()
        val before = api.activityCalls.size
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        assertEquals(before, api.activityCalls.size)
    }

    @Test
    fun `si la pagina siguiente falla no se vuelve a pedir sola y Reintentar la pide`() = scope.runTest {
        api.events = manyEvents(120)
        create()
        api.activityError = ApiError.NetworkUnavailable
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        assertTrue(state.loadMoreFailed)
        assertFalse(state.loadingMore)
        assertEquals("la lista ya cargada se conserva", 50, state.events.size)
        viewModel.onAction(ActivityAction.LoadMore)
        runCurrent()
        assertEquals("no se repite sola", 2, api.activityCalls.size)
        api.activityError = null
        viewModel.onAction(ActivityAction.Retry)
        runCurrent()
        assertFalse(state.loadMoreFailed)
        assertEquals(100, state.events.size)
    }

    // --- Sin conexión ---

    @Test
    fun `sin red la primera carga muestra Sin conexion y Reintentar vuelve a pedirla`() = scope.runTest {
        api.events = manyEvents(3)
        api.activityError = ApiError.NetworkUnavailable
        create()
        assertEquals(ActivityPhase.Offline, state.phase)
        assertTrue(state.events.isEmpty())
        api.activityError = null
        viewModel.onAction(ActivityAction.Retry)
        assertEquals(ActivityPhase.Loading, state.phase)
        runCurrent()
        assertEquals(ActivityPhase.Loaded, state.phase)
        assertEquals(3, state.events.size)
    }

    @Test
    fun `una sesion revocada o un error del servidor tambien se muestran como sin conexion`() = scope.runTest {
        api.activityError = ApiError.SessionRevoked
        create()
        assertEquals(ActivityPhase.Offline, state.phase)
    }

    @Test
    fun `el estado no imprime el contenido de los eventos`() = scope.runTest {
        api.events = manyEvents(2)
        create()
        // ActivityEvent es una data class con solo datos de la propia cuenta (tipo, hora, dispositivo): sin tokens ni IP.
        assertFalse(state.toString().contains("token", ignoreCase = true))
    }
}
