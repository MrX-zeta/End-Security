package com.luis.end_security.core.designsystem.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGuardTest {

    @Test
    fun `sin interceptor no se atiende ningun toque`() {
        assertFalse(TabGuard().intercepts(BottomNavTab.Health))
    }

    @Test
    fun `el interceptor decide si atiende el toque`() {
        val guard = TabGuard()
        guard.register { it == BottomNavTab.Settings }
        assertTrue(guard.intercepts(BottomNavTab.Settings))
        assertFalse(guard.intercepts(BottomNavTab.Vault))
    }

    @Test
    fun `una pantalla que sale tarde no borra el interceptor de la que ya entro`() {
        val guard = TabGuard()
        val saliente: (BottomNavTab) -> Boolean = { true }
        val entrante: (BottomNavTab) -> Boolean = { true }
        guard.register(saliente)
        guard.register(entrante) // la nueva ya se registró
        guard.unregister(saliente) // y la vieja sale después
        assertTrue("el de la nueva sigue activo", guard.intercepts(BottomNavTab.Vault))
        guard.unregister(entrante)
        assertFalse(guard.intercepts(BottomNavTab.Vault))
    }
}
