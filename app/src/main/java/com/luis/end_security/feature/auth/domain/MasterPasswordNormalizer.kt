package com.luis.end_security.feature.auth.domain

import java.text.Normalizer

/**
 * Normalización de la contraseña maestra (DEC-31), siempre: al registrarse, al entrar y al
 * desbloquear, antes de medirla, compararla o derivar con ella.
 * 1. Se quitan los selectores de variación U+FE0E y U+FE0F, que solo cambian cómo se dibuja un
 *    emoji: así «❤» y «❤» con U+FE0F dan la misma llave, la ponga o no el teclado. Van antes de
 *    NFKC porque bloquean la composición: «A» + U+FE0F + anillo debe quedar «Å».
 * 2. NFKC, como recomienda NIST SP 800-63B §5.1.1.2: la misma frase escrita con otro teclado
 *    (p. ej. «ñ» compuesta o como «n» + tilde) da el mismo resultado. Ninguna descomposición de
 *    NFKC produce U+FE0E ni U+FE0F, así que el resultado ya no los tiene.
 *
 * Para Argon2 se codifica en UTF-8 con `CodingErrorAction.REPORT` (DEC-05).
 *
 * `Normalizer` solo devuelve `String`: es la limitación aceptada en DEC-27. Quien llama debe
 * usar el resultado y soltarlo de inmediato, sin guardarlo.
 */
object MasterPasswordNormalizer {

    fun normalize(password: CharSequence): String {
        val withoutSelectors =
            if (password.none(::isPresentationSelector)) password else password.filterNot(::isPresentationSelector)
        return Normalizer.normalize(withoutSelectors, Normalizer.Form.NFKC)
    }

    /** Longitud en puntos de código (un emoji cuenta como 1), sobre el texto ya normalizado. */
    fun codePointLength(normalized: CharSequence): Int =
        Character.codePointCount(normalized, 0, normalized.length)

    private fun isPresentationSelector(char: Char): Boolean = char == TEXT_STYLE || char == EMOJI_STYLE

    private val TEXT_STYLE = Char(0xFE0E)
    private val EMOJI_STYLE = Char(0xFE0F)
}
