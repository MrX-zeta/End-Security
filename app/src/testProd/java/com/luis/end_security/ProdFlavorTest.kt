package com.luis.end_security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tarea 31 (E1): la variante `prod` no lleva el backend falso ni el «Modo demostración». */
class ProdFlavorTest {

    @Test
    fun las_dos_banderas_estan_en_false() {
        assertFalse(BuildConfig.USE_FAKE_BACKEND)
        assertFalse(BuildConfig.DEMO_MODE)
    }

    @Test
    fun la_clase_del_backend_falso_no_esta_en_el_classpath() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.luis.end_security.core.network.fake.FakeTransport") }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.luis.end_security.core.network.fake.FakeServer") }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.luis.end_security.core.network.fake.FakeVerificationCodes") }
    }

    @Test
    fun el_id_de_aplicacion_no_lleva_sufijo_de_demo() {
        assertFalse(BuildConfig.APPLICATION_ID.endsWith(".demo"))
        assertFalse(BuildConfig.VERSION_NAME.endsWith("-demo"))
    }
}
