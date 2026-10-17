package com.luis.end_security.feature.auth.ui.login

/**
 * Estado de Iniciar sesión (SCR-04). La contraseña maestra NO está aquí (regla no negociable 6):
 * vive en un TextFieldState de la pantalla y el ViewModel solo sabe si está vacía.
 */
data class LoginUiState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val canSubmit: Boolean = false,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    /** Segundos de bloqueo por demasiados intentos (DEC-32); 0 sin bloqueo. */
    val lockoutSecondsLeft: Int = 0,
) {
    val isLockedOut: Boolean get() = lockoutSecondsLeft > 0

    override fun toString(): String =
        "LoginUiState(canSubmit=$canSubmit, error=$error, lockoutSecondsLeft=$lockoutSecondsLeft, ***)"
}

/** Un solo error para correo y contraseña, para no revelar si el correo tiene cuenta. */
enum class LoginError { InvalidCredentials }

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    /** La pantalla avisa si el campo de contraseña quedó vacío o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : LoginAction

    /** «Entrar». */
    data object Submit : LoginAction
}

sealed interface LoginEffect {
    /** SCR-07: segundo paso de la verificación en dos pasos. */
    data object NavigateToTwoFactor : LoginEffect
}
