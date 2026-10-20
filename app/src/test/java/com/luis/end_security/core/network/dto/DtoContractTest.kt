package com.luis.end_security.core.network.dto

import com.luis.end_security.core.common.ApiError
import com.luis.end_security.core.network.ApiClient
import com.luis.end_security.core.network.ApiJson
import com.luis.end_security.core.network.ApiMappers
import com.luis.end_security.core.network.ApiMappers.toDomain
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ejemplos JSON escritos a mano a partir de `docs/api/openapi.yaml` v1.0.0. Los usará también la
 * versión con Retrofit: si cambia el contrato, estos ejemplos cambian primero.
 */
class DtoContractTest {

    private fun tree(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val accountJson = """
        {"userId":"0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90","email":"ana@correo.com","emailVerified":true,
         "displayName":null,"keyVersion":1,
         "kdf":{"algorithm":"argon2id","memoryKiB":65536,"iterations":3,"parallelism":1,"salt":"AAECAwQFBgcICQoLDA0ODw"},
         "protectedVaultKey":{"nonce":"AAAAAAAAAAAAAAAA","ciphertext":"${"A".repeat(64)}"},
         "twoFactorEnabled":false}
    """.trimIndent()

    @Test
    fun `Authenticated del login`() {
        val json = """
            {"status":"authenticated",
             "tokens":{"accessToken":"eyJhbGciOi.x.y","accessTokenExpiresIn":900,
                       "refreshToken":"${"B".repeat(43)}","refreshTokenExpiresAt":"2026-11-18T20:15:00Z",
                       "sessionId":"7c2a1d3e-4b5f-4a6b-8c7d-9e0f1a2b3c4d"},
             "account":$accountJson}
        """.trimIndent()
        val dto = ApiJson.decodeFromString(AuthResponseDto.serializer(), json)
        assertEquals("authenticated", dto.status)
        val account = dto.account!!.toDomain()
        assertEquals(65_536, account.kdf.memoryKiB)
        assertEquals(16, account.kdf.salt.size)
        assertEquals(48, account.protectedVaultKey.ciphertext.size)
        assertNull(account.displayName)
        assertEquals(900, dto.tokens!!.toDomain().accessTokenExpiresInSeconds)
    }

    @Test
    fun `TwoFactorRequired del login`() {
        val json = """{"status":"two_factor_required","challengeToken":"${"c".repeat(43)}","challengeExpiresIn":300}"""
        val dto = ApiJson.decodeFromString(AuthResponseDto.serializer(), json)
        assertEquals("two_factor_required", dto.status)
        assertEquals(300, dto.challengeExpiresIn)
        assertNull(dto.tokens)
    }

    @Test
    fun `un campo nuevo del servidor se ignora`() {
        val json = """{"kdf":{"algorithm":"argon2id","memoryKiB":65536,"iterations":3,"parallelism":1,"salt":"AAECAwQFBgcICQoLDA0ODw","nuevo":1}}"""
        assertEquals(3, ApiJson.decodeFromString(PreloginResponseDto.serializer(), json).kdf.iterations)
    }

    @Test
    fun `registro sin nombre visible no manda displayName`() {
        val dto = RegisterRequestDto(
            userId = "0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90",
            email = "ana@correo.com",
            authKey = "A".repeat(43),
            kdf = KdfDto("argon2id", 65_536, 3, 1, "A".repeat(22)),
            protectedVaultKey = ProtectedVaultKeyDto("A".repeat(16), "A".repeat(64)),
        )
        val json = tree(ApiJson.encodeToString(RegisterRequestDto.serializer(), dto))
        assertFalse("displayName es opcional y no admite null", json.containsKey("displayName"))
        assertEquals(setOf("userId", "email", "authKey", "kdf", "protectedVaultKey"), json.keys)
    }

    @Test
    fun `al crear un elemento expectedVersion va como null`() {
        val json = tree(ApiJson.encodeToString(PutItemRequestDto.serializer(), PutItemRequestDto(null, 1, "A".repeat(16), "A".repeat(23))))
        assertTrue(json.containsKey("expectedVersion"))
        assertEquals("null", json.getValue("expectedVersion").toString())
    }

    @Test
    fun `cambios de la boveda con una lapida`() {
        val json = """
            {"items":[
               {"id":"11111111-1111-4111-8111-111111111111","version":2,"revision":7,"formatVersion":1,"deleted":true,
                "nonce":null,"ciphertext":null,"updatedAt":"2026-10-19T20:15:00Z"},
               {"id":"22222222-2222-4222-8222-222222222222","version":1,"revision":8,"formatVersion":1,"deleted":false,
                "nonce":"AAAAAAAAAAAAAAAA","ciphertext":"${"A".repeat(40)}","updatedAt":"2026-10-19T20:16:00Z"}],
             "nextSince":8,"hasMore":false,"currentRevision":8}
        """.trimIndent()
        val items = ApiJson.decodeFromString(VaultChangesDto.serializer(), json).items.map { it.toDomain() }
        assertNull(items[0].sealed)
        assertEquals(12, items[1].sealed!!.nonce.size)
    }

    @Test
    fun `Problem con code y Retry-After`() {
        val body = """{"type":"about:blank","title":"Too Many Requests","status":429,"code":"rate_limited","retryAfterSeconds":900}"""
        assertEquals(ApiError.RateLimited(900), ApiClient.errorOf(HttpResponse(429, body)))
        val conflict = """{"type":"about:blank","title":"Conflict","status":409,"code":"version_conflict","currentVersion":4,"currentRevision":12}"""
        assertEquals(ApiError.VersionConflict(4, 12), ApiClient.errorOf(HttpResponse(409, conflict)))
        val verify = """{"type":"about:blank","title":"Unauthorized","status":401,"code":"invalid_code","attemptsRemaining":3}"""
        assertEquals(ApiError.InvalidCode(3), ApiClient.errorOf(HttpResponse(401, verify)))
        assertEquals("un code desconocido", ApiError.ServerError, ApiClient.errorOf(HttpResponse(500, """{"code":"x"}""")))
        assertEquals("el title no se usa", ApiError.InvalidCredentials,
            ApiClient.errorOf(HttpResponse(401, """{"type":"about:blank","title":"Otra cosa","status":401,"code":"invalid_credentials"}""")))
    }

    @Test
    fun `Base64url sin relleno y UUID canonico`() {
        assertEquals("-_8", Base64Url.encode(byteArrayOf(0xfb.toByte(), 0xff.toByte())))
        assertThrows(IllegalArgumentException::class.java) { Base64Url.decode("+/8=") }
        assertThrows(IllegalArgumentException::class.java) { ApiMappers.uuid("0B9F3C2E-5D1A-4F7E-9A2B-6C8D1E4F7A90") }
        assertEquals("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90", ApiMappers.uuid("0b9f3c2e-5d1a-4f7e-9a2b-6c8d1e4f7a90").toString())
    }
}
