package com.luis.end_security.core.network.api

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiMappers
import com.luis.end_security.core.network.dto.ActivityDto
import com.luis.end_security.core.network.dto.SessionsDto
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.ActivityPage
import com.luis.end_security.feature.activity.domain.DeviceSession
import com.luis.end_security.feature.activity.domain.SessionApi
import java.util.UUID
import javax.inject.Inject

/** [SessionApi] sobre el contrato (EP-23…EP-25). */
class SessionApiImpl @Inject constructor(
    private val client: ApiClient,
) : SessionApi {

    override suspend fun listSessions(): ApiResult<List<DeviceSession>> =
        client.call<Unit, SessionsDto, List<DeviceSession>>("GET", "/sessions", null, null, SessionsDto.serializer(), true) { dto ->
            dto.sessions.map {
                DeviceSession(
                    id = ApiMappers.uuid(it.id),
                    deviceName = it.deviceName,
                    createdAt = ApiMappers.instant(it.createdAt),
                    lastUsedAt = ApiMappers.instant(it.lastUsedAt),
                    current = it.current,
                )
            }
        }

    override suspend fun revokeSession(id: UUID): ApiResult<Unit> =
        client.callNoContent<Unit>("DELETE", "/sessions/$id", null, null, authenticated = true)

    override suspend fun listActivity(before: Long?, limit: Int): ApiResult<ActivityPage> {
        require(limit in 1..100) { "limit fuera de rango" }
        val query = buildString {
            append("/activity?limit=").append(limit)
            if (before != null) append("&before=").append(before)
        }
        return client.call<Unit, ActivityDto, ActivityPage>("GET", query, null, null, ActivityDto.serializer(), true) { dto ->
            ActivityPage(
                events = dto.events.map {
                    ActivityEvent(
                        id = it.id,
                        occurredAt = ApiMappers.instant(it.occurredAt),
                        eventType = it.eventType,
                        success = when (it.outcome) {
                            "success" -> true
                            "failure" -> false
                            else -> throw IllegalArgumentException("outcome desconocido")
                        },
                        deviceName = it.deviceName,
                    )
                },
                hasMore = dto.hasMore,
            )
        }
    }
}
