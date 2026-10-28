package com.luis.end_security.feature.health.ui

import androidx.lifecycle.SavedStateHandle
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthReport
import com.luis.end_security.feature.health.domain.HealthState
import com.luis.end_security.feature.vault.ui.FakeHealthRepository
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.openedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = TestClock()
    private val session = openedSession(clock)
    private val health = FakeHealthRepository()
    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        session.lock()
        Dispatchers.resetMain()
    }

    private fun item(name: String, password: String, breach: Int? = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, Instant.ofEpochMilli(clock.now), breach)

    private fun report(vararg pairs: Pair<String, String>): HealthReport =
        HealthAnalyzer.analyze(pairs.map { (name, pass) -> item(name, pass) }, Instant.ofEpochMilli(clock.now), estimator)

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): HealthViewModel {
        val viewModel = HealthViewModel(handle, health, session, clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `al abrir pide el analisis y mientras no hay resultado esta cargando`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLoading)
        assertEquals("abrir Salud pide el análisis (y HIBP la primera vez)", 1, health.refreshes)
    }

    @Test
    fun `con analisis muestra el resumen`() = runTest(dispatcher) {
        health.set(HealthState(report("A" to "s4-a", "B" to "s0-b"), lastCheckedAt = Instant.ofEpochMilli(clock.now)))
        val state = viewModel().state.value
        assertFalse(state.isLoading)
        assertTrue(state.hasItems)
        assertNotNull(state.report!!.score)
        assertNotNull(state.progress)
    }

    @Test
    fun `la primera revision de HIBP muestra Revisando filtraciones`() = runTest(dispatcher) {
        health.set(HealthState(report("A" to "s4-a"), checking = true, lastCheckedAt = null))
        assertTrue(viewModel().state.value.isLoading)
    }

    @Test
    fun `una revision posterior no vuelve a la pantalla de carga`() = runTest(dispatcher) {
        health.set(HealthState(report("A" to "s4-a"), checking = true, lastCheckedAt = Instant.ofEpochMilli(clock.now)))
        assertFalse(viewModel().state.value.isLoading)
    }

    @Test
    fun `una boveda sin contrasenas no esta cargando y no tiene puntuacion`() = runTest(dispatcher) {
        health.set(HealthState(report()))
        val state = viewModel().state.value
        assertFalse(state.isLoading)
        assertFalse(state.hasItems)
        assertNull(state.report!!.score)
    }

    @Test
    fun `el ranking trae los 10 primeros, mas fuertes y mas debiles`() = runTest(dispatcher) {
        val items = (0..11).map { it.toString() to "s${it % 5}-" + "x".repeat(it + 3) }
        health.set(HealthState(report(*items.toTypedArray())))
        val viewModel = viewModel()

        val strongest = viewModel.state.value.ranking
        assertEquals(10, strongest.size)
        assertTrue(strongest.zipWithNext().all { (a, b) -> a.strengthScore >= b.strengthScore })
        assertEquals(4, strongest.first().strengthScore)

        viewModel.onRankingTab(RankingTab.Weakest)
        runCurrent()
        val weakest = viewModel.state.value.ranking
        assertEquals(10, weakest.size)
        assertTrue(weakest.zipWithNext().all { (a, b) -> a.strengthScore <= b.strengthScore })
        assertEquals(0, weakest.first().strengthScore)
    }

    @Test
    fun `Debiles abre el ranking en Mas debiles y el selector cambia de pestaña`() = runTest(dispatcher) {
        health.set(HealthState(report("A" to "s4-a")))
        val viewModel = viewModel(SavedStateHandle(mapOf(HealthViewModel.RANKING_ARG to "Weakest")))
        assertEquals(RankingTab.Weakest, viewModel.state.value.rankingTab)

        assertEquals(HealthTab.Summary, viewModel.state.value.tab)
        viewModel.onTab(HealthTab.Progress)
        runCurrent()
        assertEquals(HealthTab.Progress, viewModel.state.value.tab)
    }

    @Test
    fun `Revisar ahora fuerza una revision`() = runTest(dispatcher) {
        health.set(HealthState(report("A" to "s4-a")))
        val viewModel = viewModel()
        viewModel.onCheckNow()
        runCurrent()
        assertEquals(listOf(true), health.checks)
    }

    @Test
    fun `el estado no imprime nombres ni contrasenas`() = runTest(dispatcher) {
        health.set(HealthState(report("Banco-Secreto" to "s4-Contrasena-Larga")))
        val state = viewModel().state.value
        val printed = state.toString() + state.ranking.joinToString { it.toString() } + state.report.toString()
        assertFalse(printed.contains("Secreto"))
        assertFalse(printed.contains("Contrasena"))
    }
}
