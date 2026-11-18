package com.luis.end_security.core.network.backend

import com.luis.end_security.core.network.ApiJson
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.dto.AccountDto
import com.luis.end_security.core.network.dto.ActivityDto
import com.luis.end_security.core.network.dto.AuthResponseDto
import com.luis.end_security.core.network.dto.ProblemDto
import com.luis.end_security.core.network.dto.TwoFactorSetupDto
import com.luis.end_security.core.network.dto.VaultChangesDto
import com.luis.end_security.core.network.dto.VaultItemDto
import com.luis.end_security.core.network.dto.WriteResultDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * El traductor contra respuestas con la forma exacta del backend `integracion/app` (envoltura de `TransformInterceptor`,
 * errores de `HttpExceptionFilter`). Tabla completa en specs/001-adaptador-backend-real/contracts/translation.md.
 */
class BackendTranslatorTest {

    private val calls = mutableListOf<BackendCall>()
    private val replies = ArrayDeque<BackendReply>()
    private val translator = BackendTranslator(deviceName = { "Pixel 8" }, now = { NOW })

    private suspend fun run(method: String, path: String, body: String? = null, bearer: String? = null): HttpResponse =
        translator.translate(HttpRequest(method, path, body, bearer)) { call ->
            calls += call
            replies.removeFirst()
        }

    private fun reply(status: Int, body: String?, retryAfter: Int? = null) = replies.addLast(BackendReply(status, body, retryAfter))
    private fun ok(data: String, status: Int = 200) = reply(status, """{"success":true,"data":$data,"timestamp":"2026-11-18T12:00:00.000Z"}""")
    private fun error(status: Int, error: String, details: String? = null) = reply(
        status,
        """{"statusCode":$status,"error":"$error","message":"texto del servidor"${details?.let { ""","details":$it""" } ?: ""},"path":"/api/x","timestamp":"t"}""",
    )

    private fun HttpResponse.code() = ApiJson.decodeFromString(ProblemDto.serializer(), body!!).code
    private fun HttpResponse.problem() = ApiJson.decodeFromString(ProblemDto.serializer(), body!!)
    private fun sent(i: Int = calls.lastIndex) = ApiJson.parseToJsonElement(calls[i].body!!).jsonObject
    private fun String.field(name: String) = ApiJson.parseToJsonElement(this).jsonObject[name]!!.jsonPrimitive.content

    // ---- Fundamentos: Base64, envoltura y errores (T007) ----

    @Test
    fun `prelogin cambia la forma del kdf y el Base64`() = runTest {
        ok("""{"kdf":{"salt":"+/+/+/+/+/+/+/+/+/+/+w==","memoryKib":65536,"iterations":3,"parallelism":1}}""")
        val response = run("POST", "/auth/prelogin", """{"email":"ana@example.com"}""")
        assertEquals("auth/prelogin", calls.single().path)
        val kdf = ApiJson.parseToJsonElement(response.body!!).jsonObject["kdf"]!!.jsonObject
        assertEquals("argon2id", kdf["algorithm"]!!.jsonPrimitive.content)
        assertEquals("65536", kdf["memoryKiB"]!!.jsonPrimitive.content)
        assertEquals("-_-_-_-_-_-_-_-_-_-_-w", kdf["salt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `los errores del backend llegan como code y nunca con su texto`() = runTest {
        error(400, "Bad Request")
        assertEquals("validation_failed", run("POST", "/auth/prelogin", """{"email":"x"}""").code())
        reply(429, """{"statusCode":429,"error":"TooManyRequests","message":"espera"}""", retryAfter = 42)
        val limited = run("POST", "/auth/prelogin", """{"email":"x"}""").problem()
        assertEquals("rate_limited", limited.code)
        assertEquals(42, limited.retryAfterSeconds)
        reply(500, """{"statusCode":500,"error":"InternalServerError","message":"boom"}""")
        val failed = run("POST", "/auth/prelogin", """{"email":"x"}""")
        assertEquals("server_error", failed.code())
        assertTrue("texto del servidor" !in failed.body!! && "boom" !in failed.body!!)
    }

    // ---- US1: acceso (T008) ----

    @Test
    fun `registro manda userId, authHash, pvk y deviceName y guarda el desafio para verificar`() = runTest {
        ok("""{"requiresEmailVerification":true,"requires2FA":false,"challengeToken":"$CHALLENGE","verification":{"sentTo":"an***@example.com","codeSent":true,"expiresAt":"2026-11-18T12:15:00.000Z","resendAvailableInSeconds":60}}""", 201)
        val registered = run("POST", "/auth/register", REGISTER)
        assertEquals(202, registered.status)
        val body = sent()
        assertEquals(USER_ID, body["userId"]!!.jsonPrimitive.content)
        assertEquals("AQID", body["authHash"]!!.jsonPrimitive.content)
        assertEquals("Pixel 8", body["deviceName"]!!.jsonPrimitive.content)
        assertTrue("pvk" in body && "protectedVaultKey" !in body && "authKey" !in body)

        ok(AUTH_OK)
        val verified = run("POST", "/auth/email/verify", """{"email":" Ana@Example.com ","code":"123456","deviceName":"Pixel 8"}""")
        assertEquals("auth/email/verify/confirm", calls.last().path)
        assertEquals(CHALLENGE, sent()["challengeToken"]!!.jsonPrimitive.content)
        val auth = ApiJson.decodeFromString(AuthResponseDto.serializer(), verified.body!!)
        assertEquals("authenticated", auth.status)
        assertEquals(SESSION_ID, auth.tokens!!.sessionId)
        assertEquals(NOW.plusSeconds(7 * 86_400).toString(), auth.tokens!!.refreshTokenExpiresAt)
        assertEquals("AAECAwQFBgcICQoL", auth.account!!.protectedVaultKey.nonce)
        assertNull("displayName vacío del backend = null", auth.account!!.displayName)
    }

    @Test
    fun `codigo de correo incorrecto cuenta los intentos y sin desafio es challenge_expired`() = runTest {
        assertEquals("challenge_expired", run("POST", "/auth/email/verify", """{"email":"nadie@x.com","code":"1","deviceName":"d"}""").code())
        assertTrue(calls.isEmpty())

        ok("""{"requiresEmailVerification":true,"requires2FA":false,"challengeToken":"$CHALLENGE"}""")
        run("POST", "/auth/login", LOGIN)
        error(400, "InvalidCode")
        val wrong = run("POST", "/auth/email/verify", """{"email":"ana@example.com","code":"000000","deviceName":"d"}""").problem()
        assertEquals("invalid_code", wrong.code)
        assertEquals(4, wrong.attemptsRemaining)
    }

    @Test
    fun `login traduce sus tres respuestas`() = runTest {
        ok(AUTH_OK)
        assertEquals("authenticated", run("POST", "/auth/login", LOGIN).body!!.field("status"))

        ok("""{"requiresEmailVerification":false,"requires2FA":true,"challengeToken":"$CHALLENGE"}""")
        val twoFactor = ApiJson.decodeFromString(AuthResponseDto.serializer(), run("POST", "/auth/login", LOGIN).body!!)
        assertEquals("two_factor_required", twoFactor.status)
        assertEquals(CHALLENGE, twoFactor.challengeToken)
        assertEquals(300, twoFactor.challengeExpiresIn)

        ok("""{"requiresEmailVerification":true,"requires2FA":false,"challengeToken":"$CHALLENGE"}""")
        assertEquals("email_not_verified", run("POST", "/auth/login", LOGIN).code())

        error(401, "Unauthorized")
        assertEquals("invalid_credentials", run("POST", "/auth/login", LOGIN).code())
    }

    @Test
    fun `codigo de respaldo pregunta cuantos quedan con el token nuevo`() = runTest {
        ok(AUTH_OK)
        ok("""{"status":"active","activatedAt":"2026-11-18T10:00:00.000Z","recoveryCodesRemaining":2}""")
        val response = run("POST", "/auth/2fa/recovery", """{"challengeToken":"$CHALLENGE","recoveryCode":"k7m2q-x9rtb"}""")
        assertEquals("k7m2q-x9rtb", sent(0)["recoveryCode"]!!.jsonPrimitive.content)
        assertEquals("ACCESS", calls[1].bearer)
        assertEquals(2, ApiJson.decodeFromString(AuthResponseDto.serializer(), response.body!!).recoveryCodesRemaining)

        error(401, "InvalidSecondFactor")
        assertEquals("invalid_code", run("POST", "/auth/2fa/verify", """{"challengeToken":"$CHALLENGE","code":"000000"}""").code())
        error(401, "Unauthorized")
        assertEquals("challenge_expired", run("POST", "/auth/2fa/verify", """{"challengeToken":"$CHALLENGE","code":"000000"}""").code())
    }

    @Test
    fun `401 con sesion es token_expired y 401 al renovar es session_revoked (BR-11)`() = runTest {
        error(401, "Unauthorized")
        assertEquals("token_expired", run("GET", "/sessions", bearer = "ACCESS").code())
        error(401, "Unauthorized")
        assertEquals("session_revoked", run("POST", "/auth/refresh", """{"refreshToken":"x"}""").code())
    }

    // ---- US2: bóveda (T012) ----

    @Test
    fun `descarga completa con since mayor a 0 es cursor_expired`() = runTest {
        ok("""{"revision":7,"purgedThroughRevision":0,"keyVersion":1,"fullResync":true,"items":[$ITEM]}""")
        val full = ApiJson.decodeFromString(VaultChangesDto.serializer(), run("GET", "/vault/items?since=0&limit=500", bearer = "A").body!!)
        assertEquals("vault/sync?since=0", calls.last().path)
        assertEquals(7, full.nextSince)
        assertEquals("AAECAwQFBgcICQoL", full.items.single().nonce)

        ok("""{"revision":7,"purgedThroughRevision":5,"keyVersion":1,"fullResync":true,"items":[]}""")
        assertEquals(410, run("GET", "/vault/items?since=3&limit=500", bearer = "A").status)
    }

    @Test
    fun `cada escritura es un lote de 1 y sus conflictos se traducen`() = runTest {
        ok("""{"revision":8,"applied":[{"id":"$ITEM_ID","version":1,"revision":8}]}""")
        val created = run("PUT", "/vault/items/$ITEM_ID", """{"expectedVersion":null,"formatVersion":1,"nonce":"AAECAwQFBgcICQoL","ciphertext":"-_8"}""", "A")
        val change = sent()["items"]!!.toString()
        assertTrue(change.contains(""""baseVersion":0""") && change.contains(""""ciphertext":"+/8="""))
        assertEquals("created", ApiJson.decodeFromString(WriteResultDto.serializer(), created.body!!).result)

        error(409, "VaultSyncConflict", """{"conflicts":[{"id":"$ITEM_ID","reason":"version_mismatch","currentVersion":3}]}""")
        val conflict = run("DELETE", "/vault/items/$ITEM_ID?expectedVersion=2", bearer = "A").problem()
        assertTrue(sent()["items"]!!.toString().contains(""""deleted":true"""))
        assertEquals("version_conflict", conflict.code)
        assertEquals(3L, conflict.currentVersion)

        error(409, "VaultSyncConflict", """{"conflicts":[{"id":"$ITEM_ID","reason":"not_found","currentVersion":null}]}""")
        assertEquals("not_found", run("DELETE", "/vault/items/$ITEM_ID?expectedVersion=2", bearer = "A").code())
    }

    @Test
    fun `la version del servidor de un elemento borrado sale de since=1 (BR-14)`() = runTest {
        ok("""{"revision":9,"purgedThroughRevision":0,"keyVersion":1,"fullResync":true,"items":[]}""")
        ok("""{"revision":9,"purgedThroughRevision":0,"keyVersion":1,"fullResync":false,"items":[{"id":"$ITEM_ID","version":4,"revision":9,"deleted":true,"formatVersion":1,"updatedAt":"2026-11-18T11:00:00.000Z"}]}""")
        val item = ApiJson.decodeFromString(VaultItemDto.serializer(), run("GET", "/vault/items/$ITEM_ID", bearer = "A").body!!)
        assertEquals(listOf("vault/sync?since=0", "vault/sync?since=1"), calls.map { it.path })
        assertTrue(item.deleted)
        assertEquals(4L, item.version)
    }

    // ---- US3: cuenta (T014) ----

    @Test
    fun `la cuenta toma keyVersion de vault sync y las llaves del ultimo acceso (BR-15)`() = runTest {
        ok(AUTH_OK)
        run("POST", "/auth/login", LOGIN)
        ok(USER)
        ok("""{"revision":3,"purgedThroughRevision":0,"keyVersion":1,"fullResync":true,"items":[]}""")
        val account = ApiJson.decodeFromString(AccountDto.serializer(), run("GET", "/account", bearer = "A").body!!)
        assertEquals(1, account.keyVersion)
        assertEquals("AAECAwQFBgcICQoL", account.protectedVaultKey.nonce)
        assertTrue(account.marketingOptIn)

        // Otro teléfono cambió las llaves: keyVersion del servidor, kdf de prelogin
        ok(USER)
        ok("""{"revision":4,"purgedThroughRevision":0,"keyVersion":2,"fullResync":false,"items":[]}""")
        ok("""{"kdf":{"salt":"AAAAAAAAAAAAAAAAAAAAAA==","memoryKib":65536,"iterations":3,"parallelism":1}}""")
        val newer = ApiJson.decodeFromString(AccountDto.serializer(), run("GET", "/account", bearer = "A").body!!)
        assertEquals("vault/sync?since=3", calls[calls.lastIndex - 1].path)
        assertEquals(2, newer.keyVersion)
    }

    @Test
    fun `reautenticacion y conflicto de llaves`() = runTest {
        val change = """{"reauth":{"authKey":"AQID","totpCode":"123456"},"expectedKeyVersion":1,"newAuthKey":"BAUG","newKdf":$APP_KDF,"newProtectedVaultKey":{"nonce":"AAECAwQFBgcICQoL","ciphertext":"AQID"}}"""
        error(403, "Forbidden")
        assertEquals("reauth_required", run("POST", "/account/master-password", change, "A").code())
        assertEquals("123456", sent()["code"]!!.jsonPrimitive.content)
        assertEquals("1", sent()["expectedKeyVersion"]!!.jsonPrimitive.content)
        error(401, "InvalidSecondFactor")
        assertEquals("reauth_required", run("POST", "/account/master-password", change, "A").code())
        error(409, "KeyVersionMismatch", """{"currentKeyVersion":2}""")
        assertEquals("key_version_conflict", run("POST", "/account/master-password", change, "A").code())
        ok("""{"keyVersion":2,"otherSessionsRevoked":1}""")
        assertEquals("2", run("POST", "/account/master-password", change, "A").body!!.field("keyVersion"))
    }

    @Test
    fun `activar 2 pasos pide la contrasena y lee la clave del otpauthUrl (BR-20)`() = runTest {
        assertEquals("reauth_required", run("POST", "/account/2fa/setup", null, "A").code())
        ok("""{"secret":"JBSWY3DPEHPK3PXP","otpauthUrl":"otpauth://totp/End%20Security:ana%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=End%20Security","qrCodeDataUrl":"data:image/png;base64,x"}""")
        val setup = ApiJson.decodeFromString(
            TwoFactorSetupDto.serializer(),
            run("POST", "/account/2fa/setup", """{"reauth":{"authKey":"AQID"}}""", "A").body!!,
        )
        assertEquals(setOf("authHash"), sent().keys)
        assertEquals("End Security", setup.issuer)
        assertEquals("ana@example.com", setup.accountName)
        assertEquals(6, setup.digits)
        error(409, "Conflict")
        assertEquals("two_factor_already_enabled", run("POST", "/account/2fa/setup", """{"reauth":{"authKey":"AQID"}}""", "A").code())
    }

    @Test
    fun `actividad oculta eventos desconocidos y nunca pasa la IP (BR-21)`() = runTest {
        ok(
            """{"items":[
              {"id":9,"eventType":"2fa.verify","outcome":"success","occurredAt":"2026-11-18T11:00:00.000Z","metadata":{"method":"recovery_code"},"deviceName":"Pixel 8","ip":"203.0.113.9","userAgent":"okhttp"},
              {"id":8,"eventType":"vault.sync_push","outcome":"success","occurredAt":"2026-11-18T10:59:00.000Z","metadata":{},"deviceName":null,"ip":"203.0.113.9","userAgent":"okhttp"},
              {"id":7,"eventType":"auth.login","outcome":"failure","occurredAt":"2026-11-18T10:58:00.000Z","metadata":{"reason":"account_locked"},"deviceName":null,"ip":null,"userAgent":null}
            ],"nextCursor":7}""",
        )
        val response = run("GET", "/activity?limit=50&before=10", bearer = "A")
        assertEquals("audit?limit=50&before=10", calls.single().path)
        val page = ApiJson.decodeFromString(ActivityDto.serializer(), response.body!!)
        assertEquals(listOf("recovery_code_login", "account_locked"), page.events.map { it.eventType })
        assertTrue(page.hasMore)
        assertTrue("203.0.113.9" !in response.body!!)
    }

    @Test
    fun `sesiones sin lastUsedAt usan createdAt`() = runTest {
        ok("""[{"id":"$SESSION_ID","deviceName":"Pixel 8","createdAt":"2026-11-18T10:00:00.000Z","lastUsedAt":null,"expiresAt":"2026-11-25T10:00:00.000Z","current":true}]""")
        val body = run("GET", "/sessions", bearer = "A").body!!
        assertTrue(body.contains(""""lastUsedAt":"2026-11-18T10:00:00.000Z""""))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-18T12:00:00Z")
        const val USER_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        const val SESSION_ID = "9b2c6d1e-1111-4a5b-8c9d-222233334444"
        const val ITEM_ID = "5d1f3a7c-aaaa-4bbb-8ccc-ddddeeeeffff"
        const val CHALLENGE = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln"
        const val APP_KDF = """{"algorithm":"argon2id","memoryKiB":65536,"iterations":3,"parallelism":1,"salt":"AAAAAAAAAAAAAAAAAAAAAA"}"""
        const val REGISTER =
            """{"userId":"$USER_ID","email":"ana@example.com","authKey":"AQID","kdf":$APP_KDF,"protectedVaultKey":{"nonce":"AAECAwQFBgcICQoL","ciphertext":"AQID"},"marketingOptIn":true}"""
        const val LOGIN = """{"email":"ana@example.com","authKey":"AQID","deviceName":"Pixel 8"}"""
        const val USER =
            """{"id":"$USER_ID","email":"ana@example.com","displayName":"","marketingOptIn":true,"emailVerified":true,"twoFactorEnabled":false,"createdAt":"2026-11-18T09:00:00.000Z"}"""
        const val AUTH_OK =
            """{"requiresEmailVerification":false,"requires2FA":false,"tokens":{"tokenType":"Bearer","accessToken":"ACCESS","expiresIn":900,"refreshToken":"$SESSION_ID.c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U"},"user":$USER,"keys":{"kdf":{"salt":"AAAAAAAAAAAAAAAAAAAAAA==","memoryKib":65536,"iterations":3,"parallelism":1},"pvk":{"ciphertext":"AQID","nonce":"AAECAwQFBgcICQoL"},"keyVersion":1}}"""
        const val ITEM =
            """{"id":"$ITEM_ID","version":1,"revision":7,"deleted":false,"nonce":"AAECAwQFBgcICQoL","ciphertext":"AQID","formatVersion":1,"updatedAt":"2026-11-18T11:00:00.000Z"}"""
    }
}
