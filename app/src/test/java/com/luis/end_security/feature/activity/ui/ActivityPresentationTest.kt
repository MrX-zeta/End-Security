package com.luis.end_security.feature.activity.ui

import com.luis.end_security.R
import com.luis.end_security.feature.activity.domain.ActivityKind
import com.luis.end_security.feature.activity.event
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** La tabla de la tarea 19: título e ícono por `eventType` (los 18 del contrato y el desconocido). */
class ActivityPresentationTest {

    private val at = Instant.parse("2026-11-04T16:00:00Z")

    private val expected = mapOf(
        "account_registered" to (R.string.activity_kind_account_registered to R.drawable.ic_user_20),
        "email_verification_sent" to (R.string.activity_kind_email_verification_sent to R.drawable.ic_mail_20),
        "email_verified" to (R.string.activity_kind_email_verified to R.drawable.ic_mail_20),
        "login" to (R.string.activity_kind_login to R.drawable.ic_log_out_20),
        "totp_verification" to (R.string.activity_kind_totp_verification to R.drawable.ic_shield_check_22),
        "recovery_code_login" to (R.string.activity_kind_recovery_code_login to R.drawable.ic_key_20),
        "account_locked" to (R.string.activity_kind_account_locked to R.drawable.ic_alert_20),
        "token_reuse_detected" to (R.string.activity_kind_token_reuse_detected to R.drawable.ic_shield_alert_20),
        "logout" to (R.string.activity_kind_logout to R.drawable.ic_log_out_20),
        "session_revoked" to (R.string.activity_kind_session_revoked to R.drawable.ic_smartphone_20),
        "master_password_changed" to (R.string.activity_kind_master_password_changed to R.drawable.ic_key_20),
        "email_change_requested" to (R.string.activity_kind_email_change_requested to R.drawable.ic_mail_20),
        "email_changed" to (R.string.activity_kind_email_changed to R.drawable.ic_mail_20),
        "two_factor_enabled" to (R.string.activity_kind_two_factor_enabled to R.drawable.ic_shield_check_22),
        "two_factor_disabled" to (R.string.activity_kind_two_factor_disabled to R.drawable.ic_shield_alert_20),
        "recovery_codes_regenerated" to (R.string.activity_kind_recovery_codes_regenerated to R.drawable.ic_key_20),
        "account_deleted" to (R.string.activity_kind_account_deleted to R.drawable.ic_trash_20),
        "account_anonymized" to (R.string.activity_kind_account_anonymized to R.drawable.ic_trash_20),
    )

    @Test
    fun `los 18 tipos del contrato tienen su titulo e icono`() {
        assertEquals(18, expected.size)
        for ((wire, pair) in expected) {
            val ok = event(1, at, type = wire)
            assertEquals("título de $wire", pair.first, ok.rowTitleRes())
            assertEquals("ícono de $wire", pair.second, ok.rowIconRes())
        }
    }

    @Test
    fun `un tipo desconocido dice Actividad en tu cuenta`() {
        val unknown = event(1, at, type = "tipo_nuevo")
        assertEquals(R.string.activity_kind_unknown, unknown.rowTitleRes())
        assertEquals(R.drawable.ic_history_20, unknown.rowIconRes())
    }

    @Test
    fun `un login fallido es Acceso fallido con la alerta`() {
        val failed = event(1, at, type = "login", success = false)
        assertEquals(R.string.activity_kind_login_failed, failed.rowTitleRes())
        assertEquals(R.drawable.ic_alert_20, failed.rowIconRes())
    }

    @Test
    fun `otro evento fallido conserva su titulo pero usa la alerta`() {
        for (wire in listOf("totp_verification", "recovery_code_login", "master_password_changed")) {
            val failed = event(1, at, type = wire, success = false)
            assertEquals(expected.getValue(wire).first, failed.rowTitleRes())
            assertEquals(R.drawable.ic_alert_20, failed.rowIconRes())
        }
    }

    @Test
    fun `los tipos conocidos cubren todo el enum`() {
        assertEquals(ActivityKind.entries.size - 1, expected.size)
    }

    @Test
    fun `la fecha de un dia pasado va en espanol`() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals("lunes 2 de noviembre", formatDay(LocalDate.of(2026, 11, 2), today))
        // Otro año: incluye el año.
        assertEquals("martes 31 de diciembre de 2024", formatDay(LocalDate.of(2024, 12, 31), today))
    }
}
