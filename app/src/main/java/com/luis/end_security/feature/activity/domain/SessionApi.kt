package com.luis.end_security.feature.activity.domain

import com.luis.end_security.core.common.ApiResult
import java.time.Instant
import java.util.UUID

/** Un dispositivo con sesión abierta (`Session` del contrato). */
data class DeviceSession(
    val id: UUID,
    val deviceName: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val current: Boolean,
)

/** Un evento de la actividad de la cuenta (`ActivityEvent` del contrato). */
data class ActivityEvent(
    val id: Long,
    val occurredAt: Instant,
    /** Valor del contrato, p. ej. `login` o `two_factor_enabled`. */
    val eventType: String,
    val success: Boolean,
    val deviceName: String?,
)

class ActivityPage(
    val events: List<ActivityEvent>,
    val hasMore: Boolean,
)

/** Dispositivos y actividad (contrato `docs/api`, EP-23…EP-25). Requieren sesión. */
interface SessionApi {
    /** EP-23: la sesión actual primero. */
    suspend fun listSessions(): ApiResult<List<DeviceSession>>

    /** EP-24. */
    suspend fun revokeSession(id: UUID): ApiResult<Unit>

    /** EP-25: del más reciente al más antiguo; [before] es el id del último recibido. */
    suspend fun listActivity(before: Long? = null, limit: Int = 50): ApiResult<ActivityPage>
}
