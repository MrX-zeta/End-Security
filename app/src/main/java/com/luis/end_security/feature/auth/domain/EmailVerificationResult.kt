package com.luis.end_security.feature.auth.domain

/** Respuesta de verificar el código del correo (OP-12, tabla `email_verifications`). */
sealed interface EmailVerificationResult {
    data object Verified : EmailVerificationResult

    /** Código equivocado; el servidor dice cuántos intentos quedan (máximo [MAX_ATTEMPTS]). */
    data class WrongCode(val attemptsLeft: Int) : EmailVerificationResult

    /** El código venció o ya no quedan intentos: hay que pedir otro. */
    data object Expired : EmailVerificationResult

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
