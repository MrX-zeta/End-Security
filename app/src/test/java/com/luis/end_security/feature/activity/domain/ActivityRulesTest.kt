package com.luis.end_security.feature.activity.domain

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.feature.activity.FakeSessionApi
import com.luis.end_security.feature.activity.event
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ActivityRulesTest {

    private val now = Instant.parse("2026-11-04T18:00:00Z")

    private fun lastUsed(ago: Duration, current: Boolean = false) = LastUsed.of(now.minus(ago), now, current)

    // --- Tiempo relativo de los dispositivos ---

    @Test
    fun `la sesion actual es Activo ahora aunque su hora sea vieja`() {
        assertEquals(LastUsed.Now, lastUsed(Duration.ofDays(30), current = true))
    }

    @Test
    fun `menos de 5 minutos es Activo ahora y 5 minutos ya es Hace 5 min`() {
        assertEquals(LastUsed.Now, lastUsed(Duration.ZERO))
        assertEquals(LastUsed.Now, lastUsed(Duration.ofMinutes(4).plusSeconds(59)))
        assertEquals(LastUsed.Minutes(5), lastUsed(Duration.ofMinutes(5)))
    }

    @Test
    fun `minutos, horas, dias y semanas se redondean hacia abajo`() {
        assertEquals(LastUsed.Minutes(59), lastUsed(Duration.ofMinutes(59).plusSeconds(59)))
        assertEquals(LastUsed.Hours(1), lastUsed(Duration.ofMinutes(60)))
        assertEquals(LastUsed.Hours(23), lastUsed(Duration.ofHours(23).plusMinutes(59)))
        assertEquals(LastUsed.Days(1), lastUsed(Duration.ofHours(24)))
        assertEquals(LastUsed.Days(2), lastUsed(Duration.ofDays(2).plusHours(5)))
        assertEquals(LastUsed.Days(6), lastUsed(Duration.ofDays(6).plusHours(23)))
        assertEquals(LastUsed.Weeks(1), lastUsed(Duration.ofDays(7)))
        assertEquals(LastUsed.Weeks(3), lastUsed(Duration.ofDays(25)))
        assertEquals(LastUsed.Weeks(52), lastUsed(Duration.ofDays(365)))
    }

    @Test
    fun `una hora del servidor en el futuro es Activo ahora`() {
        assertEquals(LastUsed.Now, LastUsed.of(now.plusSeconds(90), now, current = false))
    }

    // --- Tipos de evento ---

    @Test
    fun `los 18 eventType del contrato se reconocen y uno desconocido no`() {
        val contract = listOf(
            "account_registered", "email_verification_sent", "email_verified", "login", "totp_verification",
            "recovery_code_login", "account_locked", "token_reuse_detected", "logout", "session_revoked",
            "master_password_changed", "email_change_requested", "email_changed", "two_factor_enabled",
            "two_factor_disabled", "recovery_codes_regenerated", "account_deleted", "account_anonymized",
        )
        assertEquals(18, contract.size)
        contract.forEach { assertTrue("$it", ActivityKind.fromWire(it) != ActivityKind.Unknown) }
        assertEquals(18, contract.map { ActivityKind.fromWire(it) }.toSet().size)
        assertEquals(ActivityKind.Unknown, ActivityKind.fromWire("algo_nuevo_del_servidor"))
        assertEquals(ActivityKind.Unknown, ActivityKind.fromWire(""))
        assertEquals(19, ActivityKind.entries.size)
    }

    // --- Agrupación por día local ---

    private val mexico = ZoneId.of("America/Mexico_City")

    @Test
    fun `agrupa por dia local conservando el orden y un evento a las 2359 y otro a las 0001 son dias distintos`() {
        // Hora local de Ciudad de México (UTC-6 en noviembre): 23:59 del 3 y 00:01 del 4 están a dos minutos.
        val laterAfterMidnight = Instant.parse("2026-11-04T06:01:00Z") // 00:01 local del 4
        val beforeMidnight = Instant.parse("2026-11-04T05:59:00Z") // 23:59 local del 3
        val earlier = Instant.parse("2026-11-03T20:00:00Z") // 14:00 local del 3
        val events = listOf(event(3, laterAfterMidnight), event(2, beforeMidnight), event(1, earlier))
        val days = ActivityRules.groupByDay(events, mexico)
        assertEquals(listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3)), days.map { it.date })
        assertEquals(listOf(3L), days[0].events.map { it.id })
        assertEquals(listOf(2L, 1L), days[1].events.map { it.id })
    }

    @Test
    fun `la misma hora UTC cae en otro dia segun la zona`() {
        val instant = Instant.parse("2026-11-04T03:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 4), ActivityRules.groupByDay(listOf(event(1, instant)), ZoneId.of("UTC")).single().date)
        assertEquals(LocalDate.of(2026, 11, 3), ActivityRules.groupByDay(listOf(event(1, instant)), mexico).single().date)
    }

    @Test
    fun `sin eventos no hay dias`() {
        assertTrue(ActivityRules.groupByDay(emptyList(), mexico).isEmpty())
    }

    @Test
    fun `Hoy, Ayer y la fecha`() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(DayLabel.Today, DayLabel.of(today, today))
        assertEquals(DayLabel.Yesterday, DayLabel.of(today.minusDays(1), today))
        assertEquals(DayLabel.Date(today.minusDays(2)), DayLabel.of(today.minusDays(2), today))
        // Tras la medianoche, lo que era «Hoy» pasa a «Ayer».
        assertEquals(DayLabel.Yesterday, DayLabel.of(today, today.plusDays(1)))
    }

    @Test
    fun `los milisegundos hasta la medianoche local`() {
        val at2330 = Instant.parse("2026-11-04T05:30:00Z") // 23:30 local del 3 en Ciudad de México
        assertEquals(Duration.ofMinutes(30).toMillis(), ActivityRules.millisUntilNextDay(at2330, mexico))
        val exactlyMidnight = Instant.parse("2026-11-04T06:00:00Z")
        assertEquals(Duration.ofDays(1).toMillis(), ActivityRules.millisUntilNextDay(exactlyMidnight, mexico))
    }

    @Test
    fun `una pagina nueva no duplica lo que ya esta`() {
        val base = Instant.parse("2026-11-04T18:00:00Z")
        val first = listOf(event(5, base), event(4, base))
        val page = listOf(event(4, base), event(3, base), event(2, base))
        val merged = ActivityRules.appendPage(first, page)
        assertEquals(listOf(5L, 4L, 3L, 2L), merged.map { it.id })
        // Dos veces la misma página no cambia nada.
        assertEquals(merged.map { it.id }, ActivityRules.appendPage(merged, page).map { it.id })
    }

    // --- Cerrar varias sesiones ---

    private val a = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val b = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val c = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    @Test
    fun `cerrar varias sesiones llama a EP-24 en serie y en orden`() = runBlocking {
        val api = FakeSessionApi()
        val outcome = RevokeSessions(api)(listOf(a, b, c))
        assertEquals(listOf(a, b, c), api.revoked)
        assertEquals(listOf(a, b, c), outcome.closed)
        assertTrue(outcome.allClosed)
    }

    @Test
    fun `si una falla las demas se intentan y queda en la lista de fallidas`() = runBlocking {
        val api = FakeSessionApi()
        api.revokeResults[b] = ApiResult.Failure(ApiError.NetworkUnavailable)
        val outcome = RevokeSessions(api)(listOf(a, b, c))
        assertEquals(listOf(a, b, c), api.revoked)
        assertEquals(listOf(a, c), outcome.closed)
        assertEquals(listOf(b), outcome.failed)
        assertFalse(outcome.allClosed)
    }

    @Test
    fun `una sesion que el servidor ya no tiene cuenta como cerrada`() = runBlocking {
        val api = FakeSessionApi()
        api.revokeResults[a] = ApiResult.Failure(ApiError.NotFound)
        val outcome = RevokeSessions(api)(listOf(a))
        assertEquals(listOf(a), outcome.closed)
        assertTrue(outcome.failed.isEmpty())
    }

    @Test
    fun `si la sesion propia ya no es valida se deja de llamar`() = runBlocking {
        val api = FakeSessionApi()
        api.revokeResults[a] = ApiResult.Failure(ApiError.SessionRevoked)
        val outcome = RevokeSessions(api)(listOf(a, b, c))
        assertEquals("no se sigue llamando", listOf(a), api.revoked)
        assertEquals(listOf(a, b, c), outcome.failed)
        assertTrue(outcome.closed.isEmpty())
    }

    @Test
    fun `sin sesiones que cerrar no hace nada`() = runBlocking {
        val api = FakeSessionApi()
        val outcome = RevokeSessions(api)(emptyList())
        assertTrue(api.revoked.isEmpty())
        assertTrue(outcome.allClosed)
    }
}
