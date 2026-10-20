package com.luis.end_security.feature.vault.domain

import com.luis.end_security.core.common.ApiResult

/** Sincronización de la bóveda con el servidor (OP-32…OP-34). */
interface VaultSyncRepository {
    /**
     * OP-32: descarga inicial (`GET /vault/items?since=0`, paginada) a Room. Solo guarda bloques
     * cifrados; las lápidas no se guardan.
     */
    suspend fun initialDownload(): ApiResult<Unit>
}
