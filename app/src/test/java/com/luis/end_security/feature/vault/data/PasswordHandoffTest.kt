package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.vault.ui.TestClock
import com.luis.end_security.feature.vault.ui.openedSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La entrega única (DEC-24): una sola copia en memoria, solo con la bóveda abierta, que se borra al tomarla o al bloquear. */
class PasswordHandoffTest {

    private val job = Job()
    private val session: VaultSession = openedSession(TestClock())
    private val handoff = PasswordHandoffImpl(session, CoroutineScope(Dispatchers.Unconfined + job))

    @After
    fun tearDown() {
        session.lock()
        job.cancel()
    }

    @Test
    fun `se toma una sola vez`() {
        handoff.put("Gen-77".toCharArray())
        assertEquals("Gen-77", String(handoff.take()!!))
        assertNull(handoff.take())
    }

    @Test
    fun `guarda una copia y borra la anterior al reemplazarla`() {
        val original = "Primera-1".toCharArray()
        handoff.put(original)
        // El original sigue siendo de quien llama; la entrega tiene su propia copia.
        assertArrayEquals("Primera-1".toCharArray(), original)
        handoff.put("Segunda-2".toCharArray())
        assertEquals("Segunda-2", String(handoff.take()!!))
    }

    @Test
    fun `al bloquear se vacia y con la boveda bloqueada no guarda nada`() {
        handoff.put("Gen-77".toCharArray())

        session.lock()

        assertNull(handoff.take())
        handoff.put("Otra-88".toCharArray())
        assertNull("bloqueada: no se guarda", handoff.take())
    }

    @Test
    fun `clear la borra y no imprime nada`() {
        handoff.put("Gen-77".toCharArray())
        handoff.clear()
        assertNull(handoff.take())
        assertEquals("PasswordHandoff(***)", handoff.toString())
    }
}
