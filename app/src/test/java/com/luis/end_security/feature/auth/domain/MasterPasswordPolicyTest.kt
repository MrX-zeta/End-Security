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
        assertTrue(MasterPasswordPolicy.isAcceptable(length = 12, score = 3))
        assertTrue(MasterPasswordPolicy.isAcceptable(length = 30, score = 4))
        assertFalse(MasterPasswordPolicy.isAcceptable(length = 11, score = 4))
        assertFalse(MasterPasswordPolicy.isAcceptable(length = 20, score = 2))
        assertFalse(MasterPasswordPolicy.isAcceptable(length = 20, score = null))
    }
}
