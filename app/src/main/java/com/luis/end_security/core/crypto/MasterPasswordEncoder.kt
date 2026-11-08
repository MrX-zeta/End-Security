package com.luis.end_security.core.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import kotlin.math.ceil

/**
 * Contraseña maestra ya normalizada (`MasterPasswordNormalizer`, DEC-31) → bytes UTF-8 para
 * Argon2id (DEC-05). Un surrogate solitario no se convierte en «?» ni se descarta (dos
 * contraseñas distintas darían la misma llave), sino que falla. Al entrar o desbloquear, ese
 * fallo se muestra como el error genérico.
 *
 * Los surrogates sueltos se buscan antes de codificar, además de `CodingErrorAction.REPORT`:
 * el codificador de Android (ICU) y el de la JVM no tratan igual uno que queda al final.
 */
object MasterPasswordEncoder {

    /**
     * Devuelve un arreglo del tamaño justo; el búfer intermedio se sobrescribe. Quien recibe el
     * resultado lo sobrescribe con [wipe] cuando ya no lo usa.
     *
     * @throws CharacterCodingException si el texto no se puede codificar en UTF-8.
     */
    fun encode(normalizedPassword: CharSequence): ByteArray {
        if (hasUnpairedSurrogate(normalizedPassword)) throw MalformedInputException(1)
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // wrap() no copia el texto.
        val input = CharBuffer.wrap(normalizedPassword)
        val buffer = ByteBuffer.allocate(ceil(normalizedPassword.length * encoder.maxBytesPerChar()).toInt())
        try {
            // Todo lo que no sea UNDERFLOW es un error, también OVERFLOW: si el búfer quedara
            // corto, la contraseña se cortaría sin avisar.
            val encoded = encoder.encode(input, buffer, true)
            if (!encoded.isUnderflow) encoded.throwException()
            val flushed = encoder.flush(buffer)
            if (!flushed.isUnderflow) flushed.throwException()
            return buffer.array().copyOf(buffer.position())
        } finally {
            buffer.array().wipe()
        }
    }

    private fun hasUnpairedSurrogate(text: CharSequence): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) return true
                    index += 2
                }
                char.isLowSurrogate() -> return true
                else -> index++
            }
        }
        return false
    }
}
