package com.luis.end_security.feature.auth.domain

import java.text.Normalizer

/**
 * Normalización de la contraseña maestra (DEC-31): NFKC, como recomienda NIST SP 800-63B
 * §5.1.1.2, antes de medirla, compararla o derivar con ella. Así la misma frase escrita con otro
 * teclado (p. ej. «ñ» compuesta o como «n» + tilde) da el mismo resultado. Para Argon2 se
 * codifica en UTF-8 (DEC-05).
 *
 * `Normalizer` solo devuelve `String`: es la limitación aceptada en DEC-27. Quien llama debe
 * usar el resultado y soltarlo de inmediato, sin guardarlo.
 */
object MasterPasswordNormalizer {

    fun normalize(password: CharSequence): String = Normalizer.normalize(password, Normalizer.Form.NFKC)

    /** Longitud en puntos de código (un emoji cuenta como 1), sobre el texto ya normalizado. */
    fun codePointLength(normalized: CharSequence): Int =
        Character.codePointCount(normalized, 0, normalized.length)
}
