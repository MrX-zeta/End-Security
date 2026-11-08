package com.luis.end_security.feature.health.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Contraseñas **parecidas** (tarea 27, DEC-20): reutilización disfrazada, como `Gato2024!` y `Gato2025!`.
 *
 * Todo ocurre **en memoria y solo durante el análisis**: las formas reducidas son `CharArray` que el análisis sobrescribe al
 * terminar (como las contraseñas descifradas, tarea 14) y las distancias no se guardan ni se registran en ningún lado. Este objeto
 * no conserva estado. Límite honesto: al normalizar (NFKC y minúsculas) la JVM crea `String` intermedios con la contraseña que no se
 * pueden sobrescribir; viven un instante y, como la contraseña original ya es un `String` en `AnalysisItem`, no agregan exposición.
 *
 * Dos contraseñas son parecidas si:
 * 1. sus **formas reducidas** son iguales y miden al menos [MIN_REDUCED] caracteres, o
 * 2. la **distancia de Levenshtein entre las originales** es ≤ [MAX_DISTANCE] y las dos miden al menos [MIN_LEVENSHTEIN_LENGTH].
 */
internal object PasswordSimilarity {
    const val MIN_REDUCED = 4
    const val MIN_LEVENSHTEIN_LENGTH = 8
    const val MAX_DISTANCE = 2

    /** Hasta tantos elementos se compara todo contra todo (O(n²), < 300 ms); con más, solo dentro de cubetas. */
    const val FULL_COMPARISON_LIMIT = 1_000

    /** Con muchos elementos, las cubetas se arman con los primeros caracteres de la forma reducida. */
    private const val BUCKET_PREFIX = 3

    /**
     * Topes contra el abuso (una importación masiva con miles de contraseñas que empiezan igual): dentro de una cubeta cada contraseña
     * se compara solo con las [BUCKET_WINDOW] siguientes (ordenadas por largo), el análisis entero hace a lo sumo
     * [MAX_COMPARISONS] comparaciones y las contraseñas de más de [MAX_COMPARE_LENGTH] caracteres no entran en la regla 2.
     * Consecuencia: por encima de [FULL_COMPARISON_LIMIT] elementos, un par que difiera en los 3 primeros caracteres de su forma
     * reducida solo se detecta por la regla 1.
     */
    private const val BUCKET_WINDOW = 50
    private const val MAX_COMPARISONS = 2_000_000
    private const val MAX_COMPARE_LENGTH = 256

    /** Sustituciones comunes («leet»). */
    private fun substitute(c: Char): Char = when (c) {
        '@', '4' -> 'a'
        '3' -> 'e'
        '1', '!' -> 'i'
        '0' -> 'o'
        '$', '5' -> 's'
        '7' -> 't'
        else -> c
    }

    /**
     * La forma reducida: NFKC y minúsculas (`Locale.ROOT`), **sin los dígitos y símbolos del final** y con las sustituciones comunes
     * (`@→a`, `4→a`, `3→e`, `1→i`, `!→i`, `0→o`, `$→s`, `5→s`, `7→t`). Primero se recorta el final y después se sustituye: así
     * `Gato2024!` queda `gato` y `P@ssw0rd` queda `password`.
     */
    fun reduce(password: String): CharArray {
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        var end = normalized.length
        while (end > 0 && !normalized[end - 1].isLetter()) end--
        val reduced = CharArray(end) { substitute(normalized[it]) }
        return reduced
    }

    /**
     * Distancia de Levenshtein entre [a] y [b], o `max + 1` si pasa de [max] (se corta en cuanto ya no puede bajar de ahí).
     */
    fun distanceAtMost(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > max) return max + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Avisa de cada par parecido con [onSimilar] (índices en [passwords]). Las formas reducidas se calculan aquí y **se sobrescriben
     * antes de volver**.
     */
    fun findSimilarPairs(passwords: List<String>, onSimilar: (Int, Int) -> Unit) {
        // La lista se crea antes del `try` y se llena dentro: si algo falla a mitad, el `finally` borra lo que ya existe.
        val reduced = ArrayList<CharArray>(passwords.size)
        try {
            for (password in passwords) reduced += reduce(password)
            // Regla 1: formas reducidas iguales (de 4 o más). Se ordenan los índices por forma y los vecinos iguales se unen.
            val order = passwords.indices.filter { reduced[it].size >= MIN_REDUCED }.sortedWith { x, y -> compare(reduced[x], reduced[y]) }
            for (k in 1 until order.size) {
                if (compare(reduced[order[k - 1]], reduced[order[k]]) == 0) onSimilar(order[k - 1], order[k])
            }

            // Regla 2: distancia ≤ 2 entre las originales de 8 o más caracteres. Solo se miran largos que difieren a lo sumo 2.
            val long = passwords.indices.filter { passwords[it].length in MIN_LEVENSHTEIN_LENGTH..MAX_COMPARE_LENGTH }
            if (passwords.size <= FULL_COMPARISON_LIMIT) {
                val byLength = long.sortedBy { passwords[it].length }
                for (a in byLength.indices) {
                    val i = byLength[a]
                    for (b in a + 1 until byLength.size) {
                        val j = byLength[b]
                        if (passwords[j].length - passwords[i].length > MAX_DISTANCE) break
                        if (distanceAtMost(passwords[i], passwords[j], MAX_DISTANCE) <= MAX_DISTANCE) onSimilar(i, j)
                    }
                }
            } else {
                // Muchos elementos: solo dentro de cubetas por el comienzo de la forma reducida y con largo ±2.
                val buckets = long.groupBy { prefixKey(reduced[it]) }
                var budget = MAX_COMPARISONS
                search@ for (members in buckets.values) {
                    val sorted = members.sortedBy { passwords[it].length }
                    for (a in sorted.indices) {
                        for (b in a + 1 until minOf(sorted.size, a + 1 + BUCKET_WINDOW)) {
                            val i = sorted[a]
                            val j = sorted[b]
                            if (passwords[j].length - passwords[i].length > MAX_DISTANCE) break
                            if (--budget < 0) break@search
                            if (distanceAtMost(passwords[i], passwords[j], MAX_DISTANCE) <= MAX_DISTANCE) onSimilar(i, j)
                        }
                    }
                }
            }
        } finally {
            reduced.forEach { it.fill('\u0000') }
        }
    }

    /** La cubeta de una forma reducida: sus primeros caracteres empaquetados en un número (sin crear textos con la contraseña). */
    private fun prefixKey(reduced: CharArray): Long {
        val n = minOf(BUCKET_PREFIX, reduced.size)
        var key = n.toLong() shl 56
        for (i in 0 until n) key = key or (reduced[i].code.toLong() shl (16 * i))
        return key
    }

    private fun compare(a: CharArray, b: CharArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return a.size - b.size
    }
}
