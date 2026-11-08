package com.luis.end_security.feature.health.domain

import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/** Contraseñas parecidas (tarea 27, Paso 4): la regla de la forma reducida, la de Levenshtein, los falsos positivos y el tiempo. */
class PasswordSimilarityTest {

    private fun similar(a: String, b: String): Boolean {
        var found = false
        PasswordSimilarity.findSimilarPairs(listOf(a, b)) { _, _ -> found = true }
        return found
    }

    private fun reduced(password: String) = String(PasswordSimilarity.reduce(password))

    // ---- La forma reducida

    @Test
    fun `la forma reducida quita los digitos y simbolos del final y sustituye`() {
        assertEquals("gato", reduced("Gato2024!"))
        assertEquals("password", reduced("P@ssw0rd"))
        assertEquals("password", reduced("password"))
        assertEquals("netflix", reduced("Netflix#2023"))
        assertEquals("", reduced("12345!"))
    }

    @Test
    fun `la forma reducida normaliza NFKC y usa minusculas sin depender del idioma`() {
        // «ﬁ» (ligadura) se normaliza a «fi»; la «I» turca no cambia la forma con Locale.ROOT.
        assertEquals("fino", reduced("ﬁNo"))
        assertEquals("ison", reduced("ISON99"))
    }

    // ---- Parecidas

    @Test
    fun `Gato2024 y Gato2025 son parecidas`() {
        assertTrue(similar("Gato2024!", "Gato2025!"))
    }

    @Test
    fun `P at ssw0rd y password son parecidas`() {
        assertTrue(similar("P@ssw0rd", "password"))
    }

    @Test
    fun `Netflix con otro año es parecida`() {
        assertTrue(similar("Netflix#2023", "Netflix#2024"))
    }

    @Test
    fun `por Levenshtein con la forma reducida distinta`() {
        // Distancia 2 y ambas de 8 o más: se parecen aunque la forma reducida difiera.
        assertTrue(similar("xylophone-ab", "xylophone-cd"))
    }

    // ---- Falsos positivos que NO deben agruparse

    @Test
    fun `abc y abd no son parecidas`() {
        assertFalse(similar("abc", "abd"))
    }

    @Test
    fun `abc12345 y xyz12345 no son parecidas`() {
        assertFalse(similar("abc12345", "xyz12345"))
    }

    @Test
    fun `contrasenas de 6 caracteres a distancia 1 no se agrupan`() {
        assertFalse(similar("ab12cd", "ab12ce"))
        assertFalse(similar("k9m2xz", "k9m2xy"))
    }

    @Test
    fun `la forma reducida de menos de 4 caracteres no cuenta`() {
        assertFalse(similar("Gat2024", "Gat2025")) // las dos se reducen a «gat»
    }

    @Test
    fun `distancia mayor a 2 no es parecida`() {
        assertFalse(similar("xylophone-abc", "xylophone-def"))
    }

    // ---- Distancia

    @Test
    fun `la distancia de Levenshtein se corta al pasar del maximo`() {
        assertEquals(0, PasswordSimilarity.distanceAtMost("abcdefgh", "abcdefgh", 2))
        assertEquals(1, PasswordSimilarity.distanceAtMost("abcdefgh", "abcdefgx", 2))
        assertEquals(2, PasswordSimilarity.distanceAtMost("abcdefgh", "abcdexxh", 2))
        assertEquals(3, PasswordSimilarity.distanceAtMost("abcdefgh", "abcxxxgh", 2)) // pasa de 2: devuelve max + 1
        assertEquals(3, PasswordSimilarity.distanceAtMost("abc", "abcdefg", 2))
    }

    // ---- Grupos en el análisis

    private val now = Instant.parse("2026-11-07T12:00:00Z")

    private val always4 = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = 4
    }

    private fun item(name: String, password: String, ageDays: Long = 10, breach: Int? = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, now.minus(Duration.ofDays(ageDays)), breach)

    @Test
    fun `los grupos son transitivos`() {
        // A~B (misma forma «gato»), B~C (Levenshtein 1 entre las originales de 8+): A, B y C quedan en un solo grupo.
        val report = HealthAnalyzer.analyze(
            listOf(item("A", "Gato2024!"), item("B", "Gato2025!"), item("C", "Gato2025?"), item("D", "Zorro-Unico-77")),
            now, always4,
        )
        assertEquals(1, report.repeatGroups.size)
        assertEquals(setOf("A", "B", "C"), report.repeatGroups.single().items.map { it.name }.toSet())
        assertEquals(ReuseKind.Similar, report.repeatGroups.single().kind)
        assertFalse(report.items.first { it.name == "D" }.repeated)
    }

    @Test
    fun `parecida cuenta como repetida - nivel Alto y no sana`() {
        val report = HealthAnalyzer.analyze(listOf(item("A", "Gato2024!"), item("B", "Gato2025!")), now, always4)
        report.items.forEach {
            assertTrue(it.repeated)
            assertEquals(RiskLevel.High, it.level)
            assertFalse(it.healthy)
        }
    }

    @Test
    fun `una identica sigue siendo identica y una parecida dice a cual se parece`() {
        val report = HealthAnalyzer.analyze(
            listOf(item("A", "Misma-Clave-1"), item("B", "Misma-Clave-1"), item("C", "Otra-Cosa-Larga-9"), item("D", "Otra-Cosa-Larga-8")),
            now, always4,
        )
        val groups = report.repeatGroups
        assertEquals(2, groups.size)
        assertEquals(ReuseKind.Identical, groups.first { g -> g.items.any { it.name == "A" } }.kind)
        assertEquals(ReuseKind.Similar, groups.first { g -> g.items.any { it.name == "C" } }.kind)
        assertNull("una idéntica no se parece a «otra»", report.items.first { it.name == "A" }.similarTo)
        assertEquals("D", report.items.first { it.name == "C" }.similarTo)
        assertEquals("C", report.items.first { it.name == "D" }.similarTo)
    }

    @Test
    fun `el resultado no conserva contrasenas ni formas reducidas`() {
        val report = HealthAnalyzer.analyze(listOf(item("A", "Gato2024!"), item("B", "Gato2025!")), now, always4)
        val printed = report.toString() + report.items.joinToString { it.toString() } + report.repeatGroups.joinToString { it.toString() }
        assertFalse(printed.contains("Gato"))
        assertFalse(printed.contains("gato"))
    }

    // ---- Tiempo

    private fun randomPasswords(count: Int): List<String> {
        val random = Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%&*"
        return List(count) { buildString { repeat(8 + random.nextInt(9)) { append(alphabet[random.nextInt(alphabet.length)]) } } }
    }

    @Test
    fun `mil contrasenas se comparan en menos de 300 ms`() {
        val passwords = randomPasswords(1_000)
        // Una pasada para calentar la JVM y el mejor tiempo de tres medidas (la máquina puede estar ocupada con otras tareas).
        PasswordSimilarity.findSimilarPairs(passwords) { _, _ -> }
        var pairs = 0
        val millis = (1..3).minOf {
            val start = System.nanoTime()
            pairs = 0
            PasswordSimilarity.findSimilarPairs(passwords) { _, _ -> pairs++ }
            (System.nanoTime() - start) / 1_000_000
        }
        assertTrue("1 000 contraseñas tardaron $millis ms (límite 300)", millis < 300)
        assertEquals("contraseñas aleatorias no se parecen", 0, pairs)
    }

    @Test
    fun `con mas de mil elementos compara solo dentro de cubetas y sigue siendo rapido`() {
        val passwords = randomPasswords(3_000) + listOf("Gato2024!", "Gato2025!")
        val start = System.nanoTime()
        val found = mutableSetOf<Pair<Int, Int>>()
        PasswordSimilarity.findSimilarPairs(passwords) { i, j -> found += minOf(i, j) to maxOf(i, j) }
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("3 000 contraseñas tardaron $millis ms", millis < 1_500)
        assertTrue("el par parecido se encuentra", found.contains(3_000 to 3_001))
    }

    @Test
    fun `miles de contrasenas que empiezan igual no se vuelven un problema de tiempo`() {
        val random = Random(7)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        // Una importación masiva: 4 000 contraseñas largas que empiezan con la misma raíz.
        val passwords = List(4_000) { "passwordbase" + String(CharArray(10) { alphabet[random.nextInt(alphabet.length)] }) }
        PasswordSimilarity.findSimilarPairs(passwords) { _, _ -> }
        val millis = (1..2).minOf {
            val start = System.nanoTime()
            PasswordSimilarity.findSimilarPairs(passwords) { _, _ -> }
            (System.nanoTime() - start) / 1_000_000
        }
        assertTrue("4 000 contraseñas con la misma raíz tardaron $millis ms", millis < 3_000)
    }

    @Test
    fun `las contrasenas enormes no entran en la comparacion por distancia`() {
        val huge = "x".repeat(500)
        var found = false
        PasswordSimilarity.findSimilarPairs(listOf(huge + "1", huge + "2")) { _, _ -> found = true }
        // La regla 1 (misma forma reducida) sí las une; la distancia ya no se calcula sobre textos de este tamaño.
        assertTrue(found)
    }
}
