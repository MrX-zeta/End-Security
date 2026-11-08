package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFormatTest {

    @Test
    fun `acepta correos con usuario, dominio y extension`() {
        listOf("ana@correo.com", "ana.lopez+banco@upchiapas.edu.mx", "  ana@correo.com  ").forEach {
            assertTrue("«$it» debería ser válido", EmailFormat.isValid(it))
        }
    }

    @Test
    fun `rechaza correos incompletos o con espacios`() {
        listOf("", "ana", "ana@", "@correo.com", "ana@correo", "ana@correo.c", "ana lopez@correo.com", "ana@@correo.com")
            .forEach { assertFalse("«$it» no debería ser válido", EmailFormat.isValid(it)) }
    }
}
