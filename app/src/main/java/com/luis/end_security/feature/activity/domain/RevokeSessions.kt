package com.luis.end_security.feature.activity.domain

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import java.util.UUID
import javax.inject.Inject

/** Qué pasó al cerrar varias sesiones: las que se cerraron y las que no. */
data class RevokeOutcome(val closed: List<UUID>, val failed: List<UUID>) {
    val allClosed: Boolean get() = failed.isEmpty()
}

/**
 * OP-30. El contrato no tiene un endpoint para cerrar todas: EP-24 se llama por cada una, **en serie**. Si alguna falla, las
 * demás se intentan igual y quedan en [RevokeOutcome.failed] las que no se cerraron. Una sesión que el servidor ya no tiene
 * (`not_found`) cuenta como cerrada: el resultado que se quería ya está.
 *
 * Si el servidor dice que la sesión **propia** ya no es válida (`session_revoked`), se deja de llamar: el flujo de siempre
 * (`TokenManager`) cierra la sesión local y lo que falte queda como fallido.
 */
class RevokeSessions @Inject constructor(
    private val api: SessionApi,
) {
    suspend operator fun invoke(ids: List<UUID>): RevokeOutcome {
        val closed = mutableListOf<UUID>()
        val failed = mutableListOf<UUID>()
        var stopped = false
        for (id in ids) {
            if (stopped) {
                failed += id
                continue
            }
            when (val result = api.revokeSession(id)) {
                is ApiResult.Success -> closed += id
                is ApiResult.Failure -> when (result.error) {
                    ApiError.NotFound -> closed += id
                    ApiError.SessionRevoked -> {
                        failed += id
                        stopped = true
                    }
                    else -> failed += id
                }
            }
        }
        return RevokeOutcome(closed, failed)
    }
}
