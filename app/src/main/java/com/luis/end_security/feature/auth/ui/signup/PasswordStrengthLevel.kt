package com.luis.end_security.feature.auth.ui.signup

import androidx.annotation.StringRes
import com.luis.end_security.R

/** Rol de color del medidor; la pantalla lo traduce a un token del tema. */
enum class StrengthColorRole { Error, OnSurfaceVariant, Success }

/** Medidor de 4 barras de DEC-31: puntaje zxcvbn → barras, etiqueta y color. */
enum class PasswordStrengthLevel(
    val filledBars: Int,
    @param:StringRes val label: Int,
    val colorRole: StrengthColorRole,
) {
    Weak(filledBars = 1, label = R.string.strength_weak, colorRole = StrengthColorRole.Error),
    Fair(filledBars = 2, label = R.string.strength_fair, colorRole = StrengthColorRole.OnSurfaceVariant),
    Strong(filledBars = 3, label = R.string.strength_strong, colorRole = StrengthColorRole.Success),
    VeryStrong(filledBars = 4, label = R.string.strength_very_strong, colorRole = StrengthColorRole.Success),
    ;

    companion object {
        fun fromScore(score: Int): PasswordStrengthLevel = when {
            score <= 1 -> Weak
            score == 2 -> Fair
            score == 3 -> Strong
            else -> VeryStrong
        }
    }
}
