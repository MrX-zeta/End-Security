package com.luis.end_security.feature.account.data

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.KeyDerivation
import com.luis.end_security.core.crypto.SecretReader
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import com.luis.end_security.feature.vault.data.FakeAccountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La `auth_key` de la reautenticación se deriva en el teléfono con los parámetros guardados en `account` (DEC-05). */
class ReauthRunnerImplTest {

    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val password = "Tortuga-violeta-salta-29-nubes"

    private fun account(memory: Int = 65_536, iterations: Int = 3) = AccountEntity(
        "0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90", "ana@correo.com", null, salt, memory, iterations, 1,
        ByteArray(12), ByteArray(48), 1, 0, null, null, null,
    )

    private fun runner(account: AccountEntity?) = ReauthRunnerImpl(FakeAccountDao(account), Dispatchers.Default)

    private fun expectedAuthKey(text: String): ByteArray {
        var copy = ByteArray(0)
        KeyDerivation.deriveMasterKey(MasterPasswordNormalizer.normalize(text), salt, KdfParams.DEFAULT).use { master ->
            KeyDerivation.authKey(master).use { it.withBytes { bytes -> copy = bytes.copyOf() } }
        }
        return copy
    }

    private fun SecretReader.bytes(): ByteArray {
        var copy = ByteArray(0)
        withBytes { copy = it.copyOf() }
        return copy
    }

    @Test
    fun `deriva la authKey igual que al entrar y manda el TOTP`() = runBlocking {
        var seen = ByteArray(0)
        var seenTotp: String? = null
        val result = runner(account()).run(password, "482916") { reauth ->
            seen = reauth.authKey.bytes()
            seenTotp = reauth.totpCode
            ApiResult.Success("ok")
        }
        assertEquals(ApiResult.Success("ok"), result)
        assertArrayEquals(expectedAuthKey(password), seen)
        assertEquals("482916", seenTotp)
    }

    @Test
    fun `otra contrasena da otra authKey`() = runBlocking {
        var seen = ByteArray(0)
        runner(account()).run("otra contraseña", null) { reauth ->
            seen = reauth.authKey.bytes()
            ApiResult.Success(Unit)
        }
        assertFalse(seen.contentEquals(expectedAuthKey(password)))
    }

    @Test
    fun `normaliza la contrasena como al entrar`() = runBlocking {
        var composed = ByteArray(0)
        var decomposed = ByteArray(0)
        runner(account()).run("contraseña-de-tortugas-29", null) { composed = it.authKey.bytes(); ApiResult.Success(Unit) }
        runner(account()).run("contraseña-de-tortugas-29", null) { decomposed = it.authKey.bytes(); ApiResult.Success(Unit) }
        assertArrayEquals(composed, decomposed)
    }

    @Test
    fun `la authKey se borra al terminar`() = runBlocking {
        var kept: SecretReader? = null
        runner(account()).run(password, null) { reauth ->
            kept = reauth.authKey
            ApiResult.Success(Unit)
        }
        assertTrue((kept as SecureBytes).isWiped)
    }

    @Test
    fun `la authKey se borra aunque la llamada falle`() = runBlocking {
        var kept: SecretReader? = null
        val result = runner(account()).run(password, null) { reauth ->
            kept = reauth.authKey
            ApiResult.Failure(ApiError.ReauthRequired)
        }
        assertEquals(ApiResult.Failure(ApiError.ReauthRequired), result)
        assertTrue((kept as SecureBytes).isWiped)
    }

    @Test
    fun `sin cuenta en el telefono no deriva ni llama`() = runBlocking {
        var called = false
        val result = runner(null).run(password, null) {
            called = true
            ApiResult.Success(Unit)
        }
        assertFalse(called)
        assertEquals(ApiResult.Failure(ApiError.ValidationFailed), result)
    }

    @Test
    fun `parametros por debajo del piso no se usan`() = runBlocking {
        var called = false
        val result = runner(account(memory = 1_024, iterations = 1)).run(password, null) {
            called = true
            ApiResult.Success(Unit)
        }
        assertFalse(called)
        assertEquals(ApiResult.Failure(ApiError.ReauthRequired), result)
    }

    @Test
    fun `si se cancela mientras corre Argon2 no se llama al servidor`() = runBlocking {
        var called = false
        val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            runner(account()).run(password, null) {
                called = true
                ApiResult.Success(Unit)
            }
        }
        kotlinx.coroutines.delay(100)
        job.cancel()
        job.join()
        assertFalse(called)
    }
}

