package com.luis.end_security.feature.auth.domain

/** Política de la contraseña maestra (DEC-31): al menos 12 caracteres y puntaje zxcvbn ≥ 3. */
object MasterPasswordPolicy {
    const val MIN_LENGTH = 12
    const val MIN_SCORE = 3

    fun meetsLength(length: Int): Boolean = length >= MIN_LENGTH

    fun isAcceptable(length: Int, score: Int?): Boolean =
        meetsLength(length) && score != null && score >= MIN_SCORE
}
