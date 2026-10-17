package com.luis.end_security.feature.auth.ui.verify

import java.util.Locale

/** Estado de Verificar correo (SCR-06). */
data class VerifyEmailUiState(
    val email: String = "",
    val code: String = "",
    val canSubmit: Boolean = false,
    /** Segundos para poder pedir otro código; 0 cuando ya se puede. */
    val resendSecondsLeft: Int = RESEND_WAIT_SECONDS,
    val error: VerifyEmailError? = null,
    val isLoading: Boolean = false,
) {
    /** «Reenviar código» se muestra al terminar la espera o en cuanto el código deja de servir. */
    val canResend: Boolean get() = resendSecondsLeft == 0 || error == VerifyEmailError.Expired

    // El código y el correo no deben terminar en un log.
    override fun toString(): String =
        "VerifyEmailUiState(digits=${code.length}, resendSecondsLeft=$resendSecondsLeft, error=$error, ***)"

    companion object {
        const val RESEND_WAIT_SECONDS = 60
    }
}

sealed interface VerifyEmailError {
    /** «Código incorrecto. Te quedan N intentos.» */
    data class WrongCode(val attemptsLeft: Int) : VerifyEmailError

    /** «Este código ya no sirve. Pide uno nuevo.» (vencido o sin intentos) */
    data object Expired : VerifyEmailError
}

sealed interface VerifyEmailAction {
    data class CodeChanged(val value: String) : VerifyEmailAction {
        override fun toString(): String = "CodeChanged(***)"
    }

    /** «Verificar» o «Listo» en el teclado. */
    data object Submit : VerifyEmailAction

    /** «Reenviar código». */
    data object Resend : VerifyEmailAction
}

/** «m:ss», como «0:45» o «1:00». Con dígitos 0–9 en cualquier idioma del teléfono. */
fun formatCountdown(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
