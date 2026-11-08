package com.luis.end_security.feature.auth.domain

/** Respuesta del segundo paso al entrar: código TOTP (OP-15) o código de respaldo (OP-16). */
sealed interface SecondFactorResult {
    data object Accepted : SecondFactorResult

    /** Código equivocado (TOTP) o inválido / ya usado (respaldo). */
    data object Rejected : SecondFactorResult

    /** Demasiados intentos (DEC-32): el servidor dice cuántos minutos esperar. */
    data class LockedOut(val minutes: Int) : SecondFactorResult
}
