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
}
