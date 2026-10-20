package com.luis.end_security.core.crypto

/**
 * Códigos de respaldo (DEC-12): 8 caracteres de Base32 de Crockford (`0-9 A-Z` sin I, L, O ni U),
 * es decir, 40 bits de [RandomBytes]. La normalización de lo que escribe el usuario está en
 * `BackupCodeFormat`; aquí solo se generan y se comprueba la forma ya normalizada.
 */
object BackupCodeAlphabet {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 8

    fun random(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[RandomBytes.nextInt(0, ALPHABET.length)]) }
    }

    fun isNormalized(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
