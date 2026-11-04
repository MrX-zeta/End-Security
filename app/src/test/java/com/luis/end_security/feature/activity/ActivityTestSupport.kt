package com.luis.end_security.feature.activity

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.common.TimeZoneProvider
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.ActivityPage
import com.luis.end_security.feature.activity.domain.DeviceSession
import com.luis.end_security.feature.activity.domain.SessionApi
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Reloj del teléfono que se mueve a mano. */
class MutableClock(var now: Instant) : DeviceClock {
    override fun wallMillis(): Long = now.toEpochMilli()

    override fun elapsedRealtimeMillis(): Long = 0L
}

class FixedZone(var zone: ZoneId) : TimeZoneProvider {
    override fun zone(): ZoneId = zone
}

/** [SessionApi] en memoria con las mismas reglas de paginación que EP-25 (`before` = id exclusivo, del más reciente al más antiguo). */
class FakeSessionApi : SessionApi {
    var sessions: ApiResult<List<DeviceSession>> = ApiResult.Success(emptyList())
    var sessionsCalls = 0

    /** Resultado de cerrar cada sesión; por omisión, éxito. */
    val revokeResults = mutableMapOf<UUID, ApiResult<Unit>>()
    val revoked = mutableListOf<UUID>()

    /** Si no es `null`, cerrar una sesión espera a que se complete. */
    var revokeGate: CompletableDeferred<Unit>? = null

    /** Todos los eventos del servidor, en cualquier orden. */
    var events: List<ActivityEvent> = emptyList()
    var activityError: ApiError? = null
    val activityCalls = mutableListOf<Long?>()

    /** Eventos que cada página repite del final de la anterior (empate en el corte). */
    var overlap = 0

    override suspend fun listSessions(): ApiResult<List<DeviceSession>> {
        sessionsCalls++
        return sessions
    }

    override suspend fun revokeSession(id: UUID): ApiResult<Unit> {
        revokeGate?.await()
        revoked += id
        return revokeResults[id] ?: ApiResult.Success(Unit)
    }

    override suspend fun listActivity(before: Long?, limit: Int): ApiResult<ActivityPage> {
        activityCalls += before
        activityError?.let { return ApiResult.Failure(it) }
        val sorted = events.sortedByDescending { it.id }
        val remaining = sorted.filter { before == null || it.id < before }
        val repeated = if (before == null) emptyList() else sorted.filter { it.id >= before }.takeLast(overlap)
        val page = (repeated + remaining).take(limit)
        return ApiResult.Success(ActivityPage(page, hasMore = remaining.size > limit - repeated.size))
    }
}

fun session(
    name: String,
    lastUsed: Instant,
    current: Boolean = false,
    id: UUID = UUID.nameUUIDFromBytes(name.toByteArray()),
) = DeviceSession(id, name, lastUsed.minusSeconds(3_600), lastUsed, current)

fun event(
    id: Long,
    at: Instant,
    type: String = "login",
    success: Boolean = true,
    device: String? = "Pixel 7",
) = ActivityEvent(id, at, type, success, device)
