package com.luis.end_security.feature.auth.domain

import com.luis.end_security.core.common.ApiResult
import kotlin.time.Duration

/** Siguiente paso después de [AuthRepository.login] o [AuthRepository.verifyEmail]. */
sealed interface AuthStep {
    /** La bóveda quedó abierta: a Bóveda · cargando. */
    data object VaultUnlocked : AuthStep

    /** Falta el segundo paso (SCR-07). */
    data object TwoFactorRequired : AuthStep

    /** Credenciales correctas pero el correo no está verificado (SCR-06). */
    data class EmailNotVerified(val email: String) : AuthStep {
        override fun toString(): String = "EmailNotVerified(***)"
    }

    /** El correo quedó verificado pero ya no está la llave en memoria (p. ej. murió el proceso): a Iniciar sesión. */
    data object SignInAgain : AuthStep
}

/** Resultado de desbloquear con la contraseña maestra (OP-19). */
sealed interface UnlockOutcome {
    data object Unlocked : UnlockOutcome

    /** «Contraseña incorrecta.»; [lockout] > 0 si con este fallo empieza el bloqueo (DEC-08). */
    data class WrongPassword(val lockout: Duration) : UnlockOutcome

    /** Todavía bloqueado por intentos. */
    data class LockedOut(val remaining: Duration) : UnlockOutcome

    /** No hay cuenta en este teléfono: a Iniciar sesión. */
    data object NoAccount : UnlockOutcome
}

/**
 * Registro, acceso, desbloqueo y cierre de sesión (OP-11…OP-19). Hace la cripto (DEC-05, DEC-31),
 * habla con [AuthApi], guarda la cuenta y los tokens y abre la bóveda. Las contraseñas llegan como
 * `CharSequence` (el `TextFieldState` de la pantalla) y nunca se guardan.
 *
 * Los errores son los del contrato (`ApiError`); los fallos locales que no deben dar pistas
 * (parámetros por debajo del piso, pvk que no abre) llegan como `ServerError`, el error genérico.
 */
interface AuthRepository {
    /** OP-11: genera `userId`, sal y `vault_key`, deriva las llaves y registra. */
    suspend fun register(email: String, password: CharSequence): ApiResult<Unit>

    /** OP-12: verifica el correo, guarda la sesión y abre la bóveda. */
    suspend fun verifyEmail(email: String, code: String): ApiResult<AuthStep>

    suspend fun resendVerification(email: String): ApiResult<Unit>

    /** OP-13/OP-14. */
    suspend fun login(email: String, password: CharSequence): ApiResult<AuthStep>

    /** OP-15. */
    suspend fun verifyTwoFactor(code: String): ApiResult<AuthStep>

    /** OP-16: [code] ya normalizado (`BackupCodeFormat`). */
    suspend fun verifyRecoveryCode(code: String): ApiResult<AuthStep>

    /** Salir del segundo paso: borra la `enc_key` que esperaba en memoria. */
    fun cancelPendingLogin()

    /** OP-19, sin red. */
    suspend fun unlock(password: CharSequence): UnlockOutcome

    /** OP-18: cierra la sesión en el servidor (aunque no haya red) y borra todo lo local. */
    suspend fun signOut()

    /** Nombre o correo para «¿No eres Ana?»; `null` sin cuenta. */
    suspend fun localAccountName(): String?

    /** Códigos de respaldo que quedan tras entrar con uno, si son 3 o menos; se lee una sola vez. */
    fun consumeRecoveryCodesNotice(): Int?
}
