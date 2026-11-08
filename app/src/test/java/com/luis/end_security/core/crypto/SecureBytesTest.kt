package com.luis.end_security.core.crypto

import com.luis.end_security.feature.auth.domain.MasterPasswordNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureBytesTest {

    @Test
    fun `toString no muestra el contenido`() {
        val secret = SecureBytes.copyOf(byteArrayOf(0x41, 0x42, 0x43))
        assertEquals("SecureBytes(***)", secret.toString())
        assertFalse("$secret".contains("65"))
    }

    @Test
    fun `copyOf hace una copia defensiva`() {
        val source = byteArrayOf(1, 2, 3)
        val secret = SecureBytes.copyOf(source)
        source.wipe()
        assertEquals(listOf<Byte>(1, 2, 3), secret.withBytes { it.toList() })
    }

    @Test
    fun `leer despues de wipe falla`() {
        val secret = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        secret.wipe()
        assertTrue(secret.isWiped)
        assertThrows(IllegalStateException::class.java) { secret.withBytes { it.size } }
    }

    @Test
    fun `close borra`() {
        val secret = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        secret.close()
        assertTrue(secret.isWiped)
        assertThrows(IllegalStateException::class.java) { secret.withBytes { it.size } }
    }

    @Test
    fun `withBytes presta el mismo arreglo, asi que una referencia sacada queda en ceros al borrar`() {
        val secret = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        // Sacar la referencia no sirve: es el mismo arreglo y queda en ceros al borrar.
        val leaked = secret.withBytes { it }
        secret.wipe()
        assertEquals(listOf<Byte>(0, 0, 0), leaked.toList())
    }

    @Test
    fun `wipe espera a que termine un withBytes en otro hilo`() {
        val secret = SecureBytes.copyOf(ByteArray(32) { 5 })
        val reading = java.util.concurrent.CountDownLatch(1)
        var seen: List<Byte> = emptyList()
        val reader = Thread {
            secret.withBytes { bytes ->
                reading.countDown()
                Thread.sleep(200)
                seen = bytes.toList()
            }
        }
        reader.start()
        reading.await()
        secret.wipe()
        reader.join()
        assertEquals("el lector vio la llave entera, no ceros", List<Byte>(32) { 5 }, seen)
        assertTrue(secret.isWiped)
    }

    @Test
    fun `equals y hashCode son por identidad`() {
        val first = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        val second = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        assertNotEquals(first, second)
        assertEquals(first, first)
    }

    @Test
    fun `KeyDerivation da las mismas llaves que el vector propio`() {
        val typed = String(
            intArrayOf(0x6E, 0x303) + "andú tortuga ".map { it.code }.toIntArray() +
                intArrayOf(0xFF12, 0xFF10, 0xFF12, 0xFF16, 0x20, 0x2764, 0xFE0F),
            0,
            2 + 13 + 7,
        )
        val masterKey = KeyDerivation.deriveMasterKey(
            MasterPasswordNormalizer.normalize(typed),
            ByteArray(16) { it.toByte() },
            KdfParams.DEFAULT,
        )
        // withBytes solo presta los bytes: cada llave se cierra en su finally.
        val encKey = KeyDerivation.encKey(masterKey)
        val authKey = KeyDerivation.authKey(masterKey)
        try {
            assertEquals("7c90a1fd322d91ed0ac3ef53c7e8c209ce82412da51e7c6365aa92de3bd96d70", encKey.withBytes { it.toHex() })
            assertEquals("dcf81e4ceb6fcf6a162b23e98558cb06236d9f68d81c5717038f46bf1b37b3ec", authKey.withBytes { it.toHex() })
        } finally {
            encKey.close()
            authKey.close()
            masterKey.close()
        }
        assertThrows(IllegalStateException::class.java) { KeyDerivation.encKey(masterKey) }
    }

    @Test
    fun `el use de Kotlin cierra y borra`() {
        val secret = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        val size = secret.use { it.size }
        assertEquals(3, size)
        assertTrue(secret.isWiped)
    }

    @Test
    fun `wipe dentro de withBytes del mismo hilo falla en vez de colgarse`() {
        val secret = SecureBytes.copyOf(byteArrayOf(1, 2, 3))
        assertThrows(IllegalStateException::class.java) { secret.withBytes { secret.wipe() } }
        assertFalse(secret.isWiped)
        secret.wipe()
        assertTrue(secret.isWiped)
    }
}
