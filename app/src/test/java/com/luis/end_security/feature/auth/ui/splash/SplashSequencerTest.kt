package com.luis.end_security.feature.auth.ui.splash

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashSequencerTest {

    private val entryMs = 620L
    private val events = mutableListOf<String>()

    private suspend fun entry() {
        delay(entryMs)
        events += "entry"
    }

    private suspend fun exit() {
        delay(SplashTiming.EXIT_MS.toLong())
        events += "exit"
    }

    @Test
    fun `los puntos aparecen solo despues de 800 ms con el destino sin decidir`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = false)
        val ready = MutableStateFlow(false)
        val job = launch { sequencer.run(ready, ::entry, ::exit) }

        advanceTimeBy(799)
        runCurrent()
        assertFalse(sequencer.showDots.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(sequencer.showDots.value)

        job.cancel()
    }

    @Test
    fun `si el destino llega antes de 800 ms los puntos no salen nunca`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = false)
        val ready = MutableStateFlow(false)
        val job = launch { sequencer.run(ready, ::entry, ::exit) }

        advanceTimeBy(300)
        ready.value = true
        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(sequencer.showDots.value)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `los puntos se quitan cuando llega el destino`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = false)
        val ready = MutableStateFlow(false)
        val job = launch { sequencer.run(ready, ::entry, ::exit) }

        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(sequencer.showDots.value)

        ready.value = true
        runCurrent()
        assertFalse(sequencer.showDots.value)
        advanceTimeBy(1_000)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `con destino listo la salida espera a que termine la entrada y no mas`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = false)
        val ready = MutableStateFlow(true)
        val job = launch { sequencer.run(ready, ::entry, ::exit) }

        advanceTimeBy(entryMs - 1)
        runCurrent()
        assertEquals("la salida no empieza durante la entrada", emptyList<String>(), events)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("entry"), events)

        advanceTimeBy(SplashTiming.EXIT_MS.toLong())
        runCurrent()
        assertEquals(listOf("entry", "exit"), events)
        assertEquals("sin esperas extra: entrada + fundido", entryMs + SplashTiming.EXIT_MS, currentTime)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `sin destino la salida espera aunque la entrada ya termino`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = false)
        val ready = MutableStateFlow(false)
        val job = launch { sequencer.run(ready, ::entry, ::exit) }

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf("entry"), events)

        ready.value = true
        advanceTimeBy(SplashTiming.EXIT_MS.toLong())
        runCurrent()
        assertEquals(listOf("entry", "exit"), events)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `con reducir movimiento no hay entrada y se sale al decidir el destino`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = true)
        val ready = MutableStateFlow(true)
        var exited = false
        sequencer.run(ready, playEntry = { events += "entry" }, playExit = { exited = true })

        assertEquals("B se ve directo: la entrada no corre", emptyList<String>(), events)
        assertTrue(exited)
        assertEquals("sin esperas", 0L, currentTime)
    }

    @Test
    fun `con reducir movimiento tambien espera el destino sin entrada`() = runTest {
        val sequencer = SplashSequencer(reducedMotion = true)
        val ready = MutableStateFlow(false)
        var exited = false
        val job = launch { sequencer.run(ready, playEntry = { events += "entry" }, playExit = { exited = true }) }

        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(exited)
        ready.value = true
        runCurrent()
        assertTrue(exited)
        assertTrue(job.isCompleted)
        assertTrue(events.isEmpty())
    }
}
