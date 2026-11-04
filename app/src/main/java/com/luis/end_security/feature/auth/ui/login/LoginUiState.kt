package com.luis.end_security.feature.auth.ui.login

/**
 * Estado de Iniciar sesión (SCR-04). La contraseña maestra NO está aquí (regla no negociable 6):
 * vive en un TextFieldState de la pantalla y el ViewModel solo sabe si está vacía.
 */
data class LoginUiState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    /** «Escribe un correo válido…»: al salir del campo con texto o al tocar «Entrar»; nunca al escribir. */
    val showEmailError: Boolean = false,
    /** «Escribe tu contraseña maestra.»: solo al tocar «Entrar» con el campo vacío. */
    val showPasswordRequired: Boolean = false,
    /** A qué campo con error se desplaza la pantalla y se le da el foco. */
    val focusRequest: LoginFocusRequest? = null,
    /** Lo que se revisa al tocar «Entrar»; el botón ya no depende de esto para estar activo. */
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

enum class LoginField { Email, Password }

/** [id] cambia en cada intento, para que dos toques seguidos con el mismo error vuelvan a desplazar la pantalla. */
data class LoginFocusRequest(val field: LoginField, val id: Int)

/**
 * Un solo error para correo y contraseña, para no revelar si el correo tiene cuenta; y el genérico
 * («No pudimos conectar») para la red y lo que no debe dar pistas.
 */
enum class LoginError { InvalidCredentials, Generic }

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    /** El foco salió del correo: si tiene texto y está mal, se muestra su error. */
    data object EmailFocusLost : LoginAction

    /** La pantalla avisa si el campo de contraseña quedó vacío o no; nunca manda su contenido. */
    data class PasswordEmptinessChanged(val isEmpty: Boolean) : LoginAction

    /**
     * «Entrar», con la contraseña de paso para derivar las llaves (OP-13); el ViewModel no la guarda.
     * No es `data class` para que su `toString()` no la imprima.
     */
    class Submit(val password: CharSequence) : LoginAction {
        override fun toString(): String = "Submit(***)"
    }
}

sealed interface LoginEffect {
    /** SCR-07: segundo paso de la verificación en dos pasos. */
    data object NavigateToTwoFactor : LoginEffect

    /** SCR-39: la bóveda quedó abierta. */
    data object NavigateToVault : LoginEffect

    /** SCR-06: credenciales correctas pero el correo sin verificar. */
    data class NavigateToVerifyEmail(val email: String) : LoginEffect {
        override fun toString(): String = "NavigateToVerifyEmail(***)"
    }
}
