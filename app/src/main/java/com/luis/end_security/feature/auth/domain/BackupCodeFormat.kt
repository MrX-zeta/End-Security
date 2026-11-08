package com.luis.end_security.feature.auth.domain

/**
 * Códigos de respaldo de la verificación en dos pasos (DEC-12): 8 caracteres en Base32 de
 * Crockford (`0-9 A-Z` sin I, L, O ni U), mostrados como `XXXX-XXXX`.
 */
object BackupCodeFormat {
    const val LENGTH = 8
    private const val GROUP = 4

    /**
     * Lo que escribió el usuario, en la forma que se guarda (sin guion): mayúsculas, sin espacios
     * ni guiones, O → 0, I/L → 1, y se descarta lo que no sea del alfabeto. Como máximo [LENGTH].
     * Así «k7qd 2m9x» queda «K7QD2M9X».
     */
    fun normalize(input: CharSequence): String {
        val result = StringBuilder(LENGTH)
        for (char in input) {
            if (result.length == LENGTH) break
            val mapped = when (val upper = char.uppercaseChar()) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
            if (mapped in '0'..'9' || (mapped in 'A'..'Z' && mapped !in EXCLUDED)) result.append(mapped)
        }
        return result.toString()
    }

    /** Para mostrar: guion después del cuarto carácter («K7QD2M9X» → «K7QD-2M9X»). */
    fun format(normalized: CharSequence): String =
        if (normalized.length > GROUP) "${normalized.subSequence(0, GROUP)}-${normalized.subSequence(GROUP, normalized.length)}"
        else normalized.toString()

    fun isComplete(normalized: CharSequence): Boolean = normalized.length == LENGTH

    /** 8 caracteres y ya en la forma de Crockford (sin guion ni caracteres fuera del alfabeto). */
    fun isValid(code: CharSequence): Boolean = isComplete(code) && normalize(code).contentEquals(code)

    private val EXCLUDED = setOf('I', 'L', 'O', 'U')
}
