package com.luis.end_security.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedRequestTest {

    /** Simula un atributo de una vista: `value` es lo que hay aplicado ahora. */
    private class FakeTarget(var value: String)

    private val request = SharedRequest<FakeTarget, String>(
        apply = { target -> target.value.also { target.value = "protegido" } },
        restore = { target, original -> target.value = original },
    )

    @Test
    fun `una sola pantalla aplica al entrar y restaura al salir`() {
        val target = FakeTarget("original")

        request.acquire(target)
        assertEquals("protegido", target.value)

        request.release(target)
        assertEquals("original", target.value)
        assertEquals(0, request.count(target))
    }

    @Test
    fun `en una transicion la pantalla que sale no quita la proteccion de la que entra`() {
        val target = FakeTarget("original")

        request.acquire(target) // Iniciar sesión
        request.acquire(target) // Crear cuenta entra antes de que Iniciar sesión salga
        request.release(target) // Iniciar sesión sale

        assertEquals("protegido", target.value)
        assertEquals(1, request.count(target))

        request.release(target) // Crear cuenta sale
        assertEquals("original", target.value)
    }

    @Test
    fun `se restaura el valor que habia antes de la primera peticion`() {
        val target = FakeTarget("original")

        request.acquire(target)
        request.acquire(target)
        request.release(target)
        request.release(target)

        assertEquals("original", target.value)
    }

    @Test
    fun `soltar sin haber pedido no hace nada`() {
        val target = FakeTarget("original")

        request.release(target)

        assertEquals("original", target.value)
        assertEquals(0, request.count(target))
    }

    @Test
    fun `cada objeto lleva su propia cuenta`() {
        val first = FakeTarget("a")
        val second = FakeTarget("b")

        request.acquire(first)
        request.acquire(second)
        request.release(first)

        assertEquals("a", first.value)
        assertEquals("protegido", second.value)
    }
}
