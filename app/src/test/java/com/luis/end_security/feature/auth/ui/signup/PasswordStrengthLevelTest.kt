package com.luis.end_security.feature.auth.ui.signup

import com.luis.end_security.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthLevelTest {

    @Test
    fun `0 y 1 son debiles con 1 barra en error`() {
        listOf(0, 1).forEach { score ->
            val level = PasswordStrengthLevel.fromScore(score)
            assertEquals(PasswordStrengthLevel.Weak, level)
            assertEquals(1, level.filledBars)
            assertEquals(R.string.strength_weak, level.label)
            assertEquals(StrengthColorRole.Error, level.colorRole)
        }
    }

    @Test
    fun `2 es regular con 2 barras en on-surface-variant`() {
        val level = PasswordStrengthLevel.fromScore(2)
        assertEquals(PasswordStrengthLevel.Fair, level)
        assertEquals(2, level.filledBars)
        assertEquals(R.string.strength_fair, level.label)
        assertEquals(StrengthColorRole.OnSurfaceVariant, level.colorRole)
    }

    @Test
    fun `3 es fuerte con 3 barras en success`() {
        val level = PasswordStrengthLevel.fromScore(3)
        assertEquals(PasswordStrengthLevel.Strong, level)
        assertEquals(3, level.filledBars)
        assertEquals(R.string.strength_strong, level.label)
        assertEquals(StrengthColorRole.Success, level.colorRole)
    }

    @Test
    fun `4 es muy fuerte con 4 barras en success`() {
        val level = PasswordStrengthLevel.fromScore(4)
        assertEquals(PasswordStrengthLevel.VeryStrong, level)
        assertEquals(4, level.filledBars)
        assertEquals(R.string.strength_very_strong, level.label)
        assertEquals(StrengthColorRole.Success, level.colorRole)
    }
}
