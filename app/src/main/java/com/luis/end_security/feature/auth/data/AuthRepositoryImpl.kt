package com.luis.end_security.feature.auth.data

import com.luis.end_security.core.crypto.keystore.BiometricKeyStore
import com.luis.end_security.core.datastore.SettingsStore
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
import com.luis.end_security.core.security.SecretClipboardController
import com.luis.end_security.core.session.SessionLifecycleLock
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.sync.SyncShutdown
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
import kotlinx.coroutines.sync.withLock
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
    private val sync: SyncShutdown,
    private val biometricKeys: BiometricKeyStore,
    private val settings: SettingsStore,
    private val sessionLock: SessionLifecycleLock,
    private val serverCheck: ServerAccountCheck,
    private val clipboard: SecretClipboardController,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    private var recoveryCodesNotice: Int? = null

    /** Lo generado en el último registro, para guardarlo tal cual al verificar el correo. */
    private class LocalRegistration(val userId: UUID, val salt: ByteArray, val params: KdfParams, val pvk: Sealed)

    private var localRegistration: LocalRegistration? = null

    init {
        // `session_revoked` (refresh reusado, sesión cerrada desde otro teléfono): cierre local.
        scope.launch {
            tokens.sessionRevoked.collect {
                // Un fallo del borrado (disco, Room) no debe matar el colector: la sesión en memoria ya se bloqueó y las
                // siguientes revocaciones tienen que seguir cerrando la sesión local (M2).
                try {
                    signOutLocally()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sin registro: el error podría traer datos de la cuenta.
                }
            }
        }
    }

    /** `enc_key` y `auth_key` (DEC-05). Se cierran juntas. */
    private class DerivedKeys(val encKey: SecureBytes, val authKey: SecureBytes) : AutoCloseable {
        override fun close() {
            encKey.close()
            authKey.close()
        }
    }

    // --- Registro (OP-11, OP-12) ---

    override suspend fun register(email: String, password: CharSequence, marketingOptIn: Boolean): ApiResult<Unit> {
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
                        marketingOptIn = marketingOptIn,
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
            login != null -> completeWithEncKey(authenticated, login.encKey, normalizedEmail).also { pending.clear() }
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
        // Con la cuenta de este correo en el teléfono, parámetros más baratos se rechazan. Una sal distinta no se rechaza aquí: se
        // acepta solo como recuperación de un cambio de contraseña a medias y se decide al abrir la pvk (ServerAccountCheck).
        if (serverKdf.salt.size != SALT_BYTES || serverCheck.lowersCost(accountDao.get(), normalizedEmail, serverKdf)) {
            return ApiResult.Failure(ApiError.ServerError)
        }
        // Otra sal que la local para esta cuenta: solo puede ser legítima si la persona cambió su contraseña y el teléfono no llegó a
        // guardarlo. Si la contraseña tecleada abre la pvk LOCAL, es la vigente en este teléfono y esa sal no es legítima: se
        // rechaza sin mandar nada derivado con una sal que eligió el servidor (revisión de la tarea 19, M2).
        val local = accountDao.get()
        if (serverCheck.saltDiffers(local, normalizedEmail, serverKdf) && opensLocalPvk(checkNotNull(local), normalized)) {
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
                    is LoginResult.Success -> completeWithEncKey(login.authenticated, keys.encKey, normalizedEmail)
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
            is ApiResult.Success -> completeWithEncKey(result.value, login.encKey, login.email).also { pending.clear() }
            is ApiResult.Failure -> {
                if (result.error == ApiError.ChallengeExpired) pending.clear()
                result
            }
        }
    }

    /** Salir del segundo paso o de Verificar correo: borra lo que esperaba en memoria. */
    override fun cancelPendingLogin() = pending.clear()

    /** Abre la pvk con la `enc_key` y deja la sesión lista. Si no abre, el error genérico. */
    private suspend fun completeWithEncKey(authenticated: Authenticated, encKey: SecureBytes, typedEmail: String): ApiResult<AuthStep> {
        val account = authenticated.account
        val vaultKey = try {
            ProtectedVaultKey.unwrap(encKey, account.protectedVaultKey, account.userId, account.keyVersion)
        } catch (_: AuthenticationFailed) {
            api.logoutWith(authenticated)
            return ApiResult.Failure(ApiError.ServerError)
        }
        // Desde aquí la `vault_key` existe: se borra en todos los caminos salvo cuando pasa a la sesión (`establish`).
        var handedOver = false
        try {
            // Con otra sal que la local solo se acepta la recuperación completa (a, b y c); si no, igual que una pvk que no abre.
            val local = accountDao.get()
            if (!serverCheck.accept(local, typedEmail, account, vaultKey)) {
                api.logoutWith(authenticated)
                return ApiResult.Failure(ApiError.ServerError)
            }
            val recoveryFrom = local?.takeIf { serverCheck.isRecovery(it, typedEmail, account) }
            val entity = account.toEntity().let { fresh ->
                // En una recuperación los elementos se conservan: el cursor de sincronización sigue valiendo.
                if (recoveryFrom != null) fresh.withCursorOf(recoveryFrom) else fresh
            }
            handedOver = establish(authenticated, vaultKey, entity, recoveryFrom)
            if (!handedOver) {
                // La cuenta local cambió mientras se decidía (cierre de sesión, otra pasada): no se acepta lo decidido.
                api.logoutWith(authenticated)
                return ApiResult.Failure(ApiError.ServerError)
            }
            return ApiResult.Success(AuthStep.VaultUnlocked)
        } finally {
            if (!handedOver) vaultKey.close()
        }
    }

    /** ¿La contraseña tecleada abre la pvk guardada en este teléfono? (Un Argon2 extra, solo con una sal distinta del servidor.) */
    private suspend fun opensLocalPvk(local: AccountEntity, normalizedPassword: String): Boolean {
        val params = try {
            KdfParams(local.kdfMemoryKiB, local.kdfIterations, local.kdfParallelism)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val keys = try {
            derive(normalizedPassword, local.kdfSalt, params)
        } catch (_: CharacterCodingException) {
            return false
        }
        return keys.use {
            try {
                ProtectedVaultKey.unwrap(it.encKey, Sealed(local.pvkNonce, local.pvkCiphertext), UUID.fromString(local.userId), local.keyVersion)
                    .close()
                true
            } catch (_: AuthenticationFailed) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

    /**
     * Guarda los tokens y la fila `account`, y abre la bóveda con [vaultKey] (que pasa a la sesión).
     * Sin cancelarse a la mitad; si algo falla antes de abrir, la llave se borra.
     */
    private suspend fun establish(
        authenticated: Authenticated,
        vaultKey: SecureBytes,
        account: AccountEntity,
        recoveryFrom: AccountEntity? = null,
    ): Boolean {
        var opened = false
        try {
            withContext(NonCancellable) {
              sessionLock.mutex.withLock {
                // Una recuperación se decidió sobre la fila local de antes: si cambió (otro cierre de sesión o una pasada de
                // sincronización) mientras tanto, no se acepta lo decidido (B2 de la revisión).
                if (recoveryFrom != null) {
                    val current = accountDao.get()
                    if (current == null || current.userId != recoveryFrom.userId || current.keyVersion != recoveryFrom.keyVersion ||
                        !current.kdfSalt.contentEquals(recoveryFrom.kdfSalt)
                    ) {
                        return@withLock
                    }
                }
                tokens.save(authenticated.tokens)
                try {
                    accountDao.replace(account)
                } catch (e: Exception) {
                    // Sin cuenta local no debe quedar una sesión a medias: los tokens se borran antes de propagar el error.
                    runCatching { tokens.clear() }
                    throw e
                }
                // La fila nueva no tiene huella: ni llave del Keystore ni opción encendida de una cuenta anterior.
                runCatching { biometricKeys.deleteAll() }
                runCatching { settings.setBiometricEnabled(false) }
                attempts.recordSuccess()
                if (session.state.value is VaultState.Unlocked) session.lock()
                session.unlock(authenticated.account.userId, vaultKey)
                opened = true
              }
            }
        } finally {
            if (!opened) vaultKey.close()
        }
        return opened
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
        val opened = withContext(NonCancellable) {
            sessionLock.mutex.withLock {
                // Si mientras se derivaba se cerró la sesión (cuenta borrada de Room), no se abre nada.
                if (accountDao.get()?.userId != account.userId) {
                    vaultKey.close()
                    false
                } else {
                    attempts.recordSuccess()
                    session.unlock(userId, vaultKey)
                    true
                }
            }
        }
        return if (opened) UnlockOutcome.Unlocked else UnlockOutcome.NoAccount
    }

    override suspend fun signOut() {
        // En el servidor aunque falle la red: lo local se borra igual.
        api.logout()
        signOutLocally()
    }

    /**
     * Borra tokens, Room y llaves en memoria. También tras `session_revoked`. Devuelve `true` si **todos** los pasos salieron
     * bien; un fallo de la limpieza de Room sigue propagándose, como siempre (los pasos previos solo se anotan).
     */
    suspend fun signOutLocally(): Boolean = withContext(NonCancellable) {
      sessionLock.mutex.withLock {
        var clean = true
        try {
            // La llave de la huella se borra lo primero (DEC-09): nada que abra la bóveda de esta cuenta queda.
            clean = runCatching { biometricKeys.deleteAll() }.isSuccess && clean
            clean = runCatching { settings.setBiometricEnabled(false) }.isSuccess && clean
            clean = runCatching { pending.clear() }.isSuccess && clean
            clean = runCatching { tokens.clear() }.isSuccess && clean
            // Sin sincronización a medias: el borrado corre con el motor en reposo y ningún disparador arranca otra pasada.
            sync.stop { withContext(Dispatchers.IO) { database.clearAllTables() } }
            // Sin `recordSuccess`: ese se traga el fallo de escritura y aquí hace falta saber si el bloqueo quedó borrado.
            clean = runCatching { attempts.reset() }.isSuccess && clean
        } finally {
            // Pase lo que pase en los pasos anteriores, la vault_key en memoria se borra (INV-07).
            session.lock()
        }
        clean
      }
    }

    // --- Eliminar cuenta (OP-28, tarea 20) ---

    override suspend fun wipeAfterAccountDeletion(): Boolean = withContext(NonCancellable) {
        // La marca va primero y con el `user_id` de la cuenta que se elimina: si el proceso muere a mitad de la limpieza, al
        // arrancar se reintenta, pero solo si la cuenta local sigue siendo esa (no se borra una cuenta nueva).
        val userId = runCatching { accountDao.get()?.userId }.getOrNull().orEmpty()
        // Dos intentos: sin marca, un fallo de la limpieza no se reintentaría nunca.
        repeat(2) { attempt ->
            if (runCatching { settings.setWipePending(userId) }.isSuccess) return@repeat
            if (attempt == 0) kotlinx.coroutines.yield()
        }
        runWipe()
    }

    override suspend fun completePendingWipe(): Boolean {
        val mark = try {
            settings.wipePendingUser()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return true
        // Una marca de otra cuenta (la eliminada ya se limpió en otro intento y desde entonces entró una cuenta nueva): se quita
        // sin tocar nada. Borraría los datos de la cuenta nueva.
        val local = runCatching { accountDao.get() }.getOrNull()
        if (mark.isNotEmpty() && local != null && local.userId != mark) {
            runCatching { settings.clearWipePending() }
            return true
        }
        return withContext(NonCancellable) { runWipe() }
    }

    /** Todos los pasos se intentan aunque uno falle; solo con todos bien se quita la marca de limpieza pendiente. */
    private suspend fun runWipe(): Boolean {
        val local = try {
            signOutLocally()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        val clipboardCleared = runCatching { clipboard.clearIfOurs() }.isSuccess
        val preferences = runCatching { settings.resetAccountPreferences() }.isSuccess
        val complete = local && clipboardCleared && preferences
        if (complete) runCatching { settings.clearWipePending() }
        return complete
    }

    override suspend fun localAccountName(): String? = accountDao.get()?.let { it.displayName ?: it.email }

    override fun consumeRecoveryCodesNotice(): Int? = recoveryCodesNotice.also { recoveryCodesNotice = null }

    // --- Ayudantes ---

    /**
     * Argon2 fuera del hilo principal. No se cancela a la mitad (el resultado se perdería sin
     * borrarse); si quien llama se canceló mientras tanto, las llaves se borran aquí.
     */
    private suspend fun derive(normalizedPassword: String, salt: ByteArray, params: KdfParams): DerivedKeys {
        // El resultado se deja en [produced] desde dentro del bloque: si `withContext` lanza `CancellationException` al volver
        // (el que llama se canceló mientras corría Argon2), el valor ya no llega a nadie y aquí se borra (INV-07, M1).
        var produced: DerivedKeys? = null
        var handedOver = false
        try {
            withContext(defaultDispatcher + NonCancellable) {
                KeyDerivation.deriveMasterKey(normalizedPassword, salt, params).use { master ->
                    val enc = KeyDerivation.encKey(master)
                    produced = try {
                        DerivedKeys(enc, KeyDerivation.authKey(master))
                    } catch (e: Throwable) {
                        enc.close()
                        throw e
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            handedOver = true
            return checkNotNull(produced)
        } finally {
            if (!handedOver) produced?.close()
        }
    }

    private fun AccountEntity.withCursorOf(local: AccountEntity) = AccountEntity(
        userId = userId,
        email = email,
        displayName = displayName,
        kdfSalt = kdfSalt,
        kdfMemoryKiB = kdfMemoryKiB,
        kdfIterations = kdfIterations,
        kdfParallelism = kdfParallelism,
        pvkNonce = pvkNonce,
        pvkCiphertext = pvkCiphertext,
        keyVersion = keyVersion,
        syncCursor = local.syncCursor,
        lastSyncAt = local.lastSyncAt,
        bioIv = bioIv,
        bioWrappedVaultKey = bioWrappedVaultKey,
    )

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

        fun normalizeEmail(email: String) = com.luis.end_security.core.common.EmailNormalizer.normalize(email)

        /** «Xiaomi 23090RA98G», recortado a los 100 caracteres del contrato. */
        fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }.take(100)
    }
}
