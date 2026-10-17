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

    /**
     * Caracteres que no se aceptan **al fijar** una contraseña nueva (registro y cambio de
     * contraseña), variante de PRECIS OpaqueString (RFC 8265, DEC-31). No se ven, así que el
     * usuario podría no volver a escribirlos igual y perder su bóveda:
     * - controles (categoría Cc);
     * - espacios de ancho cero y marcas de dirección: U+200B, U+2060, U+FEFF, U+200E, U+200F,
     *   U+202A–U+202E y U+2066–U+2069;
     * - puntos de código sin asignar en este dispositivo (su normalización podría cambiar en
     *   otra versión de Unicode).
     *
     * Se permiten U+200D (ZWJ) y U+FE0F porque los emojis compuestos los necesitan (👨\u200D👩\u200D👧, ❤\uFE0F).
     * Al entrar y al desbloquear no se usa: ahí solo se normaliza, para no dar pistas.
     *
     * @param normalized texto ya normalizado con [MasterPasswordNormalizer].
     */
    fun hasForbiddenCharacters(normalized: CharSequence): Boolean {
        var index = 0
        while (index < normalized.length) {
            val codePoint = Character.codePointAt(normalized, index)
            if (isForbidden(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun isForbidden(codePoint: Int): Boolean {
        if (codePoint == ZERO_WIDTH_JOINER || codePoint == VARIATION_SELECTOR_16) return false
        if (codePoint in INVISIBLE_MARKS) return true
        if (BIDI_EMBEDDINGS.any { codePoint in it }) return true
        return when (Character.getType(codePoint)) {
            Character.CONTROL.toInt(), Character.UNASSIGNED.toInt() -> true
            else -> false
        }
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private val INVISIBLE_MARKS = setOf(0x200B, 0x2060, 0xFEFF, 0x200E, 0x200F)
    private val BIDI_EMBEDDINGS = listOf(0x202A..0x202E, 0x2066..0x2069)
}
