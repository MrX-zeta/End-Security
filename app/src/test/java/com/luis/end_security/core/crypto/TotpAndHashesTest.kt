package com.luis.end_security.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpAndHashesTest {

    /** RFC 6238, apéndice B (SHA-1): secreto ASCII «12345678901234567890», códigos de 8 dígitos. */
    private val rfcSecret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun `vectores de RFC 6238 con SHA-1`() {
        mapOf(
            59L to "94287082",
            1_111_111_109L to "07081804",
            1_111_111_111L to "14050471",
            1_234_567_890L to "89005924",
            2_000_000_000L to "69279037",
            20_000_000_000L to "65353130",
        ).forEach { (time, expected) ->
            assertEquals("T=$time", expected, Totp.code(rfcSecret, Totp.step(time), digits = 8))
        }
    }

    @Test
    fun `verify acepta un paso de margen y no el mismo paso dos veces`() {
        val now = 1_234_567_890L
        val code = Totp.code(rfcSecret, Totp.step(now - 30))
        val step = Totp.verify(rfcSecret, code, now, lastUsedStep = null)
        assertEquals(Totp.step(now - 30), step)
        assertNull("anti-replay", Totp.verify(rfcSecret, code, now, lastUsedStep = step))
        assertNull("fuera de la ventana", Totp.verify(rfcSecret, Totp.code(rfcSecret, Totp.step(now) - 2), now, null))
    }

    @Test
    fun `HMAC-SHA256 de RFC 4231 caso 1`() {
        val mac = Hashes.hmacSha256(ByteArray(20) { 0x0b }, "Hi There".toByteArray(Charsets.US_ASCII))
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", mac.toHex())
    }

    @Test
    fun `SHA-256 de abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashes.sha256("abc".toByteArray(Charsets.US_ASCII)).toHex(),
        )
    }

    @Test
    fun `Base32 de RFC 4648 sin relleno`() {
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertEquals("MZXW6", Base32.encode("foo".toByteArray()))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
    }
}
