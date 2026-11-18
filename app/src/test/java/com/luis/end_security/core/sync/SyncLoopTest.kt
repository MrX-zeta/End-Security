package com.luis.end_security.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** El ciclo de reintentos del programador (DEC-58), con motor y esperas falsos: sin reloj real. */
class SyncLoopTest {

    private val offlineWaits = mutableListOf<Long>()
    private val serverWaits = mutableListOf<Long>()

    private fun loop(
        outcomes: List<SyncOutcome>,
        canRetryOffline: () -> Boolean = { true },
        rerun: () -> Boolean = { false },
        onSync: (Int) -> Unit = {},
    ): Int {
        var calls = 0
        runBlocking {
            SyncLoop(
                sync = {
                    onSync(calls)
                    outcomes[calls++]
                },
                canRetryOffline = canRetryOffline,
                takeRerun = rerun,
                waitOffline = { offlineWaits += it },
                waitServer = { serverWaits += it },
            ).run()
        }
        return calls
    }

    @Test
    fun `sin conexion reintenta a 15 s, 30 s y 1 min, y no pasa de 2 min`() {
        val calls = loop(List(6) { SyncOutcome.Offline } + SyncOutcome.Synced)

        assertEquals(7, calls)
        assertEquals(listOf(15_000L, 30_000L, 60_000L, 120_000L, 120_000L, 120_000L), offlineWaits)
    }

    @Test
    fun `se detiene al sincronizar bien`() {
        val calls = loop(listOf(SyncOutcome.Offline, SyncOutcome.Synced))

        assertEquals(2, calls)
        assertEquals(listOf(15_000L), offlineWaits)
    }

    @Test
    fun `se detiene al cerrar sesion o bloquear, antes y despues de esperar`() {
        var open = true
        val calls = loop(List(5) { SyncOutcome.Offline }, canRetryOffline = { open }, onSync = { if (it == 2) open = false })

        assertEquals("la tercera pasada cierra y no hay cuarta", 3, calls)
        assertEquals(listOf(15_000L, 30_000L), offlineWaits)
    }

    @Test
    fun `con la app en segundo plano no reintenta sin conexion`() {
        val calls = loop(listOf(SyncOutcome.Offline, SyncOutcome.Synced), canRetryOffline = { false })

        assertEquals(1, calls)
        assertEquals(emptyList<Long>(), offlineWaits)
    }

    @Test
    fun `el 5xx sigue con su espera de 30 s a 15 min`() {
        loop(List(7) { SyncOutcome.RetryLater } + SyncOutcome.Synced)

        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L), serverWaits)
        assertEquals(emptyList<Long>(), offlineWaits)
    }

    @Test
    fun `un cambio pedido durante la pasada hace otra al terminar`() {
        var pending = true
        val calls = loop(listOf(SyncOutcome.Synced, SyncOutcome.Synced), rerun = { pending.also { pending = false } })

        assertEquals(2, calls)
    }
}
