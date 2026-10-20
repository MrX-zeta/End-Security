package com.luis.end_security.feature.auth.domain

/**
 * Solo con el backend falso y en compilaciones de depuración: el último código de verificación
 * de un correo, para mostrarlo como «Código de prueba». En cualquier otro caso devuelve `null`.
 */
interface DebugVerificationCodes {
    fun latestCode(email: String): String?
}
