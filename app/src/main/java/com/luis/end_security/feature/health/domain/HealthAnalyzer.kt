package com.luis.end_security.feature.health.domain

import com.luis.end_security.core.crypto.Hashes
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Nivel de riesgo de un elemento (DEC-20). Sirve para **ordenar qué atender primero** (los triángulos de la Bóveda, el aviso de
 * «necesitan atención»); ya no pesa en el índice global (tarea 27: el índice es el porcentaje de contraseñas sanas).
 */
enum class RiskLevel {
    Low,
    Medium,
    High,
    Critical,
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

/**
 * Mensaje de la puntuación global (DEC-20). Los rangos viven aquí **una sola vez**: el tope del índice con una filtrada
 * ([AT_RISK_MAX]) se deriva de ellos y cambia solo si cambian.
 */
enum class HealthMessage {
    Excellent,
    Good,
    NeedsAttention,
    AtRisk,
    ;

    companion object {
        const val EXCELLENT_MIN = 90
        const val GOOD_MIN = 70
        const val ATTENTION_MIN = 50

        /** El máximo del rango «En riesgo» (menos de [ATTENTION_MIN]): con una filtrada, el índice no pasa de aquí. */
        const val AT_RISK_MAX = ATTENTION_MIN - 1

        fun forScore(score: Int): HealthMessage = when {
            score >= EXCELLENT_MIN -> Excellent
            score >= GOOD_MIN -> Good
            score >= ATTENTION_MIN -> NeedsAttention
            else -> AtRisk
        }
    }
}

/** Por qué una contraseña cae en un grupo de reutilización: es la **misma** o es solo **parecida**. */
enum class ReuseKind { Identical, Similar }

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
    /** Está en un grupo de reutilización: la misma contraseña **o una parecida** (cuenta igual: nivel Alto, no sana). */
    val repeated: Boolean,
    /** Grupo de reutilización (los miembros comparten número); `null` si no se repite ni se parece a otra. */
    val repeatGroup: Int?,
    val old: Boolean,
    val breached: Boolean,
    /** Cuántas veces apareció en filtraciones (0 si no, o si aún no se revisó). */
    val breachCount: Int,
    /** Cuántos elementos hay en su grupo de reutilización (1 si es única). */
    val repeatCount: Int,
    /** Aún sin revisar en HIBP (sin red o todavía no le tocaba). */
    val breachPending: Boolean,
    val passwordChangedAt: Instant,
    /** `Identical` si todo su grupo usa exactamente la misma contraseña, `Similar` si hay contraseñas distintas; `null` si no se repite. */
    val reuseKind: ReuseKind? = null,
    /** El nombre de otro elemento con una contraseña **distinta pero parecida** («Parecida a la de {Nombre}»); `null` si no hay. */
    val similarTo: String? = null,
    /** **Sana** (DEC-20): no filtrada, no repetida ni parecida, fuerte (zxcvbn 3–4) y de menos de 365 días. */
    val healthy: Boolean = false,
) {
    override fun toString(): String = "ItemHealth(level=$level)"
}

/** Un grupo de reutilización: la misma contraseña, o contraseñas parecidas, en [items]. */
class RepeatGroup(val items: List<ItemHealth>, val kind: ReuseKind = ReuseKind.Identical) {
    override fun toString(): String = "RepeatGroup(size=${items.size}, kind=$kind)"
}

/**
 * Cuántas contraseñas hay en cada categoría, **cada una solo en la más grave** (filtrada > repetida o parecida > débil > antigua >
 * sana): la suma es el total. Es lo que dibujan las barras por semana de Progreso.
 */
class HealthMix(val healthy: Int, val old: Int, val weak: Int, val reused: Int, val breached: Int) {
    val total: Int get() = healthy + old + weak + reused + breached
}

/**
 * Los totales de un análisis (lo que guarda la foto diaria, INV-29: nunca contraseñas ni fuerza individual). [healthy] y [mix] son
 * `null` en las fotos de antes de la tarea 27.
 */
class HealthTotals(
    val score: Int,
    val total: Int,
    val weak: Int,
    val fair: Int,
    val strong: Int,
    /** Elementos cuya contraseña se repite o se parece a otra. */
    val repeated: Int,
    val breached: Int,
    val old: Int,
    val healthy: Int? = null,
    val mix: HealthMix? = null,
)

/** El resultado global: **no contiene contraseñas**. */
class HealthReport(val items: List<ItemHealth>) {
    /** Cuántas son sanas. */
    val healthyCount: Int = items.count { it.healthy }

    val breached: List<ItemHealth> = items.filter { it.breached }

    /**
     * El índice de salud (DEC-20, tarea 27): `round(100 × sanas / total)`. Con **al menos una filtrada** no pasa del máximo del rango
     * «En riesgo» ([HealthMessage.AT_RISK_MAX]). `null` si no hay elementos (estado vacío).
     */
    val score: Int? = if (items.isEmpty()) {
        null
    } else {
        val raw = (100.0 * healthyCount / items.size).roundToInt()
        if (breached.isNotEmpty()) minOf(raw, HealthMessage.AT_RISK_MAX) else raw
    }

    val message: HealthMessage? = score?.let { HealthMessage.forScore(it) }

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
        .map { members -> RepeatGroup(members, members.first().reuseKind ?: ReuseKind.Identical) }
        .sortedByDescending { it.items.size }

    /** Cada contraseña en su categoría más grave (ver [HealthMix]). */
    val mix: HealthMix = HealthMix(
        healthy = items.count { category(it) == 0 },
        old = items.count { category(it) == 1 },
        weak = items.count { category(it) == 2 },
        reused = items.count { category(it) == 3 },
        breached = items.count { category(it) == 4 },
    )

    private fun category(item: ItemHealth): Int = when {
        item.breached -> 4
        item.repeated -> 3
        item.strength != StrengthClass.Strong -> 2
        item.old -> 1
        else -> 0
    }

    val totals: HealthTotals? = score?.let {
        HealthTotals(it, items.size, weak.size, fair.size, strong.size, repeated.size, breached.size, old.size, healthyCount, mix)
    }

    fun forItem(id: UUID): ItemHealth? = items.firstOrNull { it.id == id }

    override fun toString(): String = "HealthReport(count=${items.size}, score=$score)"
}

/**
 * Análisis de salud (OP-09, DEC-20), Kotlin puro y local. Por elemento: fuerza (el mismo estimador), repetida (SHA-256 en memoria
 * que nunca se guarda) o **parecida** ([PasswordSimilarity]), antigua (`password_changed_at` hace más de 365 días) y filtrada (cuenta
 * de HIBP > 0). Nivel: Crítico = filtrada; Alto = repetida, parecida o débil; Medio = regular o antigua; Bajo = el resto. **Sana** =
 * no filtrada, no repetida ni parecida, fuerte y no antigua.
 */
object HealthAnalyzer {
    /** Una contraseña con más de 365 días se considera antigua. */
    val OLD_AFTER: Duration = Duration.ofDays(365)

    fun analyze(inputs: List<AnalysisItem>, now: Instant, estimator: PasswordStrengthEstimator): HealthReport {
        val count = inputs.size
        // Las repetidas (idénticas) se agrupan por el SHA-256 de la contraseña, solo aquí y sin guardarlo.
        val identicalKey = IntArray(count)
        val hashIds = HashMap<String, Int>()
        for ((index, input) in inputs.withIndex()) {
            val bytes = input.password.toByteArray(Charsets.UTF_8)
            val digest = Hashes.sha256(bytes)
            bytes.fill(0)
            identicalKey[index] = hashIds.getOrPut(digest.joinToString("") { "%02x".format(it) }) { hashIds.size }
            digest.fill(0)
        }

        // Componentes: idénticas o parecidas (unión de conjuntos).
        val parent = IntArray(count) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var node = x
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        val firstWithKey = HashMap<Int, Int>()
        for (index in 0 until count) {
            val first = firstWithKey.getOrPut(identicalKey[index]) { index }
            if (first != index) union(first, index)
        }
        // Parecidas: se une el par y se anota un vecino de contraseña distinta para «Parecida a la de {Nombre}».
        val similarNeighbor = arrayOfNulls<Int>(count)
        PasswordSimilarity.findSimilarPairs(inputs.map { it.password }) { i, j ->
            union(i, j)
            if (identicalKey[i] != identicalKey[j]) {
                if (similarNeighbor[i] == null) similarNeighbor[i] = j
                if (similarNeighbor[j] == null) similarNeighbor[j] = i
            }
        }

        val members = HashMap<Int, MutableList<Int>>()
        for (index in 0 until count) members.getOrPut(find(index)) { mutableListOf() } += index
        val groupOf = IntArray(count) { -1 }
        val groupSize = IntArray(count)
        val kindOf = arrayOfNulls<ReuseKind>(count)
        var next = 0
        for (group in members.values) {
            if (group.size < 2) continue
            val kind = if (group.all { identicalKey[it] == identicalKey[group.first()] }) ReuseKind.Identical else ReuseKind.Similar
            for (index in group) {
                groupOf[index] = next
                groupSize[index] = group.size
                kindOf[index] = kind
            }
            next++
        }

        val items = inputs.mapIndexed { index, input ->
            val score = estimator.score(input.password)
            val strength = StrengthClass.fromScore(score)
            val repeated = groupOf[index] >= 0
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
                repeatGroup = groupOf[index].takeIf { it >= 0 },
                old = old,
                breached = breached,
                breachCount = input.breachCount ?: 0,
                repeatCount = if (repeated) groupSize[index] else 1,
                breachPending = input.breachCount == null,
                passwordChangedAt = input.passwordChangedAt,
                reuseKind = kindOf[index],
                similarTo = similarNeighbor[index]?.let { inputs[it].name },
                healthy = !breached && !repeated && strength == StrengthClass.Strong && !old,
            )
        }
        return HealthReport(items)
    }
}
