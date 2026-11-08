package com.luis.end_security.feature.account.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAuthUriTest {

    private val setup = TwoFactorSetup("JBSWY3DPEHPK3PXP", "End Security", "ana.lopez@correo.com", "SHA1", 6, 30)

    @Test
    fun `arma el URI del formato de Google Authenticator`() {
        assertEquals(
            "otpauth://totp/End%20Security%3Aana.lopez%40correo.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=End%20Security&algorithm=SHA1&digits=6&period=30",
            OtpAuthUri.build(setup),
        )
    }

    @Test
    fun `escapa lo que no es seguro en un URI`() {
        val uri = OtpAuthUri.build(TwoFactorSetup("AAAA", "Mi App & Co", "a+b@c.com", "SHA1", 6, 30))
        assertTrue(uri.startsWith("otpauth://totp/Mi%20App%20%26%20Co%3Aa%2Bb%40c.com?"))
        assertFalse(uri.contains(" "))
        assertFalse(uri.contains("+b"))
    }

    @Test
    fun `lleva los parametros del servidor, no valores fijos`() {
        val uri = OtpAuthUri.build(TwoFactorSetup("AAAA", "X", "y", "SHA256", 8, 60))
        assertTrue(uri.contains("algorithm=SHA256"))
        assertTrue(uri.contains("digits=8"))
        assertTrue(uri.contains("period=60"))
    }

    @Test
    fun `la sonda para preguntar al sistema no lleva secreto`() {
        assertFalse(OtpAuthUri.PROBE.contains("secret"))
        assertTrue(OtpAuthUri.PROBE.startsWith("otpauth://totp/"))
    }
}
