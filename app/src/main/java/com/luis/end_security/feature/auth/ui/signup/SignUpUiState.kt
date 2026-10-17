package com.luis.end_security.feature.auth.ui.signup

/**
 * Estado de Crear cuenta (SCR-05). Ningún texto de contraseña vive aquí (regla no negociable 6):
 * solo lo que se calcula de ellas.
 */
data class SignUpUiState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    /** Puntaje zxcvbn de 0 a 4; `null` con el campo vacío. */
    val strengthScore: Int? = null,
    val meetsLength: Boolean = false,
    val confirmationMatches: Boolean = false,
    /** «Las contraseñas no coinciden.» Solo al perder el foco o al intentar crear la cuenta. */
    val showMismatchError: Boolean = false,
    val termsAccepted: Boolean = false,
    val canSubmit: Boolean = false,
    /** Verificando la política antes de registrar. */
    val isLoading: Boolean = false,
) {
    // Ni el correo ni el puntaje junto con la longitud deben terminar en un log (INV-06).
    override fun toString(): String = "SignUpUiState(canSubmit=$canSubmit, isLoading=$isLoading, ***)"
}

sealed interface SignUpAction {
    data class EmailChanged(val value: String) : SignUpAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    /**
     * Las dos contraseñas, de paso: el ViewModel calcula y descarta, nunca las guarda.
     * No es `data class` para que su `toString()` no las imprima (regla no negociable 5).
     */
    class PasswordsChanged(val password: CharSequence, val confirmation: CharSequence) : SignUpAction {
        override fun toString(): String = "PasswordsChanged(***)"
    }

    /** Los campos se vaciaron (al salir de la pantalla o al pasar a segundo plano). */
    data object PasswordsCleared : SignUpAction

    data object ConfirmationFocusLost : SignUpAction

    data class TermsAcceptedChanged(val accepted: Boolean) : SignUpAction

    /**
     * «Crear cuenta» o «Listo» en el teclado de la confirmación. Lleva el texto que se va a
     * registrar: la política se revisa sobre ese mismo texto y no sobre el último calculado.
     */
    class Submit(val password: CharSequence, val confirmation: CharSequence) : SignUpAction {
        override fun toString(): String = "Submit(***)"
    }
}
