package com.luis.end_security.feature.activity.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Cuándo se usó por última vez un dispositivo, ya redondeado a lo que dice la pantalla (Figma 47:1817). */
sealed interface LastUsed {
    /** «Activo ahora»: la sesión actual o una usada hace menos de 5 minutos. */
    data object Now : LastUsed

    data class Minutes(val count: Int) : LastUsed

    data class Hours(val count: Int) : LastUsed

    data class Days(val count: Int) : LastUsed

    data class Weeks(val count: Int) : LastUsed

    companion object {
        /** Menos de esto es «Activo ahora». */
        val ACTIVE_WINDOW: Duration = Duration.ofMinutes(5)

        /**
         * «Hace N min/h/días/semanas» desde [lastUsedAt]. La sesión [current] y las usadas hace menos de 5 minutos (o con una
         * hora del servidor en el futuro por desfase de reloj) son «Activo ahora». Se redondea hacia abajo.
         */
        fun of(lastUsedAt: Instant, now: Instant, current: Boolean): LastUsed {
            if (current) return Now
            val elapsed = Duration.between(lastUsedAt, now)
            return when {
                elapsed < ACTIVE_WINDOW -> Now
                elapsed < Duration.ofHours(1) -> Minutes(elapsed.toMinutes().toInt())
                elapsed < Duration.ofDays(1) -> Hours(elapsed.toHours().toInt())
                elapsed < Duration.ofDays(7) -> Days(elapsed.toDays().toInt())
                else -> Weeks((elapsed.toDays() / 7).toInt())
            }
        }
    }
}

/**
 * Los `eventType` del contrato (los 18 de `ActivityEvent`). Uno que la app no conoce (un servidor más nuevo) es [Unknown] y la
 * pantalla dice «Actividad en tu cuenta».
 */
enum class ActivityKind(val wire: String) {
    AccountRegistered("account_registered"),
    EmailVerificationSent("email_verification_sent"),
    EmailVerified("email_verified"),
    Login("login"),
    TotpVerification("totp_verification"),
    RecoveryCodeLogin("recovery_code_login"),
    AccountLocked("account_locked"),
    TokenReuseDetected("token_reuse_detected"),
    Logout("logout"),
    SessionRevoked("session_revoked"),
    MasterPasswordChanged("master_password_changed"),
    EmailChangeRequested("email_change_requested"),
    EmailChanged("email_changed"),
    TwoFactorEnabled("two_factor_enabled"),
    TwoFactorDisabled("two_factor_disabled"),
    RecoveryCodesRegenerated("recovery_codes_regenerated"),
    AccountDeleted("account_deleted"),
    AccountAnonymized("account_anonymized"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(value: String): ActivityKind = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

/** Los eventos de un mismo día local, del más reciente al más antiguo. */
data class ActivityDay(val date: LocalDate, val events: List<ActivityEvent>)

/** Cómo se llama un día frente a «hoy». */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    /** La fecha completa: «lunes 2 de noviembre». */
    data class Date(val date: LocalDate) : DayLabel

    companion object {
        fun of(day: LocalDate, today: LocalDate): DayLabel = when (day) {
            today -> Today
            today.minusDays(1) -> Yesterday
            else -> Date(day)
        }
    }
}

object ActivityRules {
    /** Eventos que se piden por página (EP-25). */
    const val PAGE_SIZE = 50

    /**
     * Agrupa por día **local** (en [zone]) conservando el orden recibido (del más reciente al más antiguo): un evento a las
     * 23:59 y otro a las 00:01 caen en días distintos aunque estén a dos minutos.
     */
    fun groupByDay(events: List<ActivityEvent>, zone: ZoneId): List<ActivityDay> {
        val days = LinkedHashMap<LocalDate, MutableList<ActivityEvent>>()
        for (event in events) {
            days.getOrPut(event.occurredAt.atZone(zone).toLocalDate()) { mutableListOf() }.add(event)
        }
        return days.map { (date, list) -> ActivityDay(date, list) }
    }

    /** Milisegundos que faltan para la próxima medianoche local: entonces «Hoy» pasa a ser «Ayer». Siempre al menos 1. */
    fun millisUntilNextDay(now: Instant, zone: ZoneId): Long {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return maxOf(1L, Duration.between(now, tomorrow).toMillis())
    }

    /**
     * Une una página nueva a lo ya cargado sin duplicar: los ids que ya están se ignoran (una página puede traer de nuevo el
     * último evento si el servidor la corta por un empate).
     */
    fun appendPage(current: List<ActivityEvent>, page: List<ActivityEvent>): List<ActivityEvent> {
        val seen = current.mapTo(HashSet()) { it.id }
        return current + page.filter { seen.add(it.id) }
    }
}
