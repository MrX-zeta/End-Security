package com.luis.end_security.feature.activity.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.luis.end_security.R
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.ActivityKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Título de un tipo de evento (tabla de la tarea 19). `login` fallido es «Acceso fallido». */
@StringRes
internal fun ActivityKind.titleRes(): Int = when (this) {
    ActivityKind.AccountRegistered -> R.string.activity_kind_account_registered
    ActivityKind.EmailVerificationSent -> R.string.activity_kind_email_verification_sent
    ActivityKind.EmailVerified -> R.string.activity_kind_email_verified
    ActivityKind.Login -> R.string.activity_kind_login
    ActivityKind.TotpVerification -> R.string.activity_kind_totp_verification
    ActivityKind.RecoveryCodeLogin -> R.string.activity_kind_recovery_code_login
    ActivityKind.AccountLocked -> R.string.activity_kind_account_locked
    ActivityKind.TokenReuseDetected -> R.string.activity_kind_token_reuse_detected
    ActivityKind.Logout -> R.string.activity_kind_logout
    ActivityKind.SessionRevoked -> R.string.activity_kind_session_revoked
    ActivityKind.MasterPasswordChanged -> R.string.activity_kind_master_password_changed
    ActivityKind.EmailChangeRequested -> R.string.activity_kind_email_change_requested
    ActivityKind.EmailChanged -> R.string.activity_kind_email_changed
    ActivityKind.TwoFactorEnabled -> R.string.activity_kind_two_factor_enabled
    ActivityKind.TwoFactorDisabled -> R.string.activity_kind_two_factor_disabled
    ActivityKind.RecoveryCodesRegenerated -> R.string.activity_kind_recovery_codes_regenerated
    ActivityKind.AccountDeleted -> R.string.activity_kind_account_deleted
    ActivityKind.AccountAnonymized -> R.string.activity_kind_account_anonymized
    ActivityKind.Unknown -> R.string.activity_kind_unknown
}

/** Ícono de un tipo de evento exitoso. Los fallidos usan siempre la alerta. */
@DrawableRes
internal fun ActivityKind.iconRes(): Int = when (this) {
    ActivityKind.AccountRegistered -> R.drawable.ic_user_20
    ActivityKind.EmailVerificationSent, ActivityKind.EmailVerified,
    ActivityKind.EmailChangeRequested, ActivityKind.EmailChanged,
    -> R.drawable.ic_mail_20
    ActivityKind.Login, ActivityKind.Logout -> R.drawable.ic_log_out_20
    ActivityKind.TotpVerification, ActivityKind.TwoFactorEnabled -> R.drawable.ic_shield_check_22
    ActivityKind.RecoveryCodeLogin, ActivityKind.MasterPasswordChanged, ActivityKind.RecoveryCodesRegenerated -> R.drawable.ic_key_20
    ActivityKind.AccountLocked -> R.drawable.ic_alert_20
    ActivityKind.TokenReuseDetected, ActivityKind.TwoFactorDisabled -> R.drawable.ic_shield_alert_20
    ActivityKind.SessionRevoked -> R.drawable.ic_smartphone_20
    ActivityKind.AccountDeleted, ActivityKind.AccountAnonymized -> R.drawable.ic_trash_20
    ActivityKind.Unknown -> R.drawable.ic_history_20
}

/** Título de la fila: el del tipo; un `login` fallido es «Acceso fallido». */
@StringRes
internal fun ActivityEvent.rowTitleRes(): Int =
    if (!success && kind == ActivityKind.Login) R.string.activity_kind_login_failed else kind.titleRes()

/** Ícono de la fila: la alerta si el evento falló; si no, el del tipo. */
@DrawableRes
internal fun ActivityEvent.rowIconRes(): Int = if (success) kind.iconRes() else R.drawable.ic_alert_20

/** «lunes 2 de noviembre»; con otro año, también el año. La app está en español: no depende del idioma del teléfono. */
internal fun formatDay(day: LocalDate, today: LocalDate): String {
    val pattern = if (day.year == today.year) "EEEE d 'de' MMMM" else "EEEE d 'de' MMMM 'de' yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es")).format(day)
}
