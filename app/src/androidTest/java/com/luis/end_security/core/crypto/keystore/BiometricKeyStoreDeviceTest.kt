package com.luis.end_security.core.crypto.keystore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.util.UUID

/**
 * La llave biométrica en el Keystore real (DEC-09): exige autenticación en cada uso, así que sin pasar por
 * `BiometricPrompt` no se puede cifrar ni descifrar nada con ella.
 */
@RunWith(AndroidJUnit4::class)
class BiometricKeyStoreDeviceTest {

    private val store = AndroidBiometricKeyStore()
    private val userId = UUID.randomUUID()

    /** Crear una llave que exige autenticación en cada uso necesita al menos una huella registrada: sin ella, se omite. */
    private fun requireEnrolledFingerprint() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val status = androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        org.junit.Assume.assumeTrue("Sin huella registrada en el dispositivo", status == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS)
    }

    @After
    fun cleanUp() {
        store.deleteAll()
    }

    @Test
    fun laLlaveSeCreaYDaUnCipherConIvDe12Bytes() {
        requireEnrolledFingerprint()
        val cipher = store.newEncryptCipher(userId)
        assertEquals(12, cipher.iv.size)
    }

    @Test
    fun sinAutenticarConLaHuellaNoSePuedeCifrar() {
        requireEnrolledFingerprint()
        val cipher = store.newEncryptCipher(userId)
        try {
            cipher.doFinal(ByteArray(32))
            fail("La llave exige la huella en cada uso: no debía cifrar")
        } catch (_: GeneralSecurityException) {
            // Esperado: UserNotAuthenticatedException o similar.
        }
    }

    @Test
    fun crearOtraLlaveReemplazaLaAnteriorConOtroIv() {
        requireEnrolledFingerprint()
        val first = store.newEncryptCipher(userId).iv
        val second = store.newEncryptCipher(userId).iv
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun sinLlaveElDescifradoPideLaContrasena() {
        try {
            store.decryptCipher(UUID.randomUUID(), ByteArray(12))
            fail("Sin llave debía lanzar BiometricKeyInvalidatedException")
        } catch (_: BiometricKeyInvalidatedException) {
        }
    }

    @Test
    fun borrarQuitaLaLlave() {
        requireEnrolledFingerprint()
        store.newEncryptCipher(userId)
        store.delete(userId)
        try {
            store.decryptCipher(userId, ByteArray(12))
            fail()
        } catch (_: BiometricKeyInvalidatedException) {
        }
    }

    @Test
    fun deleteAllQuitaTodasLasLlavesDeLaApp() {
        requireEnrolledFingerprint()
        val other = UUID.randomUUID()
        store.newEncryptCipher(userId)
        store.newEncryptCipher(other)
        store.deleteAll()
        for (id in listOf(userId, other)) {
            try {
                store.decryptCipher(id, ByteArray(12))
                fail()
            } catch (_: BiometricKeyInvalidatedException) {
            }
        }
        assertTrue(true)
    }
}
