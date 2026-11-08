package com.luis.end_security.feature.health.domain

import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class HealthProgressTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 22) // jueves
    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun item(password: String, changed: LocalDate, breach: Int = 0) =
        AnalysisItem(UUID.randomUUID(), "x", password, changed.atStartOfDay(zone).toInstant(), breach)

    private fun report(vararg items: AnalysisItem) =
        HealthAnalyzer.analyze(items.toList(), today.atTime(12, 0).atZone(zone).toInstant(), estimator)

    private fun snap(day: LocalDate, score: Int, breached: Int = 0) =
        SnapshotPoint(day, HealthTotals(score, 10, 1, 2, 7, 0, breached, 0))

    @Test
    fun `un punto por semana con el ultimo valor de cada semana y el de hoy`() {
        val r = report(item("s4-a", today.minusDays(100)), item("s4-b", today.minusDays(100)))
        val summary = HealthProgressCalculator.compute(
            listOf(
                snap(LocalDate.of(2026, 9, 7), 50), // lunes
                snap(LocalDate.of(2026, 9, 9), 55), // miércoles de la misma semana: gana este
                snap(LocalDate.of(2026, 9, 21), 70),
            ),
            r, today, zone,
        )!!
        assertEquals(listOf(55, 70, 100), summary.points.map { it.score })
        assertEquals(55, summary.from)
        assertEquals(100, summary.to)
        assertEquals(45, summary.delta)
        assertTrue(summary.hasHistory)
    }

    @Test
    fun `solo cuenta las ultimas 12 semanas`() {
        val r = report(item("s4-a", today.minusDays(100)))
        val summary = HealthProgressCalculator.compute(
            listOf(snap(today.minusWeeks(20), 10), snap(today.minusWeeks(11), 40)), r, today, zone,
        )!!
        assertEquals(listOf(40, 100), summary.points.map { it.score })
    }

    @Test
    fun `sin historial solo esta el punto de hoy`() {
        val summary = HealthProgressCalculator.compute(emptyList(), report(item("s4-a", today.minusDays(100))), today, zone)!!
        assertEquals(1, summary.points.size)
        assertFalse(summary.hasHistory)
        assertEquals(0, summary.delta)
    }

    @Test
    fun `fortalecidas este mes son las fuertes con password_changed_at de este mes`() {
        val r = report(
            item("s4-nueva", LocalDate.of(2026, 10, 3)),
            item("s3-nueva", LocalDate.of(2026, 10, 20)),
            item("s0-debil-nueva", LocalDate.of(2026, 10, 10)), // este mes pero débil: no cuenta
            item("s4-vieja", LocalDate.of(2026, 9, 30)), // el mes pasado: no cuenta
        )
        assertEquals(2, HealthProgressCalculator.compute(emptyList(), r, today, zone)!!.strengthenedThisMonth)
    }

    @Test
    fun `filtradas resueltas es el maximo del mes menos las de hoy sin bajar de cero`() {
        // Hoy hay 1 filtrada; este mes llegó a haber 3: se resolvieron 2.
        val r = report(item("s4-a", today.minusDays(100), breach = 5), item("s4-b", today.minusDays(100)))
        val summary = HealthProgressCalculator.compute(
            listOf(snap(LocalDate.of(2026, 10, 2), 40, breached = 3), snap(LocalDate.of(2026, 10, 10), 60, breached = 2)),
            r, today, zone,
        )!!
        assertEquals(2, summary.resolvedBreaches)
        // Una foto de otro mes no cuenta.
        val older = HealthProgressCalculator.compute(listOf(snap(LocalDate.of(2026, 9, 25), 40, breached = 9)), r, today, zone)!!
        assertEquals(0, older.resolvedBreaches)
        // Si hoy hay más que nunca, no baja de 0.
        val more = HealthProgressCalculator.compute(listOf(snap(LocalDate.of(2026, 10, 2), 40, breached = 0)), r, today, zone)!!
        assertEquals(0, more.resolvedBreaches)
    }

    @Test
    fun `la distribucion por fuerza sale del analisis de hoy`() {
        val r = report(item("s0-a", today), item("s2-b", today), item("s4-c", today), item("s3-d", today))
        val summary = HealthProgressCalculator.compute(emptyList(), r, today, zone)!!
        assertEquals(1, summary.weak)
        assertEquals(1, summary.fair)
        assertEquals(2, summary.strong)
        assertEquals(4, summary.total)
    }

    @Test
    fun `sin elementos no hay progreso`() {
        assertNull(HealthProgressCalculator.compute(emptyList(), report(), today, zone))
    }
}
