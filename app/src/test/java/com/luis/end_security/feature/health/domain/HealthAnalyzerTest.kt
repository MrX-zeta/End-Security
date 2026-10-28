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

class HealthAnalyzerTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")

    /** Estimador de mentira: la «contraseña» dice su puntaje con un prefijo `s0-`…`s4-`. */
    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun item(
        password: String,
        name: String = "x",
        ageDays: Long = 10,
        breach: Int? = 0,
        id: UUID = UUID.randomUUID(),
    ) = AnalysisItem(id, name, password, now.minus(Duration.ofDays(ageDays)), breach)

    private fun analyze(vararg items: AnalysisItem) = HealthAnalyzer.analyze(items.toList(), now, estimator)

    @Test
    fun `una fuerte, unica, reciente y sin filtraciones es nivel Bajo`() {
        val report = analyze(item("s4-unica"))
        assertEquals(RiskLevel.Low, report.items.single().level)
        assertEquals(StrengthClass.Strong, report.items.single().strength)
    }

    @Test
    fun `filtrada es Critica aunque sea fuerte`() {
        val health = analyze(item("s4-fuerte", breach = 3400)).items.single()
        assertEquals(RiskLevel.Critical, health.level)
        assertTrue(health.breached)
        assertFalse(health.breachPending)
    }

    @Test
    fun `debil es Alta`() {
        assertEquals(RiskLevel.High, analyze(item("s0-debil")).items.single().level)
        assertEquals(RiskLevel.High, analyze(item("s1-debil")).items.single().level)
    }

    @Test
    fun `repetida es Alta aunque sea fuerte`() {
        val report = analyze(item("s4-misma", name = "A"), item("s4-misma", name = "B"))
        assertTrue(report.items.all { it.level == RiskLevel.High && it.repeated })
    }

    @Test
    fun `regular es Media y antigua tambien`() {
        assertEquals(RiskLevel.Medium, analyze(item("s2-regular")).items.single().level)
        assertEquals(RiskLevel.Medium, analyze(item("s4-vieja", ageDays = 400)).items.single().level)
    }

    @Test
    fun `la prioridad es filtrada, luego repetida o debil, luego regular o antigua`() {
        // Filtrada y débil y antigua: Crítica.
        assertEquals(RiskLevel.Critical, analyze(item("s0-todo", ageDays = 900, breach = 1)).items.single().level)
        // Débil y antigua: Alta.
        assertEquals(RiskLevel.High, analyze(item("s1-debil-vieja", ageDays = 900)).items.single().level)
        // Regular y antigua: Media.
        assertEquals(RiskLevel.Medium, analyze(item("s2-regular-vieja", ageDays = 900)).items.single().level)
    }

    @Test
    fun `antigua es estrictamente mas de 365 dias`() {
        assertFalse(analyze(item("s4-limite", ageDays = 365)).items.single().old)
        val justOver = AnalysisItem(UUID.randomUUID(), "x", "s4-limite", now.minus(Duration.ofDays(365)).minusSeconds(1), 0)
        assertTrue(HealthAnalyzer.analyze(listOf(justOver), now, estimator).items.single().old)
        assertTrue(analyze(item("s4-limite", ageDays = 366)).items.single().old)
    }

    @Test
    fun `sin revisar en HIBP queda pendiente y no cuenta como filtrada`() {
        val health = analyze(item("s4-fuerte", breach = null)).items.single()
        assertTrue(health.breachPending)
        assertFalse(health.breached)
        assertEquals(RiskLevel.Low, health.level)
    }

    @Test
    fun `sin elementos no hay puntuacion`() {
        val report = analyze()
        assertNull(report.score)
        assertNull(report.message)
        assertNull(report.totals)
    }

    @Test
    fun `la puntuacion con casos calculados a mano`() {
        // 100: todo Bajo.
        assertEquals(100, analyze(item("s4-a"), item("s3-b")).score)
        // 0: todo Crítico.
        assertEquals(0, analyze(item("s4-a", breach = 2), item("s4-b", breach = 9)).score)
        // (1,0 + 1,0 + 0,35) / 3 = 0,7833… → 78 (el «78 de 100» del diseño).
        assertEquals(78, analyze(item("s4-a"), item("s4-b"), item("s1-c")).score)
        // (1,0 + 0,7 + 0,35 + 0) / 4 = 0,5125 → 51.
        assertEquals(51, analyze(item("s4-a"), item("s2-b"), item("s0-c"), item("s4-d", breach = 5)).score)
        // (1,0 + 0,7) / 2 = 0,85 → 85.
        assertEquals(85, analyze(item("s4-a"), item("s2-b")).score)
        // 0,7 solo → 70.
        assertEquals(70, analyze(item("s2-a")).score)
    }

    @Test
    fun `los mensajes cambian en 90, 70 y 50`() {
        assertEquals(HealthMessage.Excellent, HealthMessage.forScore(100))
        assertEquals(HealthMessage.Excellent, HealthMessage.forScore(90))
        assertEquals(HealthMessage.Good, HealthMessage.forScore(89))
        assertEquals(HealthMessage.Good, HealthMessage.forScore(70))
        assertEquals(HealthMessage.NeedsAttention, HealthMessage.forScore(69))
        assertEquals(HealthMessage.NeedsAttention, HealthMessage.forScore(50))
        assertEquals(HealthMessage.AtRisk, HealthMessage.forScore(49))
        assertEquals(HealthMessage.AtRisk, HealthMessage.forScore(0))
    }

    @Test
    fun `las repetidas se agrupan por contraseña`() {
        val report = analyze(
            item("s4-uno", name = "A"), item("s4-uno", name = "B"), item("s4-uno", name = "C"),
            item("s3-dos", name = "D"), item("s3-dos", name = "E"),
            item("s4-solo", name = "F"),
        )
        assertEquals(5, report.repeated.size)
        assertEquals(listOf(3, 2), report.repeatGroups.map { it.items.size })
        assertEquals(setOf("A", "B", "C"), report.repeatGroups[0].items.map { it.name }.toSet())
        assertFalse(report.items.first { it.name == "F" }.repeated)
    }

    @Test
    fun `la distribucion por fuerza suma el total`() {
        val report = analyze(item("s0-a"), item("s1-b"), item("s2-c"), item("s3-d"), item("s4-e"))
        assertEquals(2, report.weak.size)
        assertEquals(1, report.fair.size)
        assertEquals(2, report.strong.size)
        val totals = report.totals!!
        assertEquals(5, totals.total)
        assertEquals(5, totals.weak + totals.fair + totals.strong)
    }

    @Test
    fun `necesitan atencion son las Criticas y las Altas`() {
        val report = analyze(
            item("s4-a", breach = 1), item("s0-b"), item("s2-c"), item("s4-d", ageDays = 500), item("s4-e"),
        )
        assertEquals(2, report.needAttention.size)
    }

    @Test
    fun `el resultado no tiene contrasenas y no imprime nada sensible`() {
        val report = analyze(item("s4-Secreta-Unica", name = "Banco"))
        val printed = report.toString() + report.items.joinToString { it.toString() } + item("s4-Secreta-Unica").toString()
        assertFalse(printed.contains("Secreta"))
        assertEquals(16, report.items.single().passwordLength)
    }
}
