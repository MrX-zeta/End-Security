package com.luis.end_security.feature.health.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * Lo que las pantallas de Salud muestran. [report] es `null` mientras no se ha calculado (o con la bóveda bloqueada).
 * Nada de esto lleva contraseñas.
 */
class HealthState(
    val report: HealthReport? = null,
    /** Hay una revisión de filtraciones en curso. */
    val checking: Boolean = false,
    /** Cuándo terminó la última revisión completa de HIBP; `null` si nunca. */
    val lastCheckedAt: Instant? = null,
    /** La última revisión no pudo completarse por falta de red. */
    val offline: Boolean = false,
) {
    override fun toString(): String = "HealthState(report=$report, checking=$checking)"
}

/** Una foto diaria de los totales (OP-45): sirve para la gráfica de Progreso. Nunca lleva contraseñas. */
class SnapshotPoint(val day: LocalDate, val totals: HealthTotals)

/**
 * Salud de la bóveda (OP-09, OP-10, OP-44, OP-45). Todo se calcula en el teléfono con la bóveda abierta; HIBP se
 * consulta directo y solo con el prefijo (DEC-21). Con la bóveda bloqueada el estado se vacía.
 */
interface HealthRepository {
    val state: StateFlow<HealthState>

    /**
     * Análisis local con lo que haya en la caché de filtraciones (sin red). No hace nada si la bóveda no cambió desde
     * el último análisis.
     */
    suspend fun refresh()

    /**
     * Revisa HIBP lo que falte o esté vencido (elemento cambiado o revisión de más de 7 días); con [force], todo.
     * Después vuelve a analizar y guarda la foto del día.
     */
    suspend fun checkBreaches(force: Boolean)

    /** La primera vez que se abre Salud: el análisis local y, si nunca se revisó, también HIBP. */
    suspend fun open()

    /** Las fotos diarias (12 meses como máximo), de la más vieja a la más nueva. */
    fun observeProgress(): Flow<List<SnapshotPoint>>
}
