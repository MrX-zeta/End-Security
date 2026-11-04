package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.session.SessionLifecycleLock
import com.luis.end_security.core.session.VaultLockedException
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.account.domain.AccountApi
import com.luis.end_security.feature.account.domain.ChangeMasterPassword
import com.luis.end_security.feature.account.domain.ChangePasswordResult
import com.luis.end_security.feature.account.domain.MasterPasswordChange
import com.luis.end_security.feature.account.domain.ReauthRunner
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.MasterPasswordPolicy
import com.luis.end_security.feature.unlock.domain.BiometricUnlock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.CharacterCodingException
import java.util.UUID
import javax.inject.Inject

/**
 * OP-27 (DEC-05, DEC-41, TX-10), en este orden:
 * 1. reautenticación: la `auth_key` **actual** se deriva con los parámetros de `account` ([ReauthRunner]);
 * 2. llaves nuevas: sal de 16 bytes con `SecureRandom` y los parámetros por defecto → `master_key` → `enc_key` y
 *    `auth_key` nuevas (HKDF);
 * 3. la `vault_key` de [VaultSession] se vuelve a envolver con la `enc_key` nueva (nonce nuevo, AAD con
 *    `key_version + 1`);
 * 4. EP-17 con `expectedKeyVersion` = la local;
 * 5. con 200, una sola sentencia de Room reemplaza sal, parámetros, pvk y `key_version` (la que devolvió el servidor).
 *
 * `enc_key` y `auth_key` nuevas y la `auth_key` actual se borran en `finally`, también al cancelar. Nada de esto va a
 * logs.
 */
class ChangeMasterPasswordImpl @Inject constructor(
    private val api: AccountApi,
    private val accounts: AccountDao,
    private val session: VaultSession,
    private val reauth: ReauthRunner,
    private val auth: AuthRepository,
    private val biometric: BiometricUnlock,
    private val sessionLock: SessionLifecycleLock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ChangeMasterPassword {

    /** Lo que Room necesita guardar tras el 200. */
    private class NewKeys(val salt: ByteArray, val params: KdfParams, val pvk: Sealed, val keyVersion: Int)

    private class Derived(val encKey: SecureBytes, val authKey: SecureBytes) {
        fun close() {
            encKey.close()
            authKey.close()
        }
    }

    override suspend fun change(current: CharSequence, totpCode: String?, newPassword: CharSequence): ChangePasswordResult {
        val account = accounts.get() ?: return ChangePasswordResult.Generic
        val userId = UUID.fromString(account.userId)
        val normalizedCurrent = MasterPasswordNormalizer.normalize(current)
        val normalizedNew = MasterPasswordNormalizer.normalize(newPassword)
        val codePoints = MasterPasswordNormalizer.codePointLength(normalizedNew)
        if (!MasterPasswordPolicy.meetsLength(codePoints) || MasterPasswordPolicy.exceedsMaxLength(codePoints) ||
            !MasterPasswordPolicy.meetsCharacterClasses(normalizedNew)
        ) {
            return ChangePasswordResult.InvalidNewPassword
        }
        if (normalizedCurrent == normalizedNew) return ChangePasswordResult.SameAsCurrent
        val biometricWasEnabled = account.bioWrappedVaultKey != null

        var invalidNew = false
        var finished: ChangePasswordResult? = null
        val result = reauth.run(current, totpCode) { reauthKey ->
            var derived: Derived? = null
            try {
                val salt = RandomBytes.next(SALT_BYTES)
                val params = paramsFor(account)
                val keys = derive(normalizedNew, salt, params) { derived = it }
                if (keys == null) {
                    invalidNew = true
                    return@run ApiResult.Failure(ApiError.ValidationFailed)
                }
                val expected = account.keyVersion
                val firstPvk = wrap(keys.encKey, userId, expected + 1)
                    ?: return@run ApiResult.Failure(ApiError.ServerError)
                val change = MasterPasswordChange(
                    reauth = reauthKey,
                    expectedKeyVersion = expected,
                    newAuthKey = keys.authKey,
                    newKdf = ServerKdf(params.memoryKiB, params.iterations, params.parallelism, salt),
                    newProtectedVaultKey = firstPvk,
                )
                // Desde que se manda EP-17 hasta que se guarda en Room no se puede cancelar (ni por Atrás ni por un bloqueo): el
                // servidor y este teléfono no deben quedar con llaves distintas por una cancelación (A1 de la revisión).
                withContext(NonCancellable) {
                    when (val response = api.changeMasterPassword(change)) {
                        is ApiResult.Failure -> response
                        is ApiResult.Success -> {
                            val serverVersion = response.value
                            // Lo normal es `key_version + 1`; si el servidor devuelve una mayor, se vuelve a envolver con esa (sin
                            // Argon2). Una igual o menor permitiría retroceder: se trata como cambio que no se pudo guardar aquí.
                            val pvk = when {
                                serverVersion == expected + 1 -> firstPvk
                                serverVersion > expected -> wrap(keys.encKey, userId, serverVersion)
                                else -> null
                            }
                            finished = if (pvk == null) {
                                signOutAfterServerChange()
                            } else {
                                saveLocally(NewKeys(salt, params, pvk, serverVersion), userId, biometricWasEnabled)
                            }
                            ApiResult.Success(serverVersion)
                        }
                    }
                }
            } finally {
                derived?.close()
            }
        }
        return when (result) {
            is ApiResult.Failure -> if (invalidNew) ChangePasswordResult.InvalidNewPassword else map(result.error)
            is ApiResult.Success -> checkNotNull(finished)
        }
    }

    /**
     * Argon2 con al menos los parámetros por defecto y nunca menos que los de la cuenta: un servidor que había subido el costo
     * no debe bajarlo en silencio al cambiar la contraseña (B2 de la revisión).
     */
    private fun paramsFor(account: com.luis.end_security.core.database.entity.AccountEntity): KdfParams = runCatching {
        KdfParams(
            memoryKiB = maxOf(account.kdfMemoryKiB, KdfParams.DEFAULT.memoryKiB),
            iterations = maxOf(account.kdfIterations, KdfParams.DEFAULT.iterations),
            parallelism = account.kdfParallelism.coerceIn(1, KdfParams.MAX_PARALLELISM),
        )
    }.getOrDefault(KdfParams.DEFAULT)

    /**
     * `vault_key` de la sesión envuelta con [encKey]; `null` si la bóveda se bloqueó (ya no hay con qué envolver) o la versión
     * no es válida.
     */
    private fun wrap(encKey: SecureBytes, userId: UUID, keyVersion: Int): Sealed? = try {
        session.withVaultKey { vaultKey -> ProtectedVaultKey.wrap(encKey, vaultKey, userId, keyVersion) }
    } catch (_: VaultLockedException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Argon2 y HKDF fuera del hilo principal y sin cancelarse a la mitad. El resultado se entrega por [handOver] desde
     * dentro del bloque: si `withContext` lanza `CancellationException` al volver, quien llama lo borra en su `finally`
     * (M1 de la tarea 17).
     */
    private suspend fun derive(
        normalizedPassword: String,
        salt: ByteArray,
        params: KdfParams,
        handOver: (Derived) -> Unit,
    ): Derived? {
        var produced: Derived? = null
        try {
            withContext(defaultDispatcher + NonCancellable) {
                KeyDerivation.deriveMasterKey(normalizedPassword, salt, params).use { master ->
                    // Si la segunda llave fallara, la primera ya creada se borra (B1).
                    val enc = KeyDerivation.encKey(master)
                    produced = try {
                        Derived(enc, KeyDerivation.authKey(master))
                    } catch (e: Throwable) {
                        enc.close()
                        throw e
                    }.also(handOver)
                }
            }
            currentCoroutineContext().ensureActive()
            return checkNotNull(produced)
        } catch (_: CharacterCodingException) {
            // Texto que no se puede codificar (la política ya lo rechaza al escribirlo): no hay llaves que borrar.
            produced?.close()
            return null
        }
    }

    private fun map(error: ApiError): ChangePasswordResult = when (error) {
        ApiError.ReauthRequired, ApiError.InvalidCredentials, is ApiError.InvalidCode -> ChangePasswordResult.WrongCredentials
        // Sin cuenta local (o parámetros locales inválidos): no es culpa de la contraseña nueva.
        ApiError.ValidationFailed -> ChangePasswordResult.Generic
        ApiError.KeyVersionConflict -> ChangePasswordResult.KeyVersionConflict
        is ApiError.RateLimited -> ChangePasswordResult.RateLimited(error.retryAfterSeconds)
        else -> ChangePasswordResult.Generic
    }

    /**
     * Una sola sentencia de Room (atómica): sal, parámetros, pvk y `key_version`, solo de la cuenta de esta sesión y exigiendo
     * una fila afectada. Se toma el candado de la sesión (como `establish` y `signOutLocally`). Si falla, se reintenta una vez; si
     * vuelve a fallar, se cierra la sesión local (preferible a dejar parámetros viejos con una contraseña que ya no abre).
     *
     * Con la huella activa, el envoltorio viejo ya no abre (su AAD lleva la versión anterior): se desactiva todo aquí (llave del
     * Keystore, columnas y bandera) para que el estado guardado sea siempre coherente; luego se vuelve a activar (M2).
     */
    private suspend fun saveLocally(keys: NewKeys, userId: UUID, biometricWasEnabled: Boolean): ChangePasswordResult {
        repeat(2) {
            try {
                val rows = sessionLock.mutex.withLock {
                    accounts.updateKeys(
                        userId = userId.toString(),
                        salt = keys.salt,
                        memoryKiB = keys.params.memoryKiB,
                        iterations = keys.params.iterations,
                        parallelism = keys.params.parallelism,
                        pvkNonce = keys.pvk.nonce,
                        pvkCiphertext = keys.pvk.ciphertext,
                        keyVersion = keys.keyVersion,
                    )
                }
                if (rows == 1) {
                    if (biometricWasEnabled) runCatching { biometric.disable() }
                    return ChangePasswordResult.Success(biometricWasEnabled)
                }
            } catch (_: Exception) {
                // Se reintenta una vez.
            }
        }
        return signOutAfterServerChange()
    }

    /** El servidor ya cambió la contraseña pero aquí no se pudo dejar igual: sesión local cerrada para entrar con la nueva. */
    private suspend fun signOutAfterServerChange(): ChangePasswordResult {
        runCatching { auth.signOut() }
        return ChangePasswordResult.LocalSaveFailedSignedOut
    }

    private companion object {
        const val SALT_BYTES = 16
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChangeMasterPasswordModule {
    @Binds
    abstract fun bindChangeMasterPassword(impl: ChangeMasterPasswordImpl): ChangeMasterPassword
}
