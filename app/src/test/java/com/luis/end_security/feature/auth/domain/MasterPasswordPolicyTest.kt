package com.luis.end_security.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPasswordPolicyTest {

    @Test
    fun `pide al menos 12 caracteres`() {
        assertFalse(MasterPasswordPolicy.meetsLength(11))
        assertTrue(MasterPasswordPolicy.meetsLength(12))
    }

    @Test
    fun `pide largo y puntaje de al menos 3`() {
        assertTrue(MasterPasswordPolicy.isAcceptable(codePoints = 12, score = 3))
        assertTrue(MasterPasswordPolicy.isAcceptable(codePoints = 30, score = 4))
        assertFalse(MasterPasswordPolicy.isAcceptable(codePoints = 11, score = 4))
        assertFalse(MasterPasswordPolicy.isAcceptable(codePoints = 20, score = 2))
        assertFalse(MasterPasswordPolicy.isAcceptable(codePoints = 20, score = null))
    }

    // ---- Tarea 16: mayúscula, minúscula y número (DEC-31)

    @Test
    fun `pide una mayuscula`() {
        assertFalse(MasterPasswordPolicy.hasUppercase("sin mayuscula 12"))
        assertTrue(MasterPasswordPolicy.hasUppercase("Con mayuscula"))
    }

    @Test
    fun `pide una minuscula`() {
        assertFalse(MasterPasswordPolicy.hasLowercase("SOLO MAYUSCULAS 12"))
        assertTrue(MasterPasswordPolicy.hasLowercase("MAYUSCULAS y una"))
    }

    @Test
    fun `pide un numero y los simbolos no se exigen`() {
        assertFalse(MasterPasswordPolicy.hasDigit("Sin numeros ni nada"))
        assertTrue(MasterPasswordPolicy.hasDigit("Con un 1 solo"))
        assertTrue(MasterPasswordPolicy.meetsCharacterClasses("Abcdefghijk1"))
    }

    @Test
    fun `la enie mayuscula cuenta como mayuscula`() {
        assertTrue(MasterPasswordPolicy.hasUppercase("Ñ"))
        assertTrue(MasterPasswordPolicy.hasUppercase(MasterPasswordNormalizer.normalize("aÑb")))
    }

    @Test
    fun `tras NFKC cuentan las letras y digitos de ancho completo y de otros alfabetos`() {
        val fullwidth = MasterPasswordNormalizer.normalize("Ａｂ１")
        assertTrue(MasterPasswordPolicy.meetsCharacterClasses(fullwidth))
        assertTrue(MasterPasswordPolicy.hasDigit("٣")) // dígito arábigo-índico
    }
}
