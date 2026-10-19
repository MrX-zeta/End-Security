package com.luis.end_security.feature.vault.data

import com.luis.end_security.core.crypto.AesGcm
import com.luis.end_security.core.crypto.AuthenticationFailed
import com.luis.end_security.core.crypto.SecureBytes
import com.luis.end_security.feature.vault.domain.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ItemCipherTest {

    private val vaultKey = SecureBytes.copyOf(ByteArray(32) { (it + 1).toByte() })
    private val userId = UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90")
    private val itemA = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val itemB = UUID.fromString("22222222-2222-4222-8222-222222222222")

    private val item = VaultItem(
        name = "Gmail",
        username = "ana@correo.com",
        password = "tortuga violeta 29",
        url = "https://mail.google.com",
        notes = null,
        createdAt = Instant.parse("2026-10-18T10:15:30Z"),
        passwordChangedAt = Instant.parse("2026-10-18T10:15:30Z"),
    )

    @Test
    fun `ida y vuelta`() {
        val sealed = ItemCipher.encrypt(vaultKey, itemA, userId, item)
        val opened = ItemCipher.decrypt(vaultKey, itemA, userId, ItemCipher.FORMAT_VERSION, sealed)
        assertEquals(item, opened)
        assertNull(opened.notes)
    }

    @Test
    fun `el bloque no contiene el texto en claro`() {
        val sealed = ItemCipher.encrypt(vaultKey, itemA, userId, item)
        val asText = String(sealed.ciphertext, Charsets.ISO_8859_1)
        assertFalse(asText.contains("Gmail"))
        assertFalse(asText.contains("tortuga"))
    }

    @Test
    fun `otro itemId, otro userId u otra version de formato fallan`() {
        val sealed = ItemCipher.encrypt(vaultKey, itemA, userId, item)
        assertThrows(AuthenticationFailed::class.java) { ItemCipher.decrypt(vaultKey, itemB, userId, 1, sealed) }
        assertThrows(AuthenticationFailed::class.java) {
            ItemCipher.decrypt(vaultKey, itemA, UUID.fromString("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a91"), 1, sealed)
        }
        assertThrows(AuthenticationFailed::class.java) { ItemCipher.decrypt(vaultKey, itemA, userId, 2, sealed) }
    }

    @Test
    fun `el bloque de un elemento no se abre como otro`() {
        val sealedA = ItemCipher.encrypt(vaultKey, itemA, userId, item)
        // Un servidor que copia el bloque de A en la fila de B.
        assertThrows(AuthenticationFailed::class.java) { ItemCipher.decrypt(vaultKey, itemB, userId, 1, sealedA) }
    }

    @Test
    fun `otra vault_key falla`() {
        val sealed = ItemCipher.encrypt(vaultKey, itemA, userId, item)
        val other = SecureBytes.copyOf(ByteArray(32) { 9 })
        assertThrows(AuthenticationFailed::class.java) { ItemCipher.decrypt(other, itemA, userId, 1, sealed) }
    }

    @Test
    fun `campos desconocidos se ignoran`() {
        val json = """
            {"name":"Gmail","username":"ana","password":"x","favorite":true,"tags":["correo"],
             "created_at":"2026-10-18T10:15:30Z","password_changed_at":"2026-10-18T10:15:30Z"}
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val aad = "$itemA|$userId|1".toByteArray(Charsets.US_ASCII)
        val sealed = vaultKey.withBytes { AesGcm.seal(it, json, aad) }
        val opened = ItemCipher.decrypt(vaultKey, itemA, userId, 1, sealed)
        assertEquals("Gmail", opened.name)
        assertNull(opened.url)
    }

    @Test
    fun `un bloque valido que no es un elemento v1 da ItemFormatException`() {
        val aad = "$itemA|$userId|1".toByteArray(Charsets.US_ASCII)
        val sealed = vaultKey.withBytes { AesGcm.seal(it, "{\"name\":1}".toByteArray(), aad) }
        assertThrows(ItemFormatException::class.java) { ItemCipher.decrypt(vaultKey, itemA, userId, 1, sealed) }
    }

    @Test
    fun `toString no muestra el elemento`() {
        assertEquals("VaultItem(***)", item.toString())
    }
}
