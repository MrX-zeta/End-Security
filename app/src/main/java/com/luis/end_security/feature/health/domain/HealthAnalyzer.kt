package com.luis.end_security.feature.health.domain

import com.luis.end_security.core.crypto.Hashes
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/** Nivel de riesgo de un elemento (DEC-20) con su peso en la puntuación global. */
enum class RiskLevel(val weight: Double) {
    Low(1.0),
    Medium(0.7),
    High(0.35),
    Critical(0.0),
}

/** Fuerza agrupada como el medidor de DEC-31: débil 0–1, regular 2, fuerte 3–4. */
enum class StrengthClass {
    Weak,
    Fair,
    Strong,
    ;

    companion object {
        fun fromScore(score: Int): StrengthClass = when {
            score <= 1 -> Weak
            score == 2 -> Fair
            else -> Strong
        }
    }
}

/** Mensaje de la puntuación global (DEC-20). */
enum class HealthMessage {
    Excellent,
    Good,
    NeedsAttention,
    AtRisk,
    ;

    companion object {
        fun forScore(score: Int): HealthMessage = when {
            score >= 90 -> Excellent
            score >= 70 -> Good
            score >= 50 -> NeedsAttention
            else -> AtRisk
        }
    }
}

/**
 * Lo que el análisis necesita de un elemento legible. **Lleva la contraseña**: existe solo mientras dura el
 * análisis y no sale de él (el resultado no la contiene).
 */
class AnalysisItem(
    val id: UUID,
    val name: String,
    val password: String,
    val passwordChangedAt: Instant,
    /** La cuenta de HIBP; `null` si todavía no se revisó (sin red): no cuenta como filtrada. */
    val breachCount: Int?,
) {
    override fun toString(): String = "AnalysisItem(***)"
}

/** El resultado de un elemento: **sin contraseña**, solo lo que las pantallas muestran. */
class ItemHealth(
    val id: UUID,
    val name: String,
    val passwordLength: Int,
    /** Puntaje de zxcvbn, 0–4. */
    val strengthScore: Int,
    val strength: StrengthClass,
    val level: RiskLevel,
    val repeated: Boolean,
    /** Grupo de repetidas (mismas contraseñas comparten número); `null` si no se repite. */
    val repeatGroup: Int?,
    val old: Boolean,
    val breached: Boolean,
    /** Cuántas veces apareció en filtraciones (0 si no, o si aún no se revisó). */
    val breachCount: Int,
    /** En cuántos elementos se usa esta misma contraseña (1 si es única). */
    val repeatCount: Int,
    /** Aún sin revisar en HIBP (sin red o todavía no le tocaba). */
    val breachPending: Boolean,
    val passwordChangedAt: Instant,
) {
    override fun toString(): String = "ItemHealth(level=$level)"
}

/** Un grupo de contraseñas repetidas: la misma contraseña en [items]. */
class RepeatGroup(val items: List<ItemHealth>) {
    override fun toString(): String = "RepeatGroup(size=${items.size})"
}

/** Los totales de un análisis (lo que guarda la foto diaria, INV-29: nunca contraseñas ni fuerza individual). */
class HealthTotals(
    val score: Int,
    val total: Int,
    val weak: Int,
    val fair: Int,
    val strong: Int,
    /** Elementos cuya contraseña se repite. */
    val repeated: Int,
    val breached: Int,
    val old: Int,
)

/** El resultado global: **no contiene contraseñas**. */
class HealthReport(val items: List<ItemHealth>) {
    /** `null` si no hay elementos (estado vacío). */
    val score: Int? = if (items.isEmpty()) {
        null
    } else {
        (100 * items.map { it.level.weight }.average()).roundToInt()
    }

    val message: HealthMessage? = score?.let { HealthMessage.forScore(it) }

    val breached: List<ItemHealth> = items.filter { it.breached }
    val repeated: List<ItemHealth> = items.filter { it.repeated }
    val weak: List<ItemHealth> = items.filter { it.strength == StrengthClass.Weak }
    val fair: List<ItemHealth> = items.filter { it.strength == StrengthClass.Fair }
    val strong: List<ItemHealth> = items.filter { it.strength == StrengthClass.Strong }
    val old: List<ItemHealth> = items.filter { it.old }
    val pendingBreachCount: Int = items.count { it.breachPending }

    /** Crítico + Alto: «N contraseñas necesitan atención» y los triángulos de la Bóveda. */
    val needAttention: List<ItemHealth> = items.filter { it.level == RiskLevel.Critical || it.level == RiskLevel.High }

    val repeatGroups: List<RepeatGroup> = items
        .filter { it.repeatGroup != null }
        .groupBy { it.repeatGroup }
        .values
        .map { RepeatGroup(it) }
        .sortedByDescending { it.items.size }

    val totals: HealthTotals? = score?.let {
        HealthTotals(it, items.size, weak.size, fair.size, strong.size, repeated.size, breached.size, old.size)
    }

    fun forItem(id: UUID): ItemHealth? = items.firstOrNull { it.id == id }

    override fun toString(): String = "HealthReport(count=${items.size}, score=$score)"
}

/**
 * Análisis de salud (OP-09, DEC-20), Kotlin puro y local. Por elemento: fuerza (el mismo estimador), repetida
 * (SHA-256 en memoria que nunca se guarda), antigua (`password_changed_at` hace más de 365 días) y filtrada (cuenta de
 * HIBP > 0). Nivel: Crítico = filtrada; Alto = repetida o débil; Medio = regular o antigua; Bajo = el resto.
 */
object HealthAnalyzer {
    /** Una contraseña con más de 365 días se considera antigua. */
    val OLD_AFTER: Duration = Duration.ofDays(365)

    fun analyze(inputs: List<AnalysisItem>, now: Instant, estimator: PasswordStrengthEstimator): HealthReport {
        // Agrupa las repetidas por el SHA-256 de la contraseña, solo aquí y sin guardarlo.
        val groupByHash = HashMap<String, MutableList<AnalysisItem>>()
        for (input in inputs) {
            val bytes = input.password.toByteArray(Charsets.UTF_8)
            val digest = Hashes.sha256(bytes)
            bytes.fill(0)
            groupByHash.getOrPut(digest.joinToString("") { "%02x".format(it) }) { mutableListOf() } += input
            digest.fill(0)
        }
        val groupIndex = HashMap<UUID, Int>()
        var next = 0
        for (members in groupByHash.values) {
            if (members.size >= 2) {
                members.forEach { groupIndex[it.id] = next }
                next++
            }
        }
        val sizeByGroup = HashMap<Int, Int>()
        for (members in groupByHash.values) {
            if (members.size >= 2) groupIndex[members.first().id]?.let { sizeByGroup[it] = members.size }
        }
        val items = inputs.map { input ->
            val score = estimator.score(input.password)
            val strength = StrengthClass.fromScore(score)
            val repeated = input.id in groupIndex
            val old = Duration.between(input.passwordChangedAt, now) > OLD_AFTER
            val breached = (input.breachCount ?: 0) > 0
            val level = when {
                breached -> RiskLevel.Critical
                repeated || strength == StrengthClass.Weak -> RiskLevel.High
                strength == StrengthClass.Fair || old -> RiskLevel.Medium
                else -> RiskLevel.Low
            }
            ItemHealth(
                id = input.id,
                name = input.name,
                passwordLength = input.password.length,
                strengthScore = score,
                strength = strength,
                level = level,
                repeated = repeated,
                repeatGroup = groupIndex[input.id],
                old = old,
                breached = breached,
                breachCount = input.breachCount ?: 0,
                repeatCount = groupIndex[input.id]?.let { sizeByGroup[it] } ?: 1,
                breachPending = input.breachCount == null,
                passwordChangedAt = input.passwordChangedAt,
            )
        }
        return HealthReport(items)
    }
}
