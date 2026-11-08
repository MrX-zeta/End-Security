package com.luis.end_security.feature

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.FullWidthGroups
import com.luis.end_security.core.designsystem.component.FullWidthPassword
import com.luis.end_security.core.designsystem.component.LocalMessageHost
import com.luis.end_security.core.designsystem.component.MessageHost
import com.luis.end_security.core.designsystem.component.MessageHostState
import com.luis.end_security.core.designsystem.component.rememberMessageHostState
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.health.domain.AnalysisItem
import com.luis.end_security.feature.health.domain.HealthAnalyzer
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.HealthTotals
import com.luis.end_security.feature.health.domain.SnapshotPoint
import com.luis.end_security.feature.health.ui.HealthScreen
import com.luis.end_security.feature.health.ui.HealthTab
import com.luis.end_security.feature.health.ui.HealthUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Captura el movimiento de la tarea 26 **cuadro por cuadro** (reloj de Compose controlado) para revisarlo a ojo, como en la tarea 25.
 * Las pantallas de Salud usan `FLAG_SECURE`, así que `screenrecord` las ve negras: esta es la forma de verlas. Deja PNG en
 * `cache/captures/t26-<escena>-NN.png`. No afirma nada: lo medido está en las otras pruebas.
 */
@RunWith(AndroidJUnit4::class)
class Tarea26FramesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(scene: String, index: Int) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "captures").apply { mkdirs() }
        File(dir, "t26-$scene-%02d.png".format(index)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Captura cada [step] cuadros, [shots] veces. */
    private fun record(scene: String, shots: Int, step: Int, start: Int = 0) {
        for (i in 0 until shots) {
            capture(scene, start + i)
            repeat(step) { composeRule.mainClock.advanceTimeByFrame() }
        }
    }

    private val estimator = object : PasswordStrengthEstimator {
        override fun score(password: CharSequence): Int = password.toString().substring(1, 2).toInt()
    }

    private fun item(name: String, password: String, ageDays: Long = 20, breach: Int = 0) =
        AnalysisItem(UUID.randomUUID(), name, password, Instant.now().minusSeconds(ageDays * 86_400), breach)

    private fun healthState(tab: HealthTab, base: Int = 40): HealthUiState {
        val report = HealthAnalyzer.analyze(
            listOf(
                item("Instagram", "s4-a", breach = 2), item("Facebook", "s4-b", breach = 1),
                item("BBVA", "s3-misma"), item("Amazon", "s3-misma"), item("Gmail", "s3-misma"),
                item("Spotify", "s2-x"), item("Twitch", "s2-x"), item("Netflix", "s0-n"), item("Didi", "s4-d"),
            ),
            Instant.now(), estimator,
        )
        val today = LocalDate.now()
        val snapshots = (1..11).map { week ->
            SnapshotPoint(today.minusWeeks(week.toLong()), HealthTotals(base + (11 - week) * 2, 10, 4, 2, 4, 3, 2, 1))
        }
        return HealthUiState(
            isLoading = false, report = report, lastChecked = Instant.now(), tab = tab,
            progress = HealthProgressCalculator.compute(snapshots, report, today, ZoneId.systemDefault()),
        )
    }

    private var health by mutableStateOf(healthState(HealthTab.Summary))

    private fun setHealth() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme { Box(Modifier.size(width = 360.dp, height = 640.dp)) { HealthScreen(health, {}, {}, {}, {}, {}, {}, {}) } }
        }
    }

    @Test
    fun saludResumenPrimeraVez() {
        health = healthState(HealthTab.Summary)
        setHealth()
        composeRule.mainClock.advanceTimeByFrame()
        record("resumen", shots = 16, step = 3)
    }

    @Test
    fun saludCambioAProgresoYGrafica() {
        health = healthState(HealthTab.Summary)
        setHealth()
        composeRule.mainClock.advanceTimeBy(1_500)
        health = healthState(HealthTab.Progress)
        composeRule.mainClock.advanceTimeByFrame()
        record("progreso", shots = 20, step = 3)
    }

    @Test
    fun saludDatosNuevosSinVolverACero() {
        health = healthState(HealthTab.Progress)
        setHealth()
        composeRule.mainClock.advanceTimeBy(1_800)
        health = healthState(HealthTab.Progress, base = 20)
        record("datos", shots = 12, step = 2)
    }

    @Test
    fun selectorDeSalud() {
        health = healthState(HealthTab.Summary)
        setHealth()
        composeRule.mainClock.advanceTimeBy(1_500)
        health = healthState(HealthTab.Progress)
        record("selector", shots = 12, step = 1)
    }

    private lateinit var host: MessageHostState

    @Test
    fun avisoEntraYSale() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                host = rememberMessageHostState()
                CompositionLocalProvider(LocalMessageHost provides host) {
                    Box(Modifier.size(width = 360.dp, height = 140.dp).background(Color(0xFFF2F2F2))) {
                        MessageHost(host, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        host.post("Netflix se guardó", R.drawable.ic_check_16, durationMillis = 700)
        record("aviso", shots = 12, step = 1)
        composeRule.mainClock.advanceTimeBy(500)
        record("aviso", shots = 12, step = 1, start = 12)
    }

    private val passwordStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.4.sp)
    private val keyStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp, lineHeight = 30.sp)

    private fun passwordCard(widthDp: Int, length: Int, name: String) {
        val text = buildString { repeat(length) { append("RISEfuU=9}MuzEFhqMgEd\$R0ASG&2@lLV!x7#Qp3-"[it % 36]) } }
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.width(widthDp.dp).background(Color(0xFFF2F2F2)).padding(horizontal = 32.dp, vertical = 8.dp)) {
                    Box(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(24.dp)) {
                        FullWidthPassword(text, passwordStyle, Color.White)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        capture(name, 0)
    }

    @Test fun renglones360con33() = passwordCard(360, 33, "renglones-360-33")

    @Test fun renglones412con64() = passwordCard(412, 64, "renglones-412-64")

    @Test fun renglones360con16() = passwordCard(360, 16, "renglones-360-16")

    private fun keyCard(widthDp: Int, name: String) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.width(widthDp.dp).background(Color(0xFFF2F2F2)).padding(horizontal = 32.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Box(Modifier.height(0.dp))
                        Box(Modifier.padding(end = 60.dp)) {
                            FullWidthGroups("YRHLPYJNZ4BTHQ5R2FN7U5MUHTHG3U2Y", keyStyle, Color.White)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        capture(name, 0)
    }

    @Test fun clave360() = keyCard(360, "clave-360")

    @Test fun clave412() = keyCard(412, "clave-412")
}
