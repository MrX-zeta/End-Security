package com.luis.end_security.feature.auth.data

import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.core.crypto.Sealed
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity
import com.luis.end_security.feature.account.domain.AccountInfo
import com.luis.end_security.feature.account.domain.ServerKdf
import com.luis.end_security.feature.vault.data.FakeAccountDao
import com.luis.end_security.feature.vault.data.FakeVaultItemDao
import com.luis.end_security.feature.vault.data.ItemCipher
import com.luis.end_security.feature.vault.domain.VaultItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Recuperación tras un cambio de contraseña que el servidor aplicó y el teléfono no llegó a guardar: la sal del servidor es
 * distinta de la local y solo se acepta si se cumplen (a), (b) y (c). Cada condición falla por separado.
 */
class ServerAccountCheckTest {

    private val email = "ana@correo.com"
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val oldSalt = ByteArray(16) { 1 }
    private val newSalt = ByteArray(16) { 2 }
    private val vaultKeyBytes = ByteArray(32) { (it * 3 + 1).toByte() }
    private val otherKeyBytes = ByteArray(32) { (it * 5 + 7).toByte() }

    private val items = FakeVaultItemDao(FakeAccountDao())
    private val check = ServerAccountCheck(items)

    private fun local(
        salt: ByteArray = oldSalt,
        memoryKiB: Int = 65_536,
        iterations: Int = 3,
        keyVersion: Int = 1,
        user: UUID = userId,
        mail: String = email,
    ) = AccountEntity(
        user.toString(), mail, null, salt, memoryKiB, iterations, 1, ByteArray(12), ByteArray(48), keyVersion, 77L, 1_000L, null, null,
    )

    private fun server(
        salt: ByteArray = newSalt,
        memoryKiB: Int = 65_536,
        iterations: Int = 3,
        keyVersion: Int = 2,
        user: UUID = userId,
        mail: String = email,
    ) = AccountInfo(
        user, mail, true, null, keyVersion, ServerKdf(memoryKiB, iterations, 1, salt),
        Sealed(ByteArray(12), ByteArray(48)), false, false,
    )

    private fun key(bytes: ByteArray) = SecureBytes.copyOf(bytes)

    private fun putItem(withKey: ByteArray = vaultKeyBytes, state: SyncState = SyncState.Synced) {
        val id = UUID.randomUUID()
        val now = Instant.parse("2026-11-01T10:00:00Z")
        val sealed = key(withKey).use {
            ItemCipher.encrypt(it, id, userId, VaultItem("Correo", "ana", "secreto-1", null, null, now, now))
        }
        items.put(
            VaultItemEntity(
                id.toString(), ItemCipher.FORMAT_VERSION, sealed.nonce, sealed.ciphertext, null, null, 1L, state, null, 5L,
            ),
        )
    }

    private fun accepts(
        local: AccountEntity?,
        server: AccountInfo,
        keyBytes: ByteArray = vaultKeyBytes,
        typed: String = email,
    ): Boolean = runBlocking {
        key(keyBytes).use { check.accept(local, typed, server, it) }
    }

    // --- Casos que no son recuperación ---

    @Test
    fun `sin cuenta local es un acceso normal`() {
        assertFalse(check.isRecovery(null, email, server()))
        assertTrue(accepts(null, server()))
    }

    @Test
    fun `con la misma sal es un acceso normal aunque cambie la key_version`() {
        val same = server(salt = oldSalt, keyVersion = 1)
        assertFalse(check.isRecovery(local(), email, same))
        assertTrue(accepts(local(), same))
    }

    @Test
    fun `otro correo en el telefono es un acceso normal`() {
        assertTrue(accepts(local(mail = "otra@correo.com"), server()))
    }

    // --- Combinación válida ---

    @Test
    fun `la combinacion valida se acepta con elementos y pendientes locales`() {
        putItem()
        putItem(state = SyncState.Pending)
        assertTrue(check.isRecovery(local(), email, server()))
        assertTrue(accepts(local(), server()))
    }

    @Test
    fun `sin elementos locales no hay nada que comprobar y se acepta`() {
        assertTrue(accepts(local(), server()))
    }

    @Test
    fun `parametros mayores que los locales se aceptan`() {
        putItem()
        assertTrue(accepts(local(), server(memoryKiB = 131_072, iterations = 3)))
    }

    // --- Cada condición falla por separado ---

    @Test
    fun `a - parametros por debajo de los locales se rechazan`() {
        putItem()
        assertFalse(accepts(local(memoryKiB = 131_072), server(memoryKiB = 65_536)))
        assertFalse(accepts(local(iterations = 4), server(iterations = 3)))
    }

    @Test
    fun `a - parametros por debajo del piso del cliente se rechazan`() {
        // Con una cuenta local que (imposiblemente) estuviera por debajo del piso, el piso del cliente sigue mandando.
        assertFalse(accepts(local(memoryKiB = 1_024, iterations = 1), server(memoryKiB = 1_024, iterations = 1)))
    }

    @Test
    fun `a - el costo mas bajo se rechaza antes de derivar nada`() {
        assertTrue(check.lowersCost(local(memoryKiB = 131_072), email, ServerKdf(65_536, 3, 1, newSalt)))
        assertTrue(check.lowersCost(local(iterations = 4), email, ServerKdf(65_536, 3, 1, newSalt)))
        // Mismo costo o mayor, aunque la sal sea otra, no se rechaza de entrada.
        assertFalse(check.lowersCost(local(), email, ServerKdf(65_536, 3, 1, newSalt)))
        assertFalse(check.lowersCost(local(), email, ServerKdf(131_072, 3, 1, newSalt)))
        // Otro correo o sin cuenta local: nada que comparar.
        assertFalse(check.lowersCost(local(mail = "otra@correo.com"), email, ServerKdf(65_536, 3, 1, newSalt)))
        assertFalse(check.lowersCost(null, email, ServerKdf(65_536, 3, 1, newSalt)))
    }

    @Test
    fun `b - una key_version igual o menor que la local se rechaza`() {
        putItem()
        assertFalse(accepts(local(keyVersion = 3), server(keyVersion = 3)))
        assertFalse(accepts(local(keyVersion = 3), server(keyVersion = 2)))
    }

    @Test
    fun `c - una vault_key que no abre lo que hay en el telefono se rechaza`() {
        putItem()
        assertFalse(accepts(local(), server(), keyBytes = otherKeyBytes))
    }

    @Test
    fun `c - basta un elemento que no abra, aunque tambien haya pendientes`() {
        putItem(state = SyncState.Pending)
        assertFalse(accepts(local(), server(), keyBytes = otherKeyBytes))
    }

    @Test
    fun `c - una fila local ilegible no se puede comprobar y se rechaza`() {
        items.put(
            VaultItemEntity("no-es-uuid", ItemCipher.FORMAT_VERSION, ByteArray(12), ByteArray(40), null, null, 1L, SyncState.Synced, null, 1L),
        )
        assertFalse(accepts(local(), server()))
    }

    @Test
    fun `una lapida local no cuenta para la comprobacion`() {
        items.put(
            VaultItemEntity(UUID.randomUUID().toString(), 1, null, null, null, null, 2L, SyncState.Pending, 10L, 10L),
        )
        assertTrue(accepts(local(), server()))
    }

    @Test
    fun `otra cuenta con el mismo correo no es una recuperacion`() {
        putItem()
        assertFalse(accepts(local(), server(user = UUID.randomUUID())))
    }

    @Test
    fun `la comprobacion no consume la llave que se le presta`() {
        putItem()
        val prestada = key(vaultKeyBytes)
        runBlocking { check.accept(local(), email, server(), prestada) }
        assertFalse("el que llama sigue siendo dueño de la llave", prestada.isWiped)
        prestada.close()
        assertEquals(1, items.all.size)
    }

    // --- Revisión de la tarea 19 ---

    @Test
    fun `M1 - un servidor que cambia el correo de la respuesta no esquiva las comprobaciones`() {
        putItem()
        // Estado viejo (key_version menor) con el correo alterado: la cuenta local se identifica por lo que tecleó la persona.
        assertFalse(accepts(local(keyVersion = 3), server(keyVersion = 2, mail = "otro@correo.com")))
        // El mismo servidor, sin alterar el correo, también se rechaza por (b).
        assertFalse(accepts(local(keyVersion = 3), server(keyVersion = 2)))
        // Con la sal igual y el correo alterado, no es recuperación (acceso normal), como antes.
        assertTrue(accepts(local(), server(salt = oldSalt, keyVersion = 1, mail = "otro@correo.com")))
    }

    @Test
    fun `M1 - la recuperacion exige que el correo de la respuesta sea el de la cuenta local`() {
        putItem()
        assertFalse(accepts(local(), server(mail = "otro@correo.com")))
    }

    @Test
    fun `una cuenta local de otro correo no cuenta como la cuenta de lo tecleado`() {
        assertFalse(check.isRecovery(local(mail = "otra@correo.com"), email, server()))
    }

    @Test
    fun `B4 - un paralelismo menor se rechaza igual que memoria o iteraciones`() {
        putItem()
        val four = AccountEntity(
            userId.toString(), email, null, oldSalt, 65_536, 3, 4, ByteArray(12), ByteArray(48), 1, 0L, null, null, null,
        )
        assertTrue(check.lowersCost(four, email, ServerKdf(65_536, 3, 1, newSalt)))
        assertFalse(accepts(four, server()))
    }

    @Test
    fun `saltDiffers solo para la cuenta de ese correo con otra sal`() {
        assertTrue(check.saltDiffers(local(), email, ServerKdf(65_536, 3, 1, newSalt)))
        assertFalse(check.saltDiffers(local(), email, ServerKdf(65_536, 3, 1, oldSalt)))
        assertFalse(check.saltDiffers(local(mail = "otra@correo.com"), email, ServerKdf(65_536, 3, 1, newSalt)))
        assertFalse(check.saltDiffers(null, email, ServerKdf(65_536, 3, 1, newSalt)))
    }
}
