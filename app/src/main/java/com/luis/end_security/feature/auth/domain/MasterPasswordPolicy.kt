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
     * contraseña), variante de PRECIS OpaqueString (RFC 8265, DEC-31). No se ven o no se escriben
     * igual en todos los teléfonos, así que el usuario podría no repetirlos y perder su bóveda:
     * - controles (Cc) y surrogates solitarios (Cs);
     * - todo `Default_Ignorable_Code_Point` (espacios de ancho cero, marcas de dirección, guion
     *   suave, rellenos hangul, selectores de variación…), salvo U+200C, U+200D y las etiquetas
     *   U+E0020–U+E007F, que necesitan el persa, los emojis compuestos y las banderas con etiquetas;
     * - U+2028, U+2029 y U+2800, que no son ignorables pero tampoco se ven;
     * - los de formato U+FFF9–U+FFFB (anotación interlineal), el uso privado (Co) y los
     *   no-caracteres (U+FDD0–U+FDEF y los que terminan en FFFE o FFFF), que cada teléfono o
     *   teclado puede dibujar, quitar o cambiar distinto;
     * - los puntos de código que no estaban asignados en Unicode 10.0, para que todos los
     *   teléfonos (minSdk 28) normalicen igual.
     *
     * La edad se revisa también en lo que escribió el usuario, antes de normalizar: un carácter
     * posterior a Unicode 10.0 que NFKC convierte en uno antiguo (p. ej. U+1FBF0 → «0») solo se
     * convierte en los teléfonos que lo conocen; en los demás la llave saldría distinta.
     *
     * ZWJ, ZWNJ y las etiquetas se aceptan en cualquier posición: no cambian la llave entre
     * teléfonos (riesgo aceptado en DEC-31).
     *
     * Al entrar y al desbloquear no se usa: ahí solo se normaliza, para no dar pistas.
     *
     * @param raw lo que escribió el usuario.
     * @param normalized [raw] normalizado con [MasterPasswordNormalizer].
     */
    fun hasForbiddenCharacters(raw: CharSequence, normalized: CharSequence, unicode: UnicodeCharacterData): Boolean =
        normalized.anyCodePoint { isForbidden(it, unicode) } || raw.anyCodePoint { isNewerThanUnicode10(it, unicode) }

    private inline fun CharSequence.anyCodePoint(predicate: (Int) -> Boolean): Boolean {
        var index = 0
        while (index < length) {
            val codePoint = Character.codePointAt(this, index)
            if (predicate(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun isNewerThanUnicode10(codePoint: Int, unicode: UnicodeCharacterData): Boolean {
        val age = unicode.age(codePoint) ?: return true
        return age > UnicodeVersion.UNICODE_10
    }

    private fun isForbidden(codePoint: Int, unicode: UnicodeCharacterData): Boolean {
        if (CONTROLS.any { codePoint in it }) return true
        // Un surrogate solitario llega como su propio valor; en UTF-8 se volvería «?».
        if (codePoint in SURROGATES) return true
        if (codePoint in VISIBLE_BUT_BLANK) return true
        if (codePoint in INTERLINEAR_ANNOTATIONS) return true
        if (PRIVATE_USE.any { codePoint in it }) return true
        if (isNoncharacter(codePoint)) return true
        if (unicode.isDefaultIgnorable(codePoint) && !isAllowedIgnorable(codePoint)) return true
        return isNewerThanUnicode10(codePoint, unicode)
    }

    private fun isAllowedIgnorable(codePoint: Int): Boolean =
        codePoint == ZERO_WIDTH_NON_JOINER || codePoint == ZERO_WIDTH_JOINER || codePoint in TAGS

    /** U+FDD0–U+FDEF y los dos últimos de cada plano (U+FFFE, U+FFFF, U+1FFFE, …, U+10FFFF). */
    private fun isNoncharacter(codePoint: Int): Boolean =
        codePoint in 0xFDD0..0xFDEF || (codePoint and 0xFFFE) == 0xFFFE

    private val CONTROLS = listOf(0x0000..0x001F, 0x007F..0x009F)
    private val INTERLINEAR_ANNOTATIONS = 0xFFF9..0xFFFB
    private val PRIVATE_USE = listOf(0xE000..0xF8FF, 0xF0000..0xFFFFD, 0x100000..0x10FFFD)
    private val SURROGATES = 0xD800..0xDFFF
    private const val ZERO_WIDTH_NON_JOINER = 0x200C
    private const val ZERO_WIDTH_JOINER = 0x200D
    private val TAGS = 0xE0020..0xE007F

    /** Separadores de línea y de párrafo, y el patrón braille vacío. */
    private val VISIBLE_BUT_BLANK = setOf(0x2028, 0x2029, 0x2800)
}
