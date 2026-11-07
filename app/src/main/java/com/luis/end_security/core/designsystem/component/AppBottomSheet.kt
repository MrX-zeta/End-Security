package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** El anfitrión de avisos de una hoja y si su contenido ya le dio un lugar ([SheetMessageSlot]). */
@Stable
class SheetMessages internal constructor(val host: MessageHostState) {
    /** `true` si el contenido de la hoja puso su propio [SheetMessageSlot] (p. ej. encima de su botón inferior). */
    var claimed by mutableStateOf(false)
        internal set
}

private val LocalSheetMessages = compositionLocalOf<SheetMessages?> { null }

/**
 * Una hoja inferior con **su propio anfitrión de avisos** (tarea 29).
 *
 * Una `ModalBottomSheet` es **otra ventana encima de la principal**: el anfitrión de `AppNavHost` queda detrás de ella y un aviso pedido
 * desde dentro (p. ej. «Copiada · se borra del portapapeles en 30 s» en la hoja del Generador) salía sin verse. Aquí cada hoja provee
 * su propio [MessageHostState] con [LocalMessageHost]; [HostedMessage] usa el más cercano, así que lo que se pide dentro de la hoja
 * aparece **dentro de ella**. Al cerrarse la hoja su anfitrión se limpia.
 *
 * El aviso se dibuja donde el contenido ponga [SheetMessageSlot] (por ejemplo, justo encima de su botón inferior); si no pone ninguno,
 * al final de la hoja. Todas las hojas de la app usan este componente para que ninguna se quede sin anfitrión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled: Boolean = true,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        properties = properties,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        val host = rememberMessageHostState()
        val sheetMessages = remember(host) { SheetMessages(host) }
        // Al cerrarse la hoja su anfitrión se limpia (y sus relojes se cancelan).
        DisposableEffect(host) { onDispose { host.clear() } }
        CompositionLocalProvider(LocalMessageHost provides host, LocalSheetMessages provides sheetMessages) {
            content()
            if (!sheetMessages.claimed) MessageHost(host, bottomPadding = 12.dp, horizontalPadding = 32.dp)
        }
    }
}

/**
 * El lugar del aviso dentro de la hoja: se pone **justo encima del botón inferior**. Ocupa 0 de alto mientras no hay aviso y, cuando lo
 * hay, deja 12 dp entre el aviso y el botón. Entra subiendo 16 dp en 200 ms y sale en 150 ms (el mismo movimiento de la tarea 26).
 * Fuera de una hoja no hace nada.
 */
@Composable
fun SheetMessageSlot(modifier: Modifier = Modifier) {
    val sheet = LocalSheetMessages.current ?: return
    DisposableEffect(sheet) {
        sheet.claimed = true
        onDispose { sheet.claimed = false }
    }
    MessageHost(sheet.host, modifier = modifier, bottomPadding = 12.dp, horizontalPadding = 0.dp)
}
