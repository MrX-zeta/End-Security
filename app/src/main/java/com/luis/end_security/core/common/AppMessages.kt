package com.luis.end_security.core.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un aviso que una operación pide mostrar aunque la pantalla que lo pidió ya no esté (p. ej. «Gmail se guardó» al volver a la lista).
 * Lleva el recurso de texto y sus argumentos —nunca el texto armado— para que quien lo dibuja use el idioma actual. No lleva
 * contraseñas: solo nombres de elementos y frases fijas.
 */
class AppMessage(
    @param:StringRes val text: Int,
    /** Solo nombres de elementos: nunca contraseñas, llaves ni tokens. */
    val args: List<String> = emptyList(),
    @param:DrawableRes val icon: Int,
) {
    override fun toString(): String = "AppMessage(text=$text)"
}

/**
 * Buzón de avisos de toda la app (tarea 26). Los ViewModels piden un aviso con [post]; el anfitrión de avisos de `AppNavHost` lo
 * recoge y lo dibuja encima de la barra inferior. Un aviso sin nadie escuchando se pierde: es un resultado momentáneo, no un estado.
 */
@Singleton
class AppMessages @Inject constructor() {
    private val flow = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<AppMessage> = flow.asSharedFlow()

    fun post(message: AppMessage) {
        flow.tryEmit(message)
    }
}
