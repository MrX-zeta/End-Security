package com.luis.end_security.feature.health

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.HealthReport
import com.luis.end_security.feature.health.domain.HealthTotals
import com.luis.end_security.feature.health.domain.SnapshotPoint
import com.luis.end_security.feature.health.ui.HealthBreachedScreen
import com.luis.end_security.feature.health.ui.HealthOldScreen
import com.luis.end_security.feature.health.ui.HealthRankingScreen
import com.luis.end_security.feature.health.ui.HealthRepeatedScreen
import com.luis.end_security.feature.health.ui.HealthScreen
import com.luis.end_security.feature.health.ui.HealthTab
import com.luis.end_security.feature.health.ui.HealthUiState
import com.luis.end_security.feature.health.ui.RankingTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Salud dibujada en el emulador con los textos del diseño (Figma 47:1045, 62:2357, 62:2477, 62:2634, 47:1186, 54:2496,
 * 62:2788, 69:3307). Dibuja las pantallas sin `SecureScreen` (solo está en el `Route`), así que las capturas de
 * `cache/captures` no debilitan ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class HealthScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            EndSecurityTheme { Box(Modifier.size(width = 412.dp, height = 917.dp)) { content() } }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun item(name: String, password: String, ageDays: Long = 20, breach: Int = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, Instant.now().minusSeconds(ageDays * 86_400), breach)

    private fun report(): HealthReport = HealthAnalyzer.analyze(
        listOf(
            item("Instagram", "s4-a", breach = 2), item("Facebook", "s4-b", breach = 1),
            item("BBVA", "s3-misma"), item("Amazon", "s3-misma"), item("Gmail", "s3-misma"),
            item("Spotify", "s2-x"), item("Twitch", "s2-x"),
            item("Netflix", "s0-n"), item("Uber", "s1-u", ageDays = 500), item("Didi", "s4-d"),
        ),
        Instant.now(), estimator,
    )

    private fun state(tab: HealthTab = HealthTab.Summary, ranking: RankingTab = RankingTab.Strongest): HealthUiState {
        val report = report()
        val today = LocalDate.now()
        val snapshots = (1..11).map { week ->
            SnapshotPoint(today.minusWeeks(week.toLong()), HealthTotals(40 + (11 - week) * 2, 10, 4, 2, 4, 3, 2, 1))
        }
        return HealthUiState(
            isLoading = false, report = report, lastChecked = Instant.now(), tab = tab, rankingTab = ranking,
            progress = HealthProgressCalculator.compute(snapshots, report, today, ZoneId.systemDefault()),
        )
    }

    @Test
    fun resumen_muestra_la_puntuacion_el_mensaje_y_las_cuatro_filas() {
        var opened = ""
        render("scr22_salud_resumen") {
            HealthScreen(
                state(), {}, {}, { opened = "check" }, { opened = "breached" }, { opened = "repeated" },
                { opened = "weak" }, { opened = "old" }, {},
            )
        }
        composeRule.onNodeWithText("Resumen").assertIsDisplayed()
        composeRule.onNodeWithText("Progreso").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Puntuación de salud", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Aparecen en filtraciones").assertIsDisplayed()
        composeRule.onNodeWithText("Usas la misma en varios sitios").assertIsDisplayed()
        composeRule.onNodeWithText("Son fáciles de adivinar").assertIsDisplayed()
        composeRule.onNodeWithText("Más de un año sin cambio").assertIsDisplayed()

        composeRule.onNodeWithText("Revisar ahora").performClick()
        assertEquals("check", opened)
        composeRule.onNodeWithText("Aparecen en filtraciones").performClick()
        assertEquals("breached", opened)
        composeRule.onNodeWithText("Son fáciles de adivinar").performClick()
        assertEquals("weak", opened)
    }

    @Test
    fun progreso_muestra_la_grafica_los_indicadores_y_la_distribucion() {
        render("scr23_salud_progreso") {
            HealthScreen(state(HealthTab.Progress), {}, {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("fortalecidas este mes").assertIsDisplayed()
        composeRule.onNodeWithText("filtradas resueltas").assertIsDisplayed()
        composeRule.onNodeWithText("Por fuerza").assertIsDisplayed()
        composeRule.onNodeWithText("Ver ranking de fuerza").assertIsDisplayed()
    }

    @Test
    fun cargando_muestra_Revisando_filtraciones() {
        render("scr40_salud_cargando") {
            HealthScreen(HealthUiState(isLoading = true, checking = true), {}, {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithContentDescription("Calculando la salud de tu bóveda").assertIsDisplayed()
    }

    @Test
    fun una_boveda_sin_contrasenas_muestra_el_estado_vacio() {
        render("scr22_salud_vacia") {
            HealthScreen(HealthUiState(isLoading = false, report = HealthAnalyzer.analyze(emptyList(), Instant.now(), estimator)), {}, {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Agrega contraseñas para ver la salud de tu bóveda").assertIsDisplayed()
    }

    @Test
    fun ranking_muestra_mas_fuertes_y_mas_debiles() {
        render("scr24_ranking_fuertes") { HealthRankingScreen(state(), {}, {}, {}) }
        composeRule.onNodeWithText("Fuerza de tus contraseñas").assertIsDisplayed()
        composeRule.onNodeWithText("La fuerza se calcula en tu teléfono. Tus contraseñas no salen de él.").assertExists()
    }

    @Test
    fun ranking_mas_debiles() {
        render("scr25_ranking_debiles") { HealthRankingScreen(state(ranking = RankingTab.Weakest), {}, {}, {}) }
        composeRule.onNodeWithText("Más débiles").assertIsDisplayed()
    }

    @Test
    fun filtradas_con_sus_textos_y_abre_Editar() {
        var changed: UUID? = null
        val report = report()
        render("scr26_filtradas") { HealthBreachedScreen(report, {}, { changed = it }, {}) }
        composeRule.onNodeWithText("Cámbialas cuanto antes.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Datos de Have I Been Pwned").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram", substring = true).performClick()
        assertEquals(report.breached.maxByOrNull { it.breachCount }!!.id, changed)
    }

    @Test
    fun repetidas_con_sus_textos_y_Cambiar() {
        var changed: UUID? = null
        val report = report()
        render("scr27_repetidas") { HealthRepeatedScreen(report, {}, { changed = it }, {}) }
        composeRule.onNodeWithText("Misma contraseña en 3 sitios").assertIsDisplayed()
        composeRule.onNodeWithText("Si una se filtra, quedan expuestos todos los sitios que la comparten.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cambiar")[0].performClick()
        assert(changed != null)
    }

    @Test
    fun antiguas_lista_las_de_mas_de_un_anio() {
        render("scr28_antiguas") { HealthOldScreen(report(), {}, {}, {}) }
        composeRule.onNodeWithText("Uber", substring = true).assertIsDisplayed()
    }

    private fun offlineState(noticeId: Int = 0): HealthUiState {
        val base = state()
        // Revisado ayer y con una contraseña sin revisar (la de HIBP no respondió).
        val unchecked = AnalysisItem(UUID.randomUUID(), "Instagram", "s4-a", Instant.now().minusSeconds(20 * 86_400L), null)
        val pending = HealthAnalyzer.analyze(listOf(unchecked, item("Gmail", "s3-b")), Instant.now(), estimator)
        return HealthUiState(
            isLoading = false, report = pending, lastChecked = Instant.now().minusSeconds(24 * 3600L), offline = true,
            progress = base.progress, offlineNoticeId = noticeId,
        )
    }

    private fun renderScaled(name: String, fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        render(name) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale),
            ) { content() }
        }
    }

    @Test
    fun sin_conexion_con_letra_grande_Revisar_ahora_queda_debajo_de_la_fecha() {
        renderScaled("tarea23-salud-sin-conexion-1-3x", fontScale = 1.3f) {
            HealthScreen(offlineState(), {}, {}, {}, {}, {}, {}, {}, {})
        }
        // La línea no dice «sin conexión» (eso lo avisa el mensaje al tocar «Revisar ahora»).
        assertEquals(0, composeRule.onAllNodesWithText("sin conexión", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        val line = composeRule.onNodeWithText("Revisado ayer", substring = true).getBoundsInRoot()
        val button = composeRule.onNodeWithText("Revisar ahora").getBoundsInRoot()
        // «Revisar ahora» está debajo de la fecha (no a su lado), en una sola línea de 48 dp o más.
        assert(button.top >= line.bottom) { "el botón (${button.top}) no está debajo de la fecha (${line.bottom})" }
        assert(button.height >= 48.dp) { "el botón mide ${button.height}" }
        assert(button.height < 80.dp) { "«Revisar ahora» se partió en varias líneas (${button.height})" }
        composeRule.onNodeWithText("Revisar ahora").assertHasClickAction()
    }

    @Test
    fun tocar_Revisar_ahora_sin_conexion_muestra_el_aviso_y_lo_pendiente_sigue() {
        render("tarea23-salud-aviso-sin-conexion") {
            HealthScreen(offlineState(noticeId = 1), {}, {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Sin conexión. Te mostramos la última revisión.").assertIsDisplayed()
        // Lo no revisado sigue como pendiente.
        composeRule.onNodeWithText("sin revisar todavía", substring = true).assertIsDisplayed()
    }

    @Test
    fun las_listas_vacias_dicen_Ninguna_bien_hecho() {
        val clean = HealthAnalyzer.analyze(listOf(item("Gmail", "s4-a")), Instant.now(), estimator)
        render("scr26_filtradas_vacia") { HealthBreachedScreen(clean, {}, {}, {}) }
        composeRule.onNodeWithText("Ninguna, bien hecho").assertIsDisplayed()
    }
}
