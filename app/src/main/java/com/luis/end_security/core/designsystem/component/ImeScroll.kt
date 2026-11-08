package com.luis.end_security.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

/** Lo que se espera a que el teclado termine de subir (su animación) antes de acomodar el contenido con su altura final. */
const val IME_SETTLE_MILLIS = 250L

/** Hay teclado en pantalla. */
@Composable
fun isKeyboardVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

/**
 * Con el teclado abierto, desplaza lo que lleva [requester] hasta dejarlo **completo** a la vista (tarea 23, paso 5.5). Se espera
 * a que el teclado deje de moverse ([IME_SETTLE_MILLIS]): con la altura de la mitad de la animación el contenido quedaba cortado.
 * Úsalo con `Modifier.bringIntoViewRequester(requester)` en el bloque (campo + botón) que debe verse.
 */
@Composable
fun BringIntoViewWhenKeyboardOpens(requester: BringIntoViewRequester) {
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            delay(IME_SETTLE_MILLIS)
            requester.bringIntoView()
        }
    }
}

/** Atajo de [BringIntoViewWhenKeyboardOpens] para un solo elemento (típicamente el botón principal de una vista con campos). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewWhenKeyboardOpens(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    BringIntoViewWhenKeyboardOpens(requester)
    bringIntoViewRequester(requester)
}

/**
 * Un campo (con su etiqueta y su mensaje) que, al tener el foco con el teclado abierto, se desplaza hasta quedar **completo** a la
 * vista, ya con la altura final del teclado. El cursor de `BasicTextField` solo pide su propia línea, y el campo quedaba a medias.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.keepVisibleWhenFocusedWithKeyboard(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) {
            delay(IME_SETTLE_MILLIS)
            requester.bringIntoView()
        }
    }
    onFocusChanged { focused = it.hasFocus }.bringIntoViewRequester(requester)
}
