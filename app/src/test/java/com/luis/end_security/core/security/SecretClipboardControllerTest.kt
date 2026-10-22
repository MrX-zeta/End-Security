package com.luis.end_security.core.security

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecretClipboardControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val seconds = MutableStateFlow(ClipboardSettings.DEFAULT_SECONDS)

    private class FakePort : ClipboardPort {
        var label: String? = null
        var text: String? = null
        var clears = 0
        var hideLabel = false

        override fun set(label: String, text: String) {
            this.label = label
            this.text = text
        }

        override fun currentLabel(): String? = if (hideLabel) null else label

        override fun clear() {
            label = null
            text = null
            clears++
        }
    }

    private val owner = object : LifecycleOwner {
        override val lifecycle get() = error("el controlador no lo usa")
    }
    private val port = FakePort()
    private val clipboard = SecretClipboardController(
        port,
        object : ClipboardSettings {
            override val clipboardClearSeconds = seconds
        },
        scope,
    )

    @Test
    fun `copia el texto y devuelve 30 s por defecto`() = scope.runTest {
        assertEquals(30, clipboard.copy("Tortuga-29"))
        assertEquals("Tortuga-29", port.text)
        assertTrue(port.label!!.startsWith("end-security:"))
    }

    @Test
    fun `se borra a los 30 s y no antes`() = scope.runTest {
        clipboard.copy("secreto")
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals("secreto", port.text)

        advanceTimeBy(1)
        runCurrent()
        assertNull(port.text)
        assertEquals(1, port.clears)
    }

    @Test
    fun `si ya hay otra cosa copiada no la borra`() = scope.runTest {
        clipboard.copy("secreto")
        // La persona copió otra cosa (otra app) antes de que se cumpliera el plazo.
        port.set("otra-app", "lo que copió después")

        advanceTimeBy(60_000)
        runCurrent()

        assertEquals("lo que copió después", port.text)
        assertEquals(0, port.clears)
    }

    @Test
    fun `desde segundo plano Android no deja ver que hay copiado y se borra igual`() = scope.runTest {
        clipboard.copy("secreto")
        // Segundo plano: la etiqueta llega null aunque el texto siga ahí.
        port.hideLabel = true

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, port.clears)
    }

    @Test
    fun `al volver a primer plano borra lo copiado por la app si su temporizador no corrio`() = scope.runTest {
        clipboard.copy("secreto")
        // El proceso estuvo congelado: nunca llegaron los 30 s.
        clipboard.onStart(owner)

        assertNull(port.text)
        assertEquals(1, port.clears)
    }

    @Test
    fun `al volver a primer plano no toca lo que copio otra app`() = scope.runTest {
        port.set("otra-app", "lo ajeno")
        clipboard.onStart(owner)

        assertEquals("lo ajeno", port.text)
        assertEquals(0, port.clears)
    }

    @Test
    fun `copiar otra vez reinicia la cuenta y solo borra lo ultimo`() = scope.runTest {
        clipboard.copy("primera")
        val firstLabel = port.label
        advanceTimeBy(20_000)
        clipboard.copy("segunda")
        assertNotEquals(firstLabel, port.label)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("a los 30 s de la primera todavía no se borra la segunda", "segunda", port.text)

        advanceTimeBy(20_000)
        runCurrent()
        assertNull(port.text)
        assertEquals(1, port.clears)
    }

    @Test
    fun `respeta los segundos del ajuste y uno invalido vuelve a 30`() = scope.runTest {
        seconds.value = 120
        assertEquals(120, clipboard.copy("x"))
        seconds.value = 7
        assertEquals(30, clipboard.copy("x"))
    }

    @Test
    fun `las opciones permitidas son 15, 30, 60 y 120`() {
        assertEquals(setOf(15, 30, 60, 120), ClipboardSettings.ALLOWED_SECONDS)
        assertEquals(30, ClipboardSettings.sanitize(null))
        assertEquals(15, ClipboardSettings.sanitize(15))
    }
}
