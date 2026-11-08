package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.Base32
import com.luis.end_security.core.crypto.FixedClock
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.ProtectedVaultKey
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.crypto.Totp
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiTransport
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.RefreshOutcome
import com.luis.end_security.core.network.TokenProvider
import com.luis.end_security.core.network.api.AccountApiImpl
import com.luis.end_security.core.network.api.AuthApiImpl
import com.luis.end_security.core.network.fake.FakeServer
import com.luis.end_security.core.network.fake.FakeState
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.feature.account.domain.ChangePasswordResult
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.auth.domain.FakeAuthRepository
import com.luis.end_security.feature.auth.domain.LoginResult
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.auth.domain.RegisterRequest
import com.luis.end_security.feature.vault.data.FakeAccountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * OP-27 de punta a punta contra el backend falso (que cumple EP-17) con **Argon2 real** y los parámetros por defecto:
 * la misma `vault_key` se vuelve a envolver con la contraseña nueva y los elementos no se tocan.
 */
class ChangeMasterPasswordImplTest {

    private val oldPassword = "Tortuga-violeta-salta-29-nubes"
    private val newPassword = "Nube-gris-camina-41-rapido"
    private val email = "ana@correo.com"
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val vaultKeyBytes = ByteArray(32) { (it * 3 + 1).toByte() }

    private var clock = 1_800_000_000_000L
    private var offline = false
    private val codes = mutableMapOf<String, String>()
    private val state = FakeState(Base64Url.encode(ByteArray(32) { 1 }), Base64Url.encode(ByteArray(32) { 2 }))
    private val server = FakeServer(state, { clock }) { e, c -> codes[e] = c }
    private val transport = object : ApiTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            if (offline) throw IOException("sin red")
            return server.handle(request)
        }
    }

    private inner class MemoryTokens : TokenProvider {
        var access: String? = null
        var refresh: String? = null
        override suspend fun accessToken() = access
        override suspend fun refreshAfterExpiry(expiredAccessToken: String): RefreshOutcome = RefreshOutcome.Revoked
        override suspend fun onSessionRevoked() {
            access = null
        }
    }

    private val tokens = MemoryTokens()
    private val client = ApiClient(transport, tokens)
    private val auth = AuthApiImpl(client)
    private val account = AccountApiImpl(client)

    private lateinit var accounts: FakeAccountDao
    private val session = VaultSession(FixedClock)
    private val fakeAuth = FakeAuthRepository()
    private val biometric = com.luis.end_security.feature.unlock.FakeBiometricUnlock()
    private lateinit var useCase: ChangeMasterPasswordImpl

    private fun keysOf(password: String, salt: ByteArray): Pair<SecureBytes, SecureBytes> =
        KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(password), salt, KdfParams.DEFAULT).use {
            KeyDerivation.encKey(it) to KeyDerivation.authKey(it)
        }

    /** Registra la cuenta en el servidor falso y en Room como lo deja un acceso real con [oldPassword]. */
    private fun setUp(bioWrapped: Boolean = false, localKeyVersion: Int = 1) = runBlocking {
        val (enc, authKey) = keysOf(oldPassword, salt)
        val vaultKey = SecureBytes.copyOf(vaultKeyBytes)
        val pvk = ProtectedVaultKey.wrap(enc, vaultKey, userId, 1)
        auth.register(RegisterRequest(userId, email, null, authKey, ServerKdf(65_536, 3, 1, salt), pvk))
        val verified = (auth.verifyEmail(email, codes.getValue(email), "Pixel 7 Pro") as ApiResult.Success).value
        tokens.access = verified.tokens.accessToken
        tokens.refresh = verified.tokens.refreshToken
        accounts = FakeAccountDao(
            AccountEntity(
                userId.toString(), email, null, salt, 65_536, 3, 1, pvk.nonce, pvk.ciphertext, localKeyVersion, 0, null,
                if (bioWrapped) ByteArray(12) { 1 } else null,
                if (bioWrapped) ByteArray(48) { 2 } else null,
            ),
        )
        session.unlock(userId, vaultKey)
        enc.close()
        authKey.close()
        useCase = ChangeMasterPasswordImpl(
            api = account,
            accounts = accounts,
            session = session,
            reauth = ReauthRunnerImpl(accounts, Dispatchers.Default),
            auth = fakeAuth,
            biometric = biometric,
            sessionLock = com.luis.end_security.core.session.SessionLifecycleLock(),
            defaultDispatcher = Dispatchers.Default,
        )
    }

    private fun change(current: String = oldPassword, totp: String? = null, new: String = newPassword) =
        runBlocking { useCase.change(current, totp, new) }

    private fun localVaultKeyWith(password: String, keyVersion: Int): ByteArray {
        val row = accounts.current!!
        val (enc, authKey) = keysOf(password, row.kdfSalt)
        authKey.close()
        val vaultKey = ProtectedVaultKey.unwrap(enc, Sealed(row.pvkNonce, row.pvkCiphertext), userId, keyVersion)
        enc.close()
        var copy = ByteArray(0)
        vaultKey.use { it.withBytes { bytes -> copy = bytes.copyOf() } }
        return copy
    }

    private fun serverLogin(password: String, salt: ByteArray): Boolean = runBlocking {
        val (enc, authKey) = keysOf(password, salt)
        enc.close()
        val result = auth.login(email, authKey, "Otro teléfono")
        authKey.close()
        result is ApiResult.Success && result.value is LoginResult.Success
    }

    @Test
    fun `tras cambiar la contrasena nueva desbloquea y la vieja no, contra la account local`() {
        setUp()
        val oldSalt = accounts.current!!.kdfSalt.copyOf()
        assertEquals(ChangePasswordResult.Success(biometricWasEnabled = false), change())
        val row = accounts.current!!
        assertEquals(2, row.keyVersion)
        assertFalse("sal nueva", oldSalt.contentEquals(row.kdfSalt))
        assertEquals(16, row.kdfSalt.size)
        assertEquals(KdfParams.DEFAULT.memoryKiB, row.kdfMemoryKiB)
        assertEquals(KdfParams.DEFAULT.iterations, row.kdfIterations)
        // La misma vault_key, ahora envuelta con la contraseña nueva.
        assertArrayEquals(vaultKeyBytes, localVaultKeyWith(newPassword, 2))
        try {
            localVaultKeyWith(oldPassword, 2)
            fail("la contraseña vieja no debe abrir")
        } catch (_: AuthenticationFailed) {
        }
    }

    @Test
    fun `la AAD de la pvk usa la nueva key_version`() {
        setUp()
        change()
        try {
            localVaultKeyWith(newPassword, 1)
            fail("la versión anterior no debe abrir")
        } catch (_: AuthenticationFailed) {
        }
        assertArrayEquals(vaultKeyBytes, localVaultKeyWith(newPassword, 2))
    }

    @Test
    fun `los elementos no se vuelven a cifrar y la sesion sigue abierta con la misma vault_key`() {
        setUp()
        change()
        var inSession = ByteArray(0)
        session.withVaultKey { it.withBytes { bytes -> inSession = bytes.copyOf() } }
        assertArrayEquals(vaultKeyBytes, inSession)
    }

    @Test
    fun `el servidor sube key_version y acepta la auth_key nueva, no la vieja`() {
        setUp()
        change()
        val newSalt = accounts.current!!.kdfSalt
        assertTrue(serverLogin(newPassword, newSalt))
        assertFalse(serverLogin(oldPassword, salt))
        assertEquals(2, (runBlocking { account.getAccount() } as ApiResult.Success).value.keyVersion)
    }

    @Test
    fun `las otras sesiones quedan revocadas`() = runBlocking {
        setUp()
        val (enc, authKey) = keysOf(oldPassword, salt)
        enc.close()
        val other = ((auth.login(email, authKey, "Otro") as ApiResult.Success).value as LoginResult.Success).authenticated
        change()
        assertTrue(auth.refresh(other.tokens.refreshToken) is ApiResult.Failure)
    }

    @Test
    fun `contrasena actual incorrecta no cambia nada local ni en el servidor`() {
        setUp()
        val before = accounts.current!!
        assertEquals(ChangePasswordResult.WrongCredentials, change(current = "otra-contrasena-Equivocada-1"))
        assertEquals(1, accounts.current!!.keyVersion)
        assertArrayEquals(before.pvkCiphertext, accounts.current!!.pvkCiphertext)
        assertArrayEquals(before.kdfSalt, accounts.current!!.kdfSalt)
        assertTrue(serverLogin(oldPassword, salt))
    }

    @Test
    fun `key_version distinto responde 409 y no toca nada local`() {
        // El teléfono cree estar en la 5; el servidor está en la 1.
        setUp(localKeyVersion = 5)
        val before = accounts.current!!
        assertEquals(ChangePasswordResult.KeyVersionConflict, change())
        assertEquals(5, accounts.current!!.keyVersion)
        assertArrayEquals(before.pvkCiphertext, accounts.current!!.pvkCiphertext)
        assertTrue(serverLogin(oldPassword, salt))
    }

    /** Deja la fila local con otra sal (como si otro dispositivo hubiera cambiado la contraseña) y la `key_version` indicada. */
    private fun makeLocalRowStale(keyVersion: Int) = runBlocking {
        val old = accounts.current!!
        accounts.replace(
            AccountEntity(
                old.userId, old.email, null, ByteArray(16) { 9 }, old.kdfMemoryKiB, old.kdfIterations, old.kdfParallelism,
                old.pvkNonce, old.pvkCiphertext, keyVersion, 0, null, null, null,
            ),
        )
    }

    @Test
    fun `reautenticacion rechazada con key_version del servidor mayor muestra el conflicto y no credenciales incorrectas`() {
        // El servidor está en la 1 y la fila local, desactualizada, en la 0 y con otra sal: la auth_key no coincide.
        setUp()
        makeLocalRowStale(keyVersion = 0)
        assertEquals(ChangePasswordResult.KeyVersionConflict, change())
        assertEquals(0, accounts.current!!.keyVersion)
        assertTrue(serverLogin(oldPassword, salt))
    }

    @Test
    fun `reautenticacion rechazada con la misma key_version sigue siendo credenciales incorrectas`() {
        setUp()
        makeLocalRowStale(keyVersion = 1)
        assertEquals(ChangePasswordResult.WrongCredentials, change())
    }

    @Test
    fun `reautenticacion rechazada con key_version local mayor tampoco es conflicto`() {
        setUp()
        makeLocalRowStale(keyVersion = 7)
        assertEquals(ChangePasswordResult.WrongCredentials, change())
    }

    @Test
    fun `sin red no cambia nada`() {
        setUp()
        val before = accounts.current!!
        offline = true
        assertEquals(ChangePasswordResult.Generic, change())
        offline = false
        assertEquals(1, accounts.current!!.keyVersion)
        assertArrayEquals(before.pvkCiphertext, accounts.current!!.pvkCiphertext)
    }

    @Test
    fun `una falla de Room tras el 200 se reintenta una vez`() {
        setUp()
        accounts.failNextUpdateKeys = 1
        assertEquals(ChangePasswordResult.Success(false), change())
        assertEquals(2, accounts.current!!.keyVersion)
        assertFalse(fakeAuth.signedOut)
    }

    @Test
    fun `si Room falla dos veces tras el 200 se cierra la sesion local`() {
        setUp()
        accounts.failNextUpdateKeys = 2
        assertEquals(ChangePasswordResult.LocalSaveFailedSignedOut, change())
        assertTrue(fakeAuth.signedOut)
        // En el servidor la contraseña sí cambió: la nueva entra.
        assertTrue(serverLogin(newPassword, userSaltOnServer()))
    }

    /** La sal que el servidor guardó en el cambio (la que ve el prelogin). */
    private fun userSaltOnServer(): ByteArray =
        (runBlocking { auth.prelogin(email) } as ApiResult.Success).value.salt

    @Test
    fun `igual a la actual no manda nada`() {
        setUp()
        assertEquals(ChangePasswordResult.SameAsCurrent, change(new = oldPassword))
        assertEquals(1, accounts.current!!.keyVersion)
    }

    @Test
    fun `la comparacion es sobre el texto normalizado`() {
        setUp()
        // «ñ» descompuesta y compuesta son la misma contraseña tras NFKC.
        val composed = "Contraseña-de-tortugas-29"
        val decomposed = "Contraseña-de-tortugas-29"
        assertEquals(ChangePasswordResult.SameAsCurrent, runBlocking { useCase.change(composed, null, decomposed) })
    }

    @Test
    fun `una contrasena nueva que no cumple la politica no manda nada`() {
        setUp()
        for (bad in listOf("corta1A", "todo-en-minusculas-sin-numero", "SOLO-MAYUSCULAS-Y-123456", "sin-mayuscula-pero-con-123")) {
            assertEquals(bad, ChangePasswordResult.InvalidNewPassword, change(new = bad))
        }
        assertEquals(1, accounts.current!!.keyVersion)
    }

    @Test
    fun `con 2FA activa pide el TOTP, sin el o con uno malo no cambia nada`() {
        setUp()
        val secret = runBlocking {
            val setup = (account.startTwoFactorSetup() as ApiResult.Success).value
            account.confirmTwoFactor(Totp.code(Base32.decode(setup.secret), Totp.step(clock / 1000)))
            setup.secret
        }
        clock += 30_000
        assertEquals(ChangePasswordResult.WrongCredentials, change(totp = null))
        assertEquals(ChangePasswordResult.WrongCredentials, change(totp = "000000"))
        assertEquals(1, accounts.current!!.keyVersion)
        val good = Totp.code(Base32.decode(secret), Totp.step(clock / 1000))
        assertEquals(ChangePasswordResult.Success(false), change(totp = good))
        assertEquals(2, accounts.current!!.keyVersion)
    }

    @Test
    fun `con la huella activa avisa que hay que volver a envolverla y quita el envoltorio viejo`() {
        setUp(bioWrapped = true)
        assertEquals(ChangePasswordResult.Success(biometricWasEnabled = true), change())
        // El envoltorio viejo ya no abre (su AAD lleva la versión anterior).
        assertNull(accounts.current!!.bioIv)
        assertNull(accounts.current!!.bioWrappedVaultKey)
    }

    @Test
    fun `cancelar mientras corre Argon2 no llama al servidor ni cambia nada`() = runBlocking {
        setUp()
        val job = launch(Dispatchers.Default) { useCase.change(oldPassword, null, newPassword) }
        delay(100)
        job.cancel()
        job.join()
        assertEquals(1, accounts.current!!.keyVersion)
        assertEquals(1, (account.getAccount() as ApiResult.Success).value.keyVersion)
    }

    @Test
    fun `sin cuenta en el telefono no hace nada`() {
        setUp()
        runBlocking { accounts.deleteAll() }
        assertEquals(ChangePasswordResult.Generic, change())
    }

    @Test
    fun `errores del servidor distintos de los conocidos son genericos`() {
        setUp()
        state.sessions.values.forEach { it.revoked = true }
        val result = change()
        assertTrue(result is ChangePasswordResult.Generic || result is ChangePasswordResult.WrongCredentials)
        assertEquals(1, accounts.current!!.keyVersion)
    }

    @Test
    fun `con la huella activa se desactiva todo antes de volver a envolver`() {
        setUp(bioWrapped = true)
        assertEquals(ChangePasswordResult.Success(biometricWasEnabled = true), change())
        // Llave del Keystore, columnas y bandera quedan apagadas: el estado guardado es coherente aunque el proceso muera.
        assertEquals(1, biometric.disableCalls)
    }

    @Test
    fun `sin la huella activa no se toca la huella`() {
        setUp()
        change()
        assertEquals(0, biometric.disableCalls)
    }

    @Test
    fun `se usan al menos los parametros de la cuenta, nunca menos`() {
        setUp()
        change()
        val row = accounts.current!!
        assertTrue(row.kdfMemoryKiB >= KdfParams.DEFAULT.memoryKiB)
        assertTrue(row.kdfIterations >= KdfParams.DEFAULT.iterations)
    }

    @Test
    fun `una cancelacion despues de mandar EP-17 no deja el telefono con llaves viejas`() = runBlocking {
        setUp()
        val job = launch(Dispatchers.Default) { useCase.change(oldPassword, null, newPassword) }
        // Se cancela cuando ya pasó Argon2 (el cambio tarda ~1 s): si EP-17 ya salió, el guardado local no se corta.
        delay(1_500)
        job.cancel()
        job.join()
        val serverVersion = (account.getAccount() as ApiResult.Success).value.keyVersion
        assertEquals("servidor y teléfono coinciden", serverVersion, accounts.current!!.keyVersion)
    }
}
