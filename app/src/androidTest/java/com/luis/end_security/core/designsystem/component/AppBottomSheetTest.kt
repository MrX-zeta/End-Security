package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los avisos dentro de las hojas inferiores (tarea 29): una `ModalBottomSheet` es otra ventana encima de la principal, así que lo que se
 * pide desde dentro debe mostrarse dentro de ella, encima de su botón, y no en el anfitrión principal.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class AppBottomSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var mainHost: MessageHostState
    private var sheetOpen by mutableStateOf(true)
    private var requested by mutableStateOf(true)

    private fun setSheet() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                mainHost = rememberMessageHostState()
                CompositionLocalProvider(LocalMessageHost provides mainHost) {
                    Box(Modifier.fillMaxWidth().height(400.dp)) { MessageHost(mainHost) }
                    if (sheetOpen) {
                        AppBottomSheet(
                            onDismissRequest = { sheetOpen = false },
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text("Hoja de prueba")
                            // Lo que la pantalla de la hoja pide: aparece dentro de la hoja.
                            if (requested) HostedMessage(text = "Copiada · se borra del portapapeles en 30 s", icon = R.drawable.ic_check_16)
                            SheetMessageSlot()
                            Box(Modifier.fillMaxWidth().height(56.dp).testTag("boton-inferior"))
                        }
                    }
                }
            }
        }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun el_aviso_pedido_dentro_de_la_hoja_se_ve_dentro_de_ella_y_no_en_el_anfitrion_principal() {
        setSheet()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").assertIsDisplayed()
        assertEquals("el anfitrión principal no recibió nada", 0, mainHost.count)
    }

    @Test
    fun el_aviso_queda_encima_del_boton_inferior_con_su_separacion() {
        setSheet()
        composeRule.mainClock.advanceTimeBy(400)
        val message = composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").getBoundsInRoot()
        val button = composeRule.onNodeWithTag("boton-inferior").getBoundsInRoot()
        assertTrue("el aviso termina antes del botón (${message.bottom} <= ${button.top})", message.bottom.value <= button.top.value)
        // Entre el texto del aviso y el botón hay el relleno del propio aviso más los 12 dp de separación.
        assertTrue("al menos 12 dp de separación (${button.top.value - message.bottom.value})", button.top.value - message.bottom.value >= 12f)
    }

    @Test
    fun el_aviso_de_la_hoja_entra_subiendo_y_sale_mas_rapido() {
        composeRule.mainClock.autoAdvance = false
        requested = false
        setSheetOnly()
        composeRule.mainClock.advanceTimeBy(400)
        requested = true
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        val start = composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").getBoundsInRoot().top.value
        composeRule.mainClock.advanceTimeBy(400)
        val rest = composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").getBoundsInRoot().top.value
        assertTrue("entra desde abajo ($start > $rest)", start > rest + 0.5f && start - rest <= 16.5f)
        requested = false
        composeRule.mainClock.advanceTimeBy(Motion150)
        composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").assertDoesNotExist()
    }

    private fun setSheetOnly() {
        composeRule.setContent {
            EndSecurityTheme {
                mainHost = rememberMessageHostState()
                CompositionLocalProvider(LocalMessageHost provides mainHost) {
                    AppBottomSheet(
                        onDismissRequest = {},
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text("Hoja de prueba")
                        if (requested) HostedMessage(text = "Copiada · se borra del portapapeles en 30 s", icon = R.drawable.ic_check_16)
                        SheetMessageSlot()
                        Box(Modifier.fillMaxWidth().height(56.dp).testTag("boton-inferior"))
                    }
                }
            }
        }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun al_cerrar_la_hoja_su_aviso_desaparece_y_su_anfitrion_queda_limpio() {
        setSheet()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").assertIsDisplayed()
        sheetOpen = false
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").assertDoesNotExist()
        composeRule.onNodeWithText("Hoja de prueba").assertDoesNotExist()
        assertEquals(0, mainHost.count)
    }

    @Test
    fun una_hoja_sin_lugar_propio_dibuja_el_aviso_al_final_de_su_contenido() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                AppBottomSheet(
                    onDismissRequest = {},
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(Modifier.fillMaxWidth().height(56.dp).testTag("contenido"))
                    HostedMessage(text = "Listo", icon = R.drawable.ic_check_16)
                }
            }
        }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(400)
        val content = composeRule.onNodeWithTag("contenido").getBoundsInRoot()
        val message = composeRule.onNodeWithText("Listo").getBoundsInRoot()
        assertTrue("sin lugar propio el aviso va después del contenido", message.top.value >= content.bottom.value - 0.5f)
    }

    @Test
    fun la_hoja_real_del_generador_muestra_copiada_encima_de_usar_esta_contrasena() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            EndSecurityTheme {
                mainHost = rememberMessageHostState()
                CompositionLocalProvider(LocalMessageHost provides mainHost) {
                    AppBottomSheet(
                        onDismissRequest = {},
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        com.luis.end_security.feature.vault.ui.generator.GeneratorSheetContent(
                            state = com.luis.end_security.feature.vault.ui.generator.GeneratorUiState(
                                com.luis.end_security.core.crypto.GeneratorOptions(), password = "q7#Vt!m2Lp@9xR4w", strengthScore = 4, generation = 1,
                                copiedSeconds = 30,
                            ),
                            onRegenerate = {}, onCopy = {}, onLengthChange = {}, onLengthChangeFinished = {}, onToggle = {}, onUse = {},
                        )
                    }
                }
            }
        }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(400)
        val message = composeRule.onNodeWithText("Copiada · se borra del portapapeles en 30 s").getBoundsInRoot()
        val use = composeRule.onNodeWithText("Usar esta contraseña").getBoundsInRoot()
        assertTrue("el aviso se ve dentro de la hoja, encima del botón (${message.bottom} <= ${use.top})", message.bottom.value <= use.top.value)
        assertEquals("no salió en el anfitrión principal", 0, mainHost.count)
    }

    private companion object {
        const val Motion150 = 250L
    }
}
