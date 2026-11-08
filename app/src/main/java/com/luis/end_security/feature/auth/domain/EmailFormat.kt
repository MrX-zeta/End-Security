package com.luis.end_security.feature.auth.domain

/**
 * Revisión de forma del correo antes de habilitar «Entrar»: algo@dominio.ext, sin espacios.
 * No comprueba que el correo exista; eso lo decide el servidor.
 */
object EmailFormat {
    private val pattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")

    fun isValid(email: String): Boolean = pattern.matches(email.trim())
}
