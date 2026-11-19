package com.luis.end_security.feature.vault.domain

import com.luis.end_security.core.crypto.GeneratorOptions
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemLimitsTest {
    @Test
    fun `el peor caso cabe en el bloque cifrado de 32 KiB`() {
        assertTrue(ItemLimits.WORST_CASE_BYTES < 32 * 1024)
    }

    @Test
    fun `el tope de la contrasena no corta una generada`() {
        assertTrue(ItemLimits.PASSWORD >= GeneratorOptions.MAX_LENGTH)
    }
}
