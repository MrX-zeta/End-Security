package com.luis.end_security.feature.health.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Un punto de la gráfica de Progreso: el último valor de una semana (DEC-55). [mix] es el desglose por categoría de esa foto, o `null`
 * en las fotos de antes de la tarea 27 (esas semanas muestran solo el [total], en gris); su [score] es el que se guardó entonces.
 */
class WeekPoint(val weekStart: LocalDate, val score: Int, val total: Int = 0, val mix: HealthMix? = null)

/**
 * Lo que muestra Progreso (SCR-23, OP-44), calculado con las fotos diarias y el análisis de hoy. Nada de esto lleva
 * contraseñas.
 */
class ProgressSummary(
    /** Hasta 12 semanas, de la más vieja a la más nueva; el último punto es el de hoy. */
    val points: List<WeekPoint>,
    /** Puntuación de la primera semana de la gráfica y la de hoy. */
    val from: Int,
    val to: Int,
    /** Elementos con `password_changed_at` de este mes que hoy son fuertes. */
    val strengthenedThisMonth: Int,
    /** El máximo de filtradas en las fotos del mes menos las de hoy, sin bajar de 0. */
    val resolvedBreaches: Int,
    val weak: Int,
    val fair: Int,
    val strong: Int,
    val total: Int,
) {
    val delta: Int get() = to - from

    /** Hay al menos dos semanas para comparar. */
    val hasHistory: Boolean get() = points.size >= 2
}

object HealthProgressCalculator {
    const val WEEKS = 12

    /** `null` si no hay análisis (bóveda vacía o bloqueada). */
    fun compute(snapshots: List<SnapshotPoint>, report: HealthReport, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ProgressSummary? {
        val totals = report.totals ?: return null
        val firstWeek = weekStart(today).minusWeeks((WEEKS - 1).toLong())
        // La foto de hoy puede no estar todavía (se escribe después del análisis): se usa el análisis de hoy.
        val byWeek = sortedMapOf<LocalDate, WeekPoint>()
        for (snapshot in snapshots.sortedBy { it.day }) {
            val week = weekStart(snapshot.day)
            // El pasado no se recalcula: cada semana usa el índice que se guardó entonces.
            if (week >= firstWeek && snapshot.day <= today) {
                byWeek[week] = WeekPoint(week, snapshot.totals.score, snapshot.totals.total, snapshot.totals.mix)
            }
        }
        byWeek[weekStart(today)] = WeekPoint(weekStart(today), totals.score, totals.total, totals.mix)
        val points = byWeek.values.toList()

        val monthStart = today.withDayOfMonth(1)
        val strengthened = report.items.count { item ->
            val changed = item.passwordChangedAt.atZone(zone).toLocalDate()
            item.strength == StrengthClass.Strong && changed >= monthStart && changed <= today
        }
        val maxThisMonth = snapshots.filter { it.day >= monthStart && it.day <= today }.maxOfOrNull { it.totals.breached } ?: totals.breached
        return ProgressSummary(
            points = points,
            from = points.first().score,
            to = totals.score,
            strengthenedThisMonth = strengthened,
            resolvedBreaches = (maxThisMonth - totals.breached).coerceAtLeast(0),
            weak = totals.weak,
            fair = totals.fair,
            strong = totals.strong,
            total = totals.total,
        )
    }

    fun weekStart(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

    /** Semanas completas entre dos fechas (para ubicar los meses en el eje). */
    fun weeksBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.WEEKS.between(from, to)
}
