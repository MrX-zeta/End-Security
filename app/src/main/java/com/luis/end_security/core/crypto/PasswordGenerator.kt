package com.luis.end_security.core.crypto

import java.security.SecureRandom

/**
 * Lo que la persona elige en el Generador (SCR-15, DEC-24). Las minúsculas siempre van incluidas, así que
 * nunca hay un alfabeto vacío.
 */
class GeneratorOptions(
    val length: Int = DEFAULT_LENGTH,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** «Evitar parecidos»: quita `l 1 I O 0 |`. */
    val avoidAmbiguous: Boolean = false,
) {
    /** La misma configuración con la longitud dentro de [MIN_LENGTH]–[MAX_LENGTH]. */
    fun sanitized(): GeneratorOptions = GeneratorOptions(length.coerceIn(MIN_LENGTH, MAX_LENGTH), uppercase, digits, symbols, avoidAmbiguous)

    fun copy(
        length: Int = this.length,
        uppercase: Boolean = this.uppercase,
        digits: Boolean = this.digits,
        symbols: Boolean = this.symbols,
        avoidAmbiguous: Boolean = this.avoidAmbiguous,
    ) = GeneratorOptions(length, uppercase, digits, symbols, avoidAmbiguous)

    override fun equals(other: Any?): Boolean = other is GeneratorOptions && length == other.length &&
        uppercase == other.uppercase && digits == other.digits && symbols == other.symbols && avoidAmbiguous == other.avoidAmbiguous

    override fun hashCode(): Int = listOf(length, uppercase, digits, symbols, avoidAmbiguous).hashCode()

    override fun toString(): String = "GeneratorOptions(length=$length, uppercase=$uppercase, digits=$digits, symbols=$symbols, avoidAmbiguous=$avoidAmbiguous)"

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        const val DEFAULT_LENGTH = 16
    }
}

/**
 * Generador de contraseñas (OP-08, DEC-24). Todo es local:
 * - solo [SecureRandom] (regla no negociable 4) y `nextInt(bound)`, que no tiene sesgo de módulo;
 * - **al menos un carácter de cada clase activa**;
 * - mezcla final con Fisher-Yates, para que los garantizados no queden siempre al principio.
 *
 * Devuelve un `CharArray` (regla 6): quien lo recibe lo sobrescribe con [wipe] al terminar. Nunca lo escribe en logs.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: GeneratorOptions): CharArray {
        val opts = options.sanitized()
        val classes = alphabets(opts)
        val all = classes.joinToString("").toCharArray()
        val result = CharArray(opts.length)
        // Uno de cada clase activa, y el resto del alfabeto completo.
        classes.forEachIndexed { index, alphabet -> result[index] = alphabet[random.nextInt(alphabet.length)] }
        for (index in classes.size until result.size) result[index] = all[random.nextInt(all.size)]
        // Fisher-Yates.
        for (index in result.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val swap = result[index]
            result[index] = result[other]
            result[other] = swap
        }
        all.fill('\u0000')
        return result
    }

    companion object {
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val DIGITS = "0123456789"

        /** Sin comillas, barra invertida ni espacio, para que no fallen en formularios (DEC-24). */
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

        /** Los que se confunden al leer (DEC-24). */
        const val AMBIGUOUS = "l1IO0|"

        /** Los alfabetos de las clases activas, ya sin los parecidos si se pidió. */
        fun alphabets(options: GeneratorOptions): List<String> {
            val classes = buildList {
                add(LOWERCASE)
                if (options.uppercase) add(UPPERCASE)
                if (options.digits) add(DIGITS)
                if (options.symbols) add(SYMBOLS)
            }
            return if (options.avoidAmbiguous) classes.map { alphabet -> alphabet.filter { it !in AMBIGUOUS } } else classes
        }
    }
}
