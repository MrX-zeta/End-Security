package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.core.common.EmailNormalizer
import com.luis.end_security.feature.auth.domain.EmailFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mantiene `account.email` de Room igual al del servidor (OP-26, Paso 3 de la tarea 21): si la cuenta cambió de correo en
 * otro teléfono, la recuperación de `login()` (que identifica la cuenta por el correo tecleado) tiene que seguir funcionando
 * con el correo nuevo. **Solo se actualiza si el `userId` coincide**: la respuesta de otra cuenta nunca toca la fila local.
 */
@Singleton
class AccountEmailSync @Inject constructor(
    private val accounts: AccountDao,
) {
    private val mutex = Mutex()

    /**
     * Un servidor no debe poder alterar la identificación local con esto (revisión de la tarea 21): el correo debe ser válido, venir
     * **ya normalizado** y caber en 254 caracteres, y la cuenta no debe haber cambiado de sal (eso es una recuperación de `login()`, no
     * un cambio de correo). Si algo no cuadra se ignora. Las escrituras van de una en una.
     */
    suspend fun apply(info: AccountInfo) = mutex.withLock {
        val local = accounts.get() ?: return@withLock
        val email = info.email
        if (local.userId != info.userId.toString() || local.email == email) return@withLock
        if (email.length > MAX_EMAIL || EmailNormalizer.normalize(email) != email || !EmailFormat.isValid(email)) return@withLock
        if (!local.kdfSalt.contentEquals(info.kdf.salt)) return@withLock
        accounts.updateEmail(local.userId, email)
    }

    private companion object {
        const val MAX_EMAIL = 254
    }
}

/**
 * [AccountApi] de la app: igual que el real, pero cada `getAccount()` que llega con un correo distinto del local lo guarda
 * ([AccountEmailSync]). Así cualquier pantalla que ya consulta la cuenta (Ajustes, 2 pasos, contraseña, eliminar) lo mantiene al
 * día sin hacer nada más.
 */
class EmailSyncingAccountApi @Inject constructor(
    private val inner: com.luis.end_security.core.network.api.AccountApiImpl,
    private val sync: AccountEmailSync,
) : AccountApi by inner {

    override suspend fun getAccount(): ApiResult<AccountInfo> = inner.getAccount().also { result ->
        if (result is ApiResult.Success) runCatching { sync.apply(result.value) }
    }
}
