package com.luis.end_security.core.crypto

/**
 * TOTP de RFC 6238 (HMAC-SHA1, 30 s, 6 dígitos), como lo configura una app autenticadora con el
 * secreto de EP-19. Lo usa el backend falso para verificar el segundo paso (EP-06).
 */
object Totp {
    const val PERIOD_SECONDS = 30L
    const val DIGITS = 6

    /** Paso de tiempo de [epochSeconds]. */
    fun step(epochSeconds: Long): Long = Math.floorDiv(epochSeconds, PERIOD_SECONDS)

    /** Código de [digits] dígitos para un paso (RFC 4226 §5.3, truncamiento dinámico). */
    fun code(secret: ByteArray, step: Long, digits: Int = DIGITS): String {
        val counter = ByteArray(8) { index -> (step ushr (56 - 8 * index)).toByte() }
        val hash = Hashes.hmacSha1(secret, counter)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(digits, '0')
    }

    /**
     * Verifica [code] en la ventana de ±[window] pasos alrededor de [epochSeconds], sin aceptar
     * pasos ya usados (anti-replay con `last_used_step`). Devuelve el paso que coincidió o `null`.
     */
    fun verify(secret: ByteArray, code: String, epochSeconds: Long, lastUsedStep: Long?, window: Int = 1): Long? {
        val current = step(epochSeconds)
        for (candidate in current - window..current + window) {
            if (lastUsedStep != null && candidate <= lastUsedStep) continue
            if (Hashes.constantTimeEquals(code(secret, candidate).toByteArray(), code.toByteArray())) return candidate
        }
        return null
    }
}

/** Base32 de RFC 4648 en mayúsculas y sin relleno, como el secreto TOTP del contrato (EP-19). */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.trimEnd('=').uppercase()
        val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (char in clean) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Base32 inválido" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
