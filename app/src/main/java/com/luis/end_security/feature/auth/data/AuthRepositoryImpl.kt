package com.luis.end_security.feature.auth.data

import android.os.Build
import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.ApplicationScope
import com.luis.end_security.core.common.DefaultDispatcher
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.VaultKey
import com.luis.end_security.core.database.AppDatabase
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.datastore.UnlockAttemptStore
import com.luis.end_security.core.network.TokenManager
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.Authenticated
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.AuthApi
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.AuthStep
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.auth.domain.UnlockOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.CharacterCodingException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * [AuthRepository]: la jerarquía de llaves de DEC-05 aplicada al registro, al acceso y al
 * desbloqueo. Argon2 corre en [defaultDispatcher]; las llaves viven en `SecureBytes` y se borran
 * con `use`/`close` en cuanto dejan de usarse.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokens: TokenManager,
    private val accountDao: AccountDao,
    private val database: AppDatabase,
    private val session: VaultSession,
    private val attempts: UnlockAttemptStore,
    private val pending: PendingAuth,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    private var recoveryCodesNotice: Int? = null

    /** Lo generado en el último registro, para guardarlo tal cual al verificar el correo. */
    private class LocalRegistration(val userId: UUID, val salt: ByteArray, val params: KdfParams, val pvk: Sealed)

    private var localRegistration: LocalRegistration? = null

    init {
        // `session_revoked` (refresh reusado, sesión cerrada desde otro teléfono): cierre local.
        scope.launch { tokens.sessionRevoked.collect { signOutLocally() } }
    }

    /** `enc_key` y `auth_key` (DEC-05). Se cierran juntas. */
    private class DerivedKeys(val encKey: SecureBytes, val authKey: SecureBytes) : AutoCloseable {
        override fun close() {
            encKey.close()
            authKey.close()
        }
    }

    // --- Registro (OP-11, OP-12) ---

    override suspend fun register(email: String, password: CharSequence): ApiResult<Unit> {
        val normalizedEmail = normalizeEmail(email)
        // Se normaliza en el hilo que llama: el TextFieldState de la pantalla puede cambiar.
        val normalized = MasterPasswordNormalizer.normalize(password)
        val userId = UUID.randomUUID()
        val salt = RandomBytes.next(SALT_BYTES)
        val params = KdfParams.DEFAULT
        val keys = try {
            derive(normalized, salt, params)
        } catch (_: CharacterCodingException) {
            return ApiResult.Failure(ApiError.ValidationFailed)
        }
        val vaultKey = VaultKey.generate()
        try {
            val result = keys.use {
                val pvk = ProtectedVaultKey.wrap(it.encKey, vaultKey, userId, FIRST_KEY_VERSION)
                localRegistration = LocalRegistration(userId, salt, params, pvk)
                api.register(
                    RegisterRequest(
                        userId = userId,
                        email = normalizedEmail,
                        displayName = null,
                        authKey = it.authKey,
                        kdf = ServerKdf(params.memoryKiB, params.iterations, params.parallelism, salt),
                        protectedVaultKey = pvk,
                    ),
                )
            }
            if (result is ApiResult.Success) pending.setRegistration(normalizedEmail, userId, vaultKey)
            return result
        } finally {
            if (pending.registrationFor(normalizedEmail)?.vaultKey !== vaultKey) vaultKey.close()
        }
    }

    override suspend fun verifyEmail(email: String, code: String): ApiResult<AuthStep> {
        val normalizedEmail = normalizeEmail(email)
        val authenticated = when (val result = api.verifyEmail(normalizedEmail, code, deviceName())) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result
        }
        val registration = pending.registrationFor(normalizedEmail)
        val login = pending.loginFor(normalizedEmail)
        return when {
            registration != null && registration.userId == authenticated.account.userId -> {
                // La vault_key pasa a la sesión: se suelta sin borrarla.
                pending.handOverRegistration()
                // Hallazgo 9 de la revisión: se guardan la sal, los parámetros y la pvk generados
                // aquí, no los que devuelve el servidor.
                val local = localRegistration?.takeIf { it.userId == registration.userId }
                localRegistration = null
                val entity = authenticated.account.toEntity().let { if (local != null) it.withLocal(local) else it }
                establish(authenticated, registration.vaultKey, entity)
                ApiResult.Success(AuthStep.VaultUnlocked)
            }
            login != null -> completeWithEncKey(authenticated, login.encKey).also { pending.clear() }
            else -> {
                // Sin la llave en memoria no se puede abrir la bóveda: se cierra esta sesión y se entra de nuevo.
                api.logoutWith(authenticated)
                pending.clear()
                ApiResult.Success(AuthStep.SignInAgain)
            }
        }
    }

    override suspend fun resendVerification(email: String): ApiResult<Unit> = api.resendVerification(normalizeEmail(email))

    // --- Acceso (OP-13…OP-16) ---

    override suspend fun login(email: String, password: CharSequence): ApiResult<AuthStep> {
        val normalizedEmail = normalizeEmail(email)
        val normalized = MasterPasswordNormalizer.normalize(password)
        pending.clear()
        val serverKdf = when (val result = api.prelogin(normalizedEmail)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result
        }
        // Piso del cliente (DEC-05): por debajo, el error genérico, sin mandar nada derivado.
        val params = serverKdf.toParams() ?: return ApiResult.Failure(ApiError.ServerError)
        if (serverKdf.salt.size != SALT_BYTES || downgradesKnownAccount(normalizedEmail, serverKdf)) {
            return ApiResult.Failure(ApiError.ServerError)
        }
        val keys = try {
            derive(normalized, serverKdf.salt, params)
        } catch (_: CharacterCodingException) {
            // DEC-31: si no se puede codificar, al entrar es el fallo genérico de credenciales.
            return ApiResult.Failure(ApiError.InvalidCredentials)
        }
        // La enc_key se borra en todos los caminos (también si se cancela durante la red), salvo
        // cuando pasa a PendingAuth para el segundo paso o la verificación del correo.
        var handedOver = false
        try {
            val result = keys.authKey.use { authKey -> api.login(normalizedEmail, authKey, deviceName()) }
            return when (result) {
                is ApiResult.Success -> when (val login = result.value) {
                    is LoginResult.Success -> completeWithEncKey(login.authenticated, keys.encKey)
                    is LoginResult.TwoFactorRequired -> {
                        pending.setLogin(normalizedEmail, keys.encKey, login.challengeToken)
                        handedOver = true
                        ApiResult.Success(AuthStep.TwoFactorRequired)
                    }
                }
                is ApiResult.Failure -> if (result.error == ApiError.EmailNotVerified) {
                    pending.setLogin(normalizedEmail, keys.encKey, challengeToken = null)
                    handedOver = true
                    ApiResult.Success(AuthStep.EmailNotVerified(normalizedEmail))
                } else {
                    result
                }
            }
        } finally {
            if (!handedOver) keys.close()
        }
    }

    override suspend fun verifyTwoFactor(code: String): ApiResult<AuthStep> =
        secondStep { challenge -> api.verifyTwoFactor(challenge, code) }

    override suspend fun verifyRecoveryCode(code: String): ApiResult<AuthStep> = secondStep { challenge ->
        when (val result = api.verifyRecoveryCode(challenge, code)) {
            is ApiResult.Success -> {
                recoveryCodesNotice = result.value.recoveryCodesRemaining.takeIf { it <= RECOVERY_NOTICE_THRESHOLD }
                ApiResult.Success(result.value.authenticated)
            }
            is ApiResult.Failure -> result
        }
    }

    private suspend fun secondStep(call: suspend (String) -> ApiResult<Authenticated>): ApiResult<AuthStep> {
        val login = pending.loginFor() ?: return ApiResult.Failure(ApiError.ChallengeExpired)
        val challenge = login.challengeToken ?: return ApiResult.Failure(ApiError.ChallengeExpired)
        return when (val result = call(challenge)) {
            is ApiResult.Success -> completeWithEncKey(result.value, login.encKey).also { pending.clear() }
            is ApiResult.Failure -> {
                if (result.error == ApiError.ChallengeExpired) pending.clear()
                result
            }
        }
    }

    /** Salir del segundo paso o de Verificar correo: borra lo que esperaba en memoria. */
    override fun cancelPendingLogin() = pending.clear()

    /** Abre la pvk con la `enc_key` y deja la sesión lista. Si no abre, el error genérico. */
    private suspend fun completeWithEncKey(authenticated: Authenticated, encKey: SecureBytes): ApiResult<AuthStep> {
        val account = authenticated.account
        val vaultKey = try {
            ProtectedVaultKey.unwrap(encKey, account.protectedVaultKey, account.userId, account.keyVersion)
        } catch (_: AuthenticationFailed) {
            api.logoutWith(authenticated)
            return ApiResult.Failure(ApiError.ServerError)
        }
        establish(authenticated, vaultKey, account.toEntity())
        return ApiResult.Success(AuthStep.VaultUnlocked)
    }

    /**
     * Guarda los tokens y la fila `account`, y abre la bóveda con [vaultKey] (que pasa a la sesión).
     * Sin cancelarse a la mitad; si algo falla antes de abrir, la llave se borra.
     */
    private suspend fun establish(authenticated: Authenticated, vaultKey: SecureBytes, account: AccountEntity) {
        var opened = false
        try {
            withContext(NonCancellable) {
                tokens.save(authenticated.tokens)
                accountDao.replace(account)
                attempts.recordSuccess()
                if (session.state.value is VaultState.Unlocked) session.lock()
                session.unlock(authenticated.account.userId, vaultKey)
                opened = true
            }
        } finally {
            if (!opened) vaultKey.close()
        }
    }

    // --- Desbloquear (OP-19) y cerrar sesión (OP-18) ---

    override suspend fun unlock(password: CharSequence): UnlockOutcome {
        val normalized = MasterPasswordNormalizer.normalize(password)
        val account = accountDao.get() ?: return UnlockOutcome.NoAccount
        val remaining = attempts.remainingLockout()
        if (remaining > Duration.ZERO) return UnlockOutcome.LockedOut(remaining)
        // Se cuenta antes de derivar (hallazgo 4 de la tarea 07): cerrar la app durante Argon2
        // no evita que el intento cuente. Un acierto lo deshace.
        val lockoutIfWrong = withContext(NonCancellable) { attempts.recordFailure() }
        val params = try {
            KdfParams(account.kdfMemoryKiB, account.kdfIterations, account.kdfParallelism)
        } catch (_: IllegalArgumentException) {
            return UnlockOutcome.WrongPassword(lockoutIfWrong)
        }
        val keys = try {
            derive(normalized, account.kdfSalt, params)
        } catch (_: CharacterCodingException) {
            return UnlockOutcome.WrongPassword(lockoutIfWrong)
        }
        val userId = UUID.fromString(account.userId)
        val vaultKey = keys.use {
            try {
                ProtectedVaultKey.unwrap(
                    it.encKey,
                    Sealed(account.pvkNonce, account.pvkCiphertext),
                    userId,
                    account.keyVersion,
                )
            } catch (_: AuthenticationFailed) {
                null
            }
        } ?: return UnlockOutcome.WrongPassword(lockoutIfWrong)
        // Juntos y sin cancelarse: si se cancela al salir de withContext, la llave ya está en la sesión.
        withContext(NonCancellable) {
            attempts.recordSuccess()
            session.unlock(userId, vaultKey)
        }
        return UnlockOutcome.Unlocked
    }

    override suspend fun signOut() {
        // En el servidor aunque falle la red: lo local se borra igual.
        api.logout()
        signOutLocally()
    }

    /** Borra tokens, Room y llaves en memoria. También tras `session_revoked`. */
    suspend fun signOutLocally() = withContext(NonCancellable) {
        pending.clear()
        tokens.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        attempts.recordSuccess()
        session.lock()
    }

    override suspend fun localAccountName(): String? = accountDao.get()?.let { it.displayName ?: it.email }

    override fun consumeRecoveryCodesNotice(): Int? = recoveryCodesNotice.also { recoveryCodesNotice = null }

    // --- Ayudantes ---

    /**
     * Argon2 fuera del hilo principal. No se cancela a la mitad (el resultado se perdería sin
     * borrarse); si quien llama se canceló mientras tanto, las llaves se borran aquí.
     */
    private suspend fun derive(normalizedPassword: String, salt: ByteArray, params: KdfParams): DerivedKeys {
        val keys = withContext(defaultDispatcher + NonCancellable) {
            KeyDerivation.deriveMasterKey(normalizedPassword, salt, params).use { master ->
                DerivedKeys(KeyDerivation.encKey(master), KeyDerivation.authKey(master))
            }
        }
        try {
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            keys.close()
            throw e
        }
        return keys
    }

    /** Hallazgo 1 de la tarea 07: con una cuenta de este correo en el teléfono, otra sal o parámetros más bajos se rechazan. */
    private suspend fun downgradesKnownAccount(email: String, kdf: ServerKdf): Boolean {
        val known = accountDao.get()?.takeIf { it.email == email } ?: return false
        return !known.kdfSalt.contentEquals(kdf.salt) ||
            kdf.memoryKiB < known.kdfMemoryKiB ||
            kdf.iterations < known.kdfIterations
    }

    /** Si la pvk no abrió o falta la llave, la sesión recién creada se cierra en el servidor. */
    private suspend fun AuthApi.logoutWith(authenticated: Authenticated) {
        tokens.save(authenticated.tokens)
        logout()
        tokens.clear()
    }

    private fun ServerKdf.toParams(): KdfParams? = try {
        KdfParams(memoryKiB, iterations, parallelism)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun AccountEntity.withLocal(local: LocalRegistration) = AccountEntity(
        userId = userId,
        email = email,
        displayName = displayName,
        kdfSalt = local.salt,
        kdfMemoryKiB = local.params.memoryKiB,
        kdfIterations = local.params.iterations,
        kdfParallelism = local.params.parallelism,
        pvkNonce = local.pvk.nonce,
        pvkCiphertext = local.pvk.ciphertext,
        keyVersion = keyVersion,
        syncCursor = syncCursor,
        lastSyncAt = lastSyncAt,
        bioIv = bioIv,
        bioWrappedVaultKey = bioWrappedVaultKey,
    )

    private fun AccountInfo.toEntity() = AccountEntity(
        userId = userId.toString(),
        email = email,
        displayName = displayName,
        kdfSalt = kdf.salt,
        kdfMemoryKiB = kdf.memoryKiB,
        kdfIterations = kdf.iterations,
        kdfParallelism = kdf.parallelism,
        pvkNonce = protectedVaultKey.nonce,
        pvkCiphertext = protectedVaultKey.ciphertext,
        keyVersion = keyVersion,
        syncCursor = 0,
        lastSyncAt = null,
        bioIv = null,
        bioWrappedVaultKey = null,
    )

    private companion object {
        const val SALT_BYTES = 16
        const val FIRST_KEY_VERSION = 1
        const val RECOVERY_NOTICE_THRESHOLD = 3

        fun normalizeEmail(email: String) = email.trim().lowercase()

        /** «Xiaomi 23090RA98G», recortado a los 100 caracteres del contrato. */
        fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }.take(100)
    }
}
