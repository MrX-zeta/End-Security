package com.luis.end_security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tarea 31: la variante `demo` lleva el backend falso y el «Modo demostración», con su propio id y versión. */
class DemoFlavorTest {

    @Test
    fun las_dos_banderas_estan_en_true() {
        assertTrue(BuildConfig.USE_FAKE_BACKEND)
        assertTrue(BuildConfig.DEMO_MODE)
    }

    @Test
    fun el_id_y_la_version_llevan_el_sufijo_demo() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".demo"))
        assertTrue(BuildConfig.VERSION_NAME.startsWith("1.0.0") && BuildConfig.VERSION_NAME.contains("-demo"))
        assertEquals(1, BuildConfig.VERSION_CODE)
    }
}
