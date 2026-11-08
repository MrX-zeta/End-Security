package com.luis.end_security.feature.health.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cuándo se animan las vistas de Salud (tarea 27, Paso 1). */
class HealthEntranceTest {

    @Test
    fun `la primera vez de cada vista es completa y despues se repite mas corta`() {
        val entrance = HealthEntrance()
        assertEquals(EntranceMode.First, entrance.modeFor(HealthTab.Summary))
        assertEquals(EntranceMode.First, entrance.modeFor(HealthTab.Progress))
        // Al volver a cada vista (selector o pestaña) se repite.
        assertEquals(EntranceMode.Replay, entrance.modeFor(HealthTab.Summary))
        assertEquals(EntranceMode.Replay, entrance.modeFor(HealthTab.Progress))
        assertEquals(EntranceMode.Replay, entrance.modeFor(HealthTab.Summary))
    }

    @Test
    fun `al volver de un detalle con Atras no se anima y despues sigue repitiendo`() {
        val entrance = HealthEntrance()
        assertEquals(EntranceMode.First, entrance.modeFor(HealthTab.Summary))
        entrance.leavingForDetail()
        assertEquals("el regreso con Atrás no repite", EntranceMode.None, entrance.modeFor(HealthTab.Summary))
        // Otra vista que aún no se había visto es su primera vez; la que ya se vio, una repetición.
        assertEquals(EntranceMode.First, entrance.modeFor(HealthTab.Progress))
        assertEquals(EntranceMode.Replay, entrance.modeFor(HealthTab.Summary))
    }

    @Test
    fun `un detalle antes de ver una vista tampoco la deja marcada como primera`() {
        val entrance = HealthEntrance()
        entrance.leavingForDetail()
        assertEquals(EntranceMode.None, entrance.modeFor(HealthTab.Summary))
        // Esa vista ya se vio: cuando se vuelva a entrar desde otra pestaña es una repetición.
        assertEquals(EntranceMode.Replay, entrance.modeFor(HealthTab.Summary))
    }
}
