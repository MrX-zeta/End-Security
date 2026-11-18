package com.luis.end_security.feature.auth.domain

/**
 * Formato de los códigos de respaldo de la verificación en dos pasos. Hay dos, según quién los genera:
 * - [CROCKFORD] (DEC-12, servidor falso de `demo`): 8 caracteres de Crockford (`0-9 A-Z` sin I, L, O ni U), `XXXX-XXXX`.
 * - [SERVER] (backend real de `prod`, BR-10): 10 caracteres de `abcdefghjkmnpqrstuvwxyz23456789`, `xxxxx-xxxxx`.
 * La app los muestra y los acepta tal como los entrega su servidor; la UI elige uno según la variante.
 */
class BackupCodeFormat private constructor(
    private val alphabet: String,
    val length: Int,
    private val group: Int,
    private val uppercase: Boolean,
    /** Letras que se confunden y se escriben como la que sí está en el alfabeto. */
    private val aliases: Map<Char, Char>,
) {
    /** Las mayúsculas las pone el filtro (Crockford) o no hacen falta (servidor). */
    val usesUppercase: Boolean get() = uppercase

    /**
     * Lo que escribió el usuario, en la forma que se guarda (sin guion ni espacios, con las mayúsculas o minúsculas del
     * formato, alias aplicados) y como máximo [length]. Así «k7qd 2m9x» queda «K7QD2M9X» en Crockford.
     */
    fun normalize(input: CharSequence): String {
        val result = StringBuilder(length)
        for (char in input) {
            if (result.length == length) break
            val cased = if (uppercase) char.uppercaseChar() else char.lowercaseChar()
            val mapped = aliases[cased] ?: cased
            if (mapped in alphabet) result.append(mapped)
        }
        return result.toString()
    }

    /** Para mostrar: guion después del primer grupo («K7QD2M9X» → «K7QD-2M9X»). */
    fun format(normalized: CharSequence): String =
        if (normalized.length > group) "${normalized.subSequence(0, group)}-${normalized.subSequence(group, normalized.length)}"
        else normalized.toString()

    /** Dónde va el guion que solo se dibuja. */
    val groupSize: Int get() = group

    fun isComplete(normalized: CharSequence): Boolean = normalized.length == length

    /** Completo y ya normalizado (sin guion ni caracteres fuera del alfabeto). */
    fun isValid(code: CharSequence): Boolean = isComplete(code) && normalize(code).contentEquals(code)

    companion object {
        val CROCKFORD = BackupCodeFormat(
            alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ",
            length = 8,
            group = 4,
            uppercase = true,
            aliases = mapOf('O' to '0', 'I' to '1', 'L' to '1'),
        )

        /** `RECOVERY_ALPHABET` de `two-factor.service.ts` del backend: sin 0/o ni 1/l/i, así no hay alias. */
        val SERVER = BackupCodeFormat(
            alphabet = "abcdefghjkmnpqrstuvwxyz23456789",
            length = 10,
            group = 5,
            uppercase = false,
            aliases = emptyMap(),
        )
    }
}
