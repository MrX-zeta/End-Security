package com.luis.end_security.feature.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.BuildConfig
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.settings.ui.legal.AboutBody
import com.luis.end_security.feature.settings.ui.legal.LICENSES_ROW_TAG
import com.luis.end_security.feature.settings.ui.legal.LICENSE_TEXT_TAG
import com.luis.end_security.feature.settings.ui.legal.LegalDocument
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentBody
import com.luis.end_security.feature.settings.ui.legal.LegalDocumentContent
import com.luis.end_security.feature.settings.ui.legal.LegalInfo
import com.luis.end_security.feature.settings.ui.legal.LegalMarkdown
import com.luis.end_security.feature.settings.ui.legal.LicensesScreen
import com.luis.end_security.feature.settings.ui.legal.Licenses
import com.luis.end_security.feature.settings.ui.legal.securityReportIntent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Documentos legales, Licencias y Acerca de (tarea 22) dibujados en el emulador. Ninguna de estas pantallas muestra secretos, así
 * que las capturas de `cache/captures` no debilitan ninguna protección.
 */
@RunWith(AndroidJUnit4::class)
class LegalScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val sample = LegalMarkdown.parse(
        """
        # Documento de prueba

        **End Security** · Última actualización: 4 de noviembre de 2026

        ## Resumen
        - Punto del resumen

        ## 1. Primera sección
        Texto de la primera.

        ## 2. Segunda sección
        Texto de la segunda.
        """.trimIndent(),
    )

    private var expanded by mutableStateOf(emptyList<Int>())

    private fun renderSample() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    LegalDocumentBody(
                        model = sample,
                        expanded = expanded.toSet(),
                        onToggle = { i -> expanded = if (i in expanded) expanded - i else expanded + i },
                        onToggleAll = { expanded = if (expanded.size == sample.sections.size) emptyList() else sample.sections.indices.toList() },
                    )
                }
            }
        }
    }

    private fun sectionHeader(title: String) = hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

    private fun stateOf(title: String): String? =
        composeRule.onNode(sectionHeader(title)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    // ---------------------------------------------------------------- Documento

    @Test
    fun elDocumentoMuestraElResumenAbiertoYLasSeccionesPlegadas() {
        renderSample()
        composeRule.onNodeWithText("Documento de prueba").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.legal_summary_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Punto del resumen").assertIsDisplayed()
        composeRule.onNodeWithText("1. Primera sección").assertIsDisplayed()
        composeRule.onNodeWithText("2. Segunda sección").assertIsDisplayed()
        // Las secciones empiezan cerradas: su texto no está.
        composeRule.onAllNodesWithText("Texto de la primera.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Texto de la segunda.").assertCountEquals(0)
        assertEquals(str(R.string.legal_collapsed), stateOf("1. Primera sección"))
        assertEquals(str(R.string.legal_collapsed), stateOf("2. Segunda sección"))
        composeRule.onNodeWithText(str(R.string.legal_open_all)).assertIsDisplayed()
        capture("tarea22-documento-plegado")
    }

    @Test
    fun abrirUnaSeccionMuestraSuTextoYLaMarcaComoExpandida() {
        renderSample()
        composeRule.onNodeWithText("1. Primera sección").performClick()
        composeRule.onNodeWithText("Texto de la primera.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Texto de la segunda.").assertCountEquals(0)
        assertEquals(str(R.string.legal_expanded), stateOf("1. Primera sección"))
        assertEquals(str(R.string.legal_collapsed), stateOf("2. Segunda sección"))
        // Y se vuelve a cerrar.
        composeRule.onNodeWithText("1. Primera sección").performClick()
        composeRule.onAllNodesWithText("Texto de la primera.").assertCountEquals(0)
        assertEquals(str(R.string.legal_collapsed), stateOf("1. Primera sección"))
    }

    @Test
    fun abrirTodoYCerrarTodoCambianTodasLasSecciones() {
        renderSample()
        composeRule.onNodeWithText(str(R.string.legal_open_all)).performClick()
        composeRule.onNodeWithText("Texto de la primera.").assertIsDisplayed()
        composeRule.onNodeWithText("Texto de la segunda.").assertIsDisplayed()
        assertEquals(str(R.string.legal_expanded), stateOf("1. Primera sección"))
        assertEquals(str(R.string.legal_expanded), stateOf("2. Segunda sección"))
        capture("tarea22-documento-abierto")
        // El botón pasa a «Cerrar todo».
        composeRule.onAllNodesWithText(str(R.string.legal_open_all)).assertCountEquals(0)
        composeRule.onNodeWithText(str(R.string.legal_close_all)).performClick()
        composeRule.onAllNodesWithText("Texto de la primera.").assertCountEquals(0)
        assertEquals(str(R.string.legal_collapsed), stateOf("2. Segunda sección"))
    }

    @Test
    fun losDocumentosRealesSeAbrenSinSesionYTraenSuResumen() {
        var document by mutableStateOf(LegalDocument.entries.first())
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { LegalDocumentContent(document = document) }
            }
        }
        for (entry in LegalDocument.entries) {
            document = entry
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(str(R.string.legal_summary_title)).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(str(R.string.legal_summary_title)).assertIsDisplayed()
            composeRule.onNodeWithText(str(R.string.legal_open_all)).assertIsDisplayed()
            composeRule.onAllNodesWithText(str(R.string.legal_load_error)).assertCountEquals(0)
            capture("tarea22-${entry.routeArg}")
        }
    }

    // ---------------------------------------------------------------- Licencias

    @Test
    fun licenciasMuestraLaListaFijaYAbreElTextoDeLaLicencia() {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { LicensesScreen(onBack = {}) }
            }
        }
        composeRule.onNodeWithText(str(R.string.licenses_title)).assertIsDisplayed()
        Licenses.entries.forEachIndexed { index, entry ->
            composeRule.onNodeWithTag("$LICENSES_ROW_TAG$index").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(entry.name).assertExists()
        }
        capture("tarea22-licencias")
        // La primera (Apache 2.0) abre el texto completo desde `assets/licenses/`.
        composeRule.onNodeWithTag("${LICENSES_ROW_TAG}0").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(LICENSE_TEXT_TAG) and hasText("Apache License", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LICENSE_TEXT_TAG).assertExists()
    }

    @Test
    fun cadaLicenciaDeLaListaTieneTextoEnLosAssets() {
        for (license in Licenses.entries.map { it.license }.toSet()) {
            val text = context.assets.open(license.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue("${license.assetPath} vacío", text.length > 200)
        }
    }

    // ---------------------------------------------------------------- Acerca de

    @Test
    fun acercaDeMuestraNombreVersionYLosCreditos() {
        var reported = 0
        var repository = 0
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) {
                    AboutBody(versionName = BuildConfig.VERSION_NAME, onReportSecurityIssue = { reported++ }, onOpenRepository = { repository++ })
                }
            }
        }
        composeRule.onNodeWithText(str(R.string.about_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_version, BuildConfig.VERSION_NAME)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.about_academic)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.about_hibp)).assertIsDisplayed()
        capture("tarea22-acerca-de")
        composeRule.onNodeWithText(str(R.string.about_report_security)).performClick()
        composeRule.onNodeWithText(str(R.string.about_repository)).performClick()
        assertEquals(1, reported)
        assertEquals(1, repository)
    }

    @Test
    fun reportarUnProblemaDeSeguridadAbreElCorreoConDestinatarioYAsunto() {
        val intent = securityReportIntent()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:", intent.dataString)
        assertArrayEquals(arrayOf(LegalInfo.PRIVACY_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Reporte de seguridad", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        // Nada del usuario viaja en el correo: sin cuerpo.
        assertEquals(null, intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
