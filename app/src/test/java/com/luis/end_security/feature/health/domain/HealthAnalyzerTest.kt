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

    // Diez contraseñas fuertes, distintas entre sí y nada parecidas (el estimador de mentira lee «s3-» o «s4-»).
    private val strongOnes = listOf(
        "s4-Tortuga", "s3-Cebolla-Roja", "s4-Violin-Azul", "s3-Jirafa!", "s4-Pescado-Frito",
        "s3-Zapato-Viejo", "s4-Ventana-Norte", "s3-Martillo-Duro", "s4-Cuchillo-Largo", "s3-Montana-Nevada",
    )

    private fun healthyItems(count: Int) = strongOnes.take(count).map { item(it) }

    @Test
    fun `el indice es el porcentaje de contrasenas sanas con casos hechos a mano`() {
        // 10 de 10 sanas: 100.
        assertEquals(100, analyze(*healthyItems(10).toTypedArray()).score)
        // 8 de 10 sanas (2 débiles): 80.
        assertEquals(80, analyze(*(healthyItems(8) + item("s0-debil-uno") + item("s1-debil-dos")).toTypedArray()).score)
        // 9 de 10 sanas y 1 filtrada: 90 → tope «En riesgo» = 49.
        assertEquals(49, analyze(*(healthyItems(9) + item("s4-Filtrada-X", breach = 12)).toTypedArray()).score)
        // 6 de 10 sanas (1 filtrada y 3 repetidas): 60 → 49.
        val six = healthyItems(6) + item("s4-Filtrada-Y", breach = 3) + List(3) { item("s4-La-Misma-Repetida", name = "rep$it") }
        assertEquals(49, analyze(*six.toTypedArray()).score)
        // 5 de 10 sanas (5 antiguas): 50, sin tope (no hay filtradas).
        assertEquals(50, analyze(*(healthyItems(5) + strongOnes.takeLast(5).map { item(it, ageDays = 400) }).toTypedArray()).score)
    }

    @Test
    fun `casos extremos del indice`() {
        // Sin elementos no hay índice.
        assertNull(analyze().score)
        // Una sola filtrada: 0.
        assertEquals(0, analyze(item("s4-Filtrada-Sola", breach = 5)).score)
        // Redondeo: 2 de 3 = 66,67 → 67.
        assertEquals(67, analyze(item("s4-Tortuga"), item("s3-Cebolla-Roja"), item("s0-debil")).score)
        // Una «regular» (zxcvbn 2) no cuenta como sana.
        assertEquals(0, analyze(item("s2-Regular-Sola")).score)
        assertEquals(50, analyze(item("s2-Regular-Sola"), item("s4-Tortuga")).score)
        // Una pendiente de revisar (sin red) no cuenta como filtrada.
        assertEquals(100, analyze(item("s4-Tortuga", breach = null)).score)
    }

    @Test
    fun `el tope de las filtradas se deriva de los rangos de los mensajes`() {
        assertEquals(HealthMessage.ATTENTION_MIN - 1, HealthMessage.AT_RISK_MAX)
        assertEquals(49, HealthMessage.AT_RISK_MAX)
        assertEquals(HealthMessage.AtRisk, HealthMessage.forScore(HealthMessage.AT_RISK_MAX))
        assertEquals(HealthMessage.NeedsAttention, HealthMessage.forScore(HealthMessage.AT_RISK_MAX + 1))
        // Con una filtrada el mensaje es siempre «En riesgo», sin importar qué tan sanas sean las demás.
        val report = analyze(*(healthyItems(9) + item("s4-Filtrada-X", breach = 1)).toTypedArray())
        assertEquals(HealthMessage.AtRisk, report.message)
    }

    @Test
    fun `sana exige las cuatro condiciones`() {
        assertTrue(analyze(item("s4-Tortuga")).items.single().healthy)
        assertTrue(analyze(item("s3-Tortuga")).items.single().healthy)
        assertFalse(analyze(item("s2-Tortuga")).items.single().healthy) // regular
        assertFalse(analyze(item("s4-Tortuga", ageDays = 400)).items.single().healthy) // antigua
        assertFalse(analyze(item("s4-Tortuga", breach = 1)).items.single().healthy) // filtrada
        assertFalse(analyze(item("s4-Misma-1", name = "A"), item("s4-Misma-1", name = "B")).items.first().healthy) // repetida
    }

    @Test
    fun `el desglose por categoria suma el total y cuenta cada una en la mas grave`() {
        val report = analyze(
            item("s4-Tortuga"), // sana
            item("s4-Violin", ageDays = 500), // antigua
            item("s0-debil-a"), // débil
            item("s2-regular-b"), // regular → cuenta como débil en el desglose
            item("s4-Misma-Rep", name = "R1"), item("s4-Misma-Rep", name = "R2"), // repetidas
            item("s0-Filtrada-Z", breach = 8, ageDays = 900), // filtrada, débil y antigua: solo filtrada
        )
        val mix = report.mix
        assertEquals(1, mix.healthy)
        assertEquals(1, mix.old)
        assertEquals(2, mix.weak)
        assertEquals(2, mix.reused)
        assertEquals(1, mix.breached)
        assertEquals(report.items.size, mix.total)
        assertEquals(report.healthyCount, report.totals!!.healthy)
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
