package com.luis.end_security.feature.auth.domain

/**
 * Estima la fuerza de una contraseña en el dispositivo, sin red (DEC-31, TEC-ZXCVBN).
 * Recibe `CharSequence` para no crear un `String` con la contraseña.
 */
interface PasswordStrengthEstimator {
    /** Puntaje de 0 (muy débil) a 4 (muy fuerte). Bloquea: llámalo fuera del hilo principal. */
    fun score(password: CharSequence): Int
}
