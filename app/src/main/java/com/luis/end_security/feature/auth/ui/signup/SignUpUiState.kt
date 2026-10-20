package com.luis.end_security.feature.auth.ui.signup

import com.luis.end_security.core.common.ApiError

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
    /** Más de 128 caracteres tras normalizar: no se calcula el puntaje. */
    val exceedsMaxLength: Boolean = false,
    /** Tiene caracteres invisibles o de control que no se aceptan al fijar la contraseña (DEC-31). */
    val hasForbiddenCharacters: Boolean = false,
    val confirmationMatches: Boolean = false,
    /** «Las contraseñas no coinciden.» Solo al perder el foco o al intentar crear la cuenta. */
    val showMismatchError: Boolean = false,
    val termsAccepted: Boolean = false,
    val canSubmit: Boolean = false,
    /** Verificando la política, derivando las llaves y registrando. */
    val isLoading: Boolean = false,
    /** El registro no salió (sin red o límite de intentos). */
    val submitError: SignUpSubmitError? = null,
) {
    // Ni el correo ni el puntaje junto con la longitud deben terminar en un log (INV-06).
    override fun toString(): String = "SignUpUiState(canSubmit=$canSubmit, isLoading=$isLoading, ***)"
}

/** Por qué no se pudo registrar. */
sealed interface SignUpSubmitError {
    /** «No pudimos conectar. Intenta de nuevo.» */
    data object Generic : SignUpSubmitError

    /** «Demasiados intentos. Vuelve a intentarlo en N min.» */
    data class RateLimited(val minutes: Int) : SignUpSubmitError

    companion object {
        fun from(error: ApiError): SignUpSubmitError = when (error) {
            is ApiError.RateLimited -> RateLimited((error.retryAfterSeconds + 59) / 60)
            else -> Generic
        }
    }
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

sealed interface SignUpEffect {
    /** SCR-06 con el correo al que se envió el código. */
    data class NavigateToVerifyEmail(val email: String) : SignUpEffect {
        override fun toString(): String = "NavigateToVerifyEmail(***)"
    }
}
