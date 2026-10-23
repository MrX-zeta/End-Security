package com.luis.end_security.feature.vault.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.feature.vault.domain.KeepVersion
import com.luis.end_security.feature.vault.ui.conflict.ConflictDetailScreen
import com.luis.end_security.feature.vault.ui.conflict.ConflictDetailUiState
import com.luis.end_security.feature.vault.ui.conflict.ConflictListRow
import com.luis.end_security.feature.vault.ui.conflict.ConflictListScreen
import com.luis.end_security.feature.vault.ui.conflict.ConflictListUiState
import com.luis.end_security.feature.vault.ui.conflict.ConflictStatus
import com.luis.end_security.feature.vault.ui.conflict.FieldKind
import com.luis.end_security.feature.vault.ui.conflict.SideKind
import com.luis.end_security.feature.vault.ui.conflict.VersionCard
import com.luis.end_security.feature.vault.ui.conflict.VersionField
import com.luis.end_security.feature.vault.ui.list.VaultListScreen
import com.luis.end_security.feature.vault.ui.list.VaultListUiState
import com.luis.end_security.feature.vault.ui.list.VaultRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Pantallas de la tarea 12 dibujadas en el emulador, con sus textos del diseño (Figma 69:3744, 62:3168, 65:3017,
 * 65:3145, 46:1196). Dibujan los composables sin `SecureScreen` (no está en el `Screen`, solo en el `Route`), así
 * que la captura que deja cada prueba en `cache/captures` no debilita ninguna protección de la app.
 */
@RunWith(AndroidJUnit4::class)
class ConflictScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            EndSecurityTheme {
                Box(Modifier.size(width = 412.dp, height = 917.dp)) { content() }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val at = Instant.now().minusSeconds(3_000)

    private fun live(site: String, passwordLength: Int, at: Instant) = VersionCard(
        SideKind.Live,
        at,
        listOf(
            VersionField(FieldKind.Name, "Netflix", false),
            VersionField(FieldKind.Username, "luis.ruiz@gmail.com", false),
            VersionField(FieldKind.Site, site, true),
        ),
        passwordLength = passwordLength,
        passwordDiffers = true,
    )

    @Test
    fun la_boveda_muestra_sin_conexion_y_el_aviso_de_conflictos() {
        render("scr18_scr44_boveda") {
            VaultListScreen(
                state = VaultListUiState(
                    accountInitial = "L",
                    isLoading = false,
                    rows = listOf(VaultRow.Item(UUID.randomUUID(), "Netflix", "luis.ruiz@gmail.com", "N")),
                    totalCount = 24,
                    conflictCount = 2,
                    offline = true,
                ),
                onTabSelected = {}, onQueryChange = {}, onLockNow = {}, onItemClick = {}, onAddClick = {}, onUndoDelete = {},
            )
        }
        composeRule.onNodeWithText("Sin conexión. Tus cambios se guardan y se subirán al reconectar.").assertIsDisplayed()
        composeRule.onNodeWithText("Resolver").assertIsDisplayed()
    }

    @Test
    fun por_resolver_lista_los_conflictos_y_su_aviso_de_resuelta() {
        render("scr19_scr20_por_resolver") {
            ConflictListScreen(
                state = ConflictListUiState(
                    rows = listOf(
                        ConflictListRow(UUID.randomUUID(), "Spotify", "S", at),
                        ConflictListRow(UUID.randomUUID(), "Amazon", "A", at.minusSeconds(86_400)),
                    ),
                    resolvedName = "Netflix",
                    isLoading = false,
                ),
                onBack = {}, onRowClick = {}, onTabSelected = {},
            )
        }
        composeRule.onNodeWithText("Spotify").assertIsDisplayed()
        composeRule.onNodeWithText("Netflix quedó resuelta").assertIsDisplayed()
    }

    @Test
    fun elegir_version_muestra_las_dos_versiones_con_su_contrasena_oculta() {
        render("scr21_elegir_version") {
            ConflictDetailScreen(
                state = ConflictDetailUiState(
                    status = ConflictStatus.Ready,
                    name = "Netflix",
                    local = live("netflix.com", 14, at),
                    remote = live("netflix.com/mx", 12, at.plusSeconds(1_800)),
                    selected = KeepVersion.Local,
                ),
                onBack = {}, onTabSelected = {}, onSelect = {}, onToggleLocalPassword = {}, onToggleRemotePassword = {},
                onKeep = {}, onKeepSide = {}, onRetry = {},
            )
        }
        composeRule.onNodeWithText("Elige una versión").assertIsDisplayed()
        composeRule.onNodeWithText("Conservar esta versión").assertIsDisplayed()
    }

    @Test
    fun elegir_version_con_una_eliminacion_remota_usa_botones() {
        render("scr21_eliminada_en_otro_dispositivo") {
            ConflictDetailScreen(
                state = ConflictDetailUiState(
                    status = ConflictStatus.Ready,
                    name = "Netflix",
                    local = live("netflix.com", 14, at),
                    remote = VersionCard(SideKind.Deleted, at.plusSeconds(1_800)),
                    selected = KeepVersion.Local,
                ),
                onBack = {}, onTabSelected = {}, onSelect = {}, onToggleLocalPassword = {}, onToggleRemotePassword = {},
                onKeep = {}, onKeepSide = {}, onRetry = {},
            )
        }
        composeRule.onNodeWithText("Conservar la mía").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminarla").assertIsDisplayed()
    }
}
