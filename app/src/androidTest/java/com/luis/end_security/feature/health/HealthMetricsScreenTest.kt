package com.luis.end_security.feature.health

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthMix
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.HealthReport
import com.luis.end_security.feature.health.domain.HealthTotals
import com.luis.end_security.feature.health.domain.SnapshotPoint
import com.luis.end_security.feature.health.ui.HealthBreachedScreen
import com.luis.end_security.feature.health.ui.HealthRankingScreen
import com.luis.end_security.feature.health.ui.HealthRepeatedScreen
import com.luis.end_security.feature.health.ui.HealthScreen
import com.luis.end_security.feature.health.ui.HealthTab
import com.luis.end_security.feature.health.ui.HealthUiState
import com.luis.end_security.feature.health.ui.RankingTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Las pantallas de Salud con el índice nuevo, las parecidas, «Mejoradas recientemente» y las barras por semana (tarea 27). Sin capturas. */
@RunWith(AndroidJUnit4::class)
class HealthMetricsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun item(name: String, password: String, ageDays: Long = 10, breach: Int? = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, Instant.now().minusSeconds(ageDays * 86_400), breach)

    private fun analyze(vararg items: AnalysisItem): HealthReport = HealthAnalyzer.analyze(items.toList(), Instant.now(), estimator)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { EndSecurityTheme { Box(Modifier.size(width = 412.dp, height = 1400.dp)) { content() } } }
        composeRule.waitForIdle()
    }

    private fun summary(report: HealthReport) = HealthUiState(isLoading = false, report = report, lastChecked = Instant.now())

    @Test
    fun el_resumen_dice_cuantas_estan_sanas() {
        val report = analyze(item("A", "s4-Tortuga"), item("B", "s3-Cebolla-Roja"), item("C", "s0-debil"))
        show { HealthScreen(summary(report), {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithText("2 de 3 contraseñas están sanas").assertIsDisplayed()
        composeRule.onNodeWithText("Cambia las filtradas para subir tu índice").assertDoesNotExist()
    }

    @Test
    fun con_filtradas_el_resumen_dice_como_subir_el_indice() {
        val report = analyze(item("A", "s4-Tortuga"), item("B", "s4-Filtrada-Larga", breach = 3))
        show { HealthScreen(summary(report), {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithText("1 de 2 contraseña está sana", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1 de 2 contraseñas están sanas").assertIsDisplayed()
        composeRule.onNodeWithText("Cambia las filtradas para subir tu índice").assertIsDisplayed()
    }

    @Test
    fun la_fila_se_llama_repetidas_y_parecidas() {
        show { HealthScreen(summary(analyze(item("A", "s4-Tortuga"))), {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithText("Repetidas y parecidas").assertIsDisplayed()
    }

    @Test
    fun las_filtradas_dicen_cuantas_veces_aparecieron_con_separador_de_miles() {
        val report = analyze(item("Instagram", "s4-Tortuga", breach = 52_341), item("Gmail", "s4-Cebolla-Roja", breach = 1))
        show { HealthBreachedScreen(report, {}, {}) }
        val number = NumberFormat.getIntegerInstance(Locale.getDefault()).format(52_341)
        composeRule.onNodeWithText("Apareció en $number filtraciones", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Apareció en 1 filtración", substring = true).assertIsDisplayed()
    }

    @Test
    fun repetidas_y_parecidas_titulan_cada_grupo_segun_su_tipo() {
        val report = analyze(
            item("A", "s4-Misma-Clave-1"), item("B", "s4-Misma-Clave-1"), // idénticas
            item("C", "s4-Gato2024!"), item("D", "s4-Gato2025!"), // parecidas
        )
        show { HealthRepeatedScreen(report, {}, {}) }
        composeRule.onNodeWithText("Repetidas y parecidas").assertIsDisplayed()
        composeRule.onNodeWithText("Misma contraseña en 2 sitios").assertIsDisplayed()
        composeRule.onNodeWithText("Contraseñas parecidas en 2 sitios").assertIsDisplayed()
    }

    @Test
    fun el_ranking_cambia_a_mas_debiles_y_mejoradas_recientemente() {
        val report = analyze(item("A", "s0-debil"), item("B", "s4-Tortuga-Verde", ageDays = 3))
        val state = HealthUiState(
            isLoading = false, report = report, lastChecked = Instant.now(), rankingTab = RankingTab.Weakest,
            improvedRecently = report.items.filter { it.healthy },
        )
        show { HealthRankingScreen(state, {}, {}) }
        composeRule.onNodeWithText("Más débiles").assertIsDisplayed()
        composeRule.onNodeWithText("Mejoradas").assertIsDisplayed()
        composeRule.onNodeWithText("Más fuertes").assertDoesNotExist()
    }

    @Test
    fun sin_mejoradas_el_ranking_lo_explica() {
        val report = analyze(item("A", "s0-debil"))
        val state = HealthUiState(isLoading = false, report = report, lastChecked = Instant.now(), rankingTab = RankingTab.Improved)
        show { HealthRankingScreen(state, {}, {}) }
        composeRule.onNodeWithText("Todavía no hay contraseñas mejoradas este mes. Cambia una débil y aparecerá aquí.").assertIsDisplayed()
    }

    @Test
    fun cada_semana_de_las_barras_se_lee_con_su_desglose_y_las_viejas_solo_con_el_total() {
        val report = analyze(item("A", "s4-Tortuga"), item("B", "s0-debil"), item("C", "s4-Cebolla-Roja", breach = 2))
        val today = LocalDate.now()
        val snapshots = listOf(
            // Una foto de antes del desglose: solo el total.
            SnapshotPoint(today.minusWeeks(2), HealthTotals(55, 10, 2, 1, 7, 1, 0, 0)),
            SnapshotPoint(today.minusWeeks(1), HealthTotals(49, 20, 2, 1, 7, 1, 1, 0, 15, HealthMix(15, 0, 2, 2, 1))),
        )
        val progress = HealthProgressCalculator.compute(snapshots, report, today, ZoneId.systemDefault())
        val state = HealthUiState(isLoading = false, report = report, lastChecked = Instant.now(), tab = HealthTab.Progress, progress = progress)
        show { HealthScreen(state, {}, {}, {}, {}, {}, {}, {}) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNode(hasContentDescription("15 sanas, 2 débiles, 2 repetidas o parecidas, 1 filtrada", substring = true)).assertExists()
        composeRule.onNode(hasContentDescription("10 contraseñas", substring = true)).assertExists()
        assertTrue(true)
    }
}
