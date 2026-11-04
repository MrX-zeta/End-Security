package com.luis.end_security.core.common

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización de un correo (DEC-63, contrato 1.3.0): recortar espacios, minúsculas **sin depender del idioma del teléfono**
 * (`Locale.ROOT`) y NFC. La usan la app y el backend falso, para que «igual al actual» se decida igual en los dos lados. **No** quita
 * los puntos ni los `+alias` de Gmail: son direcciones distintas para el servidor.
 */
object EmailNormalizer {
    fun normalize(raw: String): String = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
}
