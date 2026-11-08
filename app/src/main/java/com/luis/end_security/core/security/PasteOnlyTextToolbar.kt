package com.luis.end_security.core.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Para campos con secretos visibles (como el código de respaldo): el menú de texto solo ofrece
 * Pegar y Seleccionar todo. Copiar y Cortar dejarían el secreto en el portapapeles sin marca de
 * sensible ni borrado (regla no negociable 7). `BasicSecureTextField` ya lo hace por su cuenta.
 */
@Composable
fun PasteOnlyTextToolbar(content: @Composable () -> Unit) {
    val platform = LocalTextToolbar.current
    val pasteOnly = remember(platform) { PasteOnly(platform) }
    CompositionLocalProvider(LocalTextToolbar provides pasteOnly, content = content)
}

private class PasteOnly(private val delegate: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect = rect,
            onCopyRequested = null,
            onPasteRequested = onPasteRequested,
            onCutRequested = null,
            onSelectAllRequested = onSelectAllRequested,
        )
    }

    override fun hide() = delegate.hide()
}
