package com.luis.end_security.feature.auth.domain

/**
 * Política de la contraseña maestra (DEC-31): de 12 a 128 caracteres y puntaje zxcvbn ≥ 3, sobre
 * el texto normalizado con [MasterPasswordNormalizer] y contando puntos de código.
 */
object MasterPasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128
    const val MIN_SCORE = 3

    /**
     * Tope grueso del campo de texto, en unidades UTF-16 (antes de normalizar). Es el doble de
     * [MAX_LENGTH] para no cortar emojis (2 unidades cada uno); el máximo real lo revisa
     * [exceedsMaxLength] después de NFKC, que puede convertir un carácter en varios (p. ej. «ﷺ» → 18).
     */
    const val MAX_FIELD_LENGTH = 2 * MAX_LENGTH

    /** [codePoints]: longitud del texto normalizado en puntos de código. */
    fun meetsLength(codePoints: Int): Boolean = codePoints >= MIN_LENGTH

    fun exceedsMaxLength(codePoints: Int): Boolean = codePoints > MAX_LENGTH

    fun isAcceptable(codePoints: Int, score: Int?): Boolean =
        meetsLength(codePoints) && !exceedsMaxLength(codePoints) && score != null && score >= MIN_SCORE
}
