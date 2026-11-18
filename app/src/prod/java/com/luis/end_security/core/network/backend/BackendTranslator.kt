package com.luis.end_security.core.network.backend

import com.luis.end_security.core.network.ApiJson
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
import com.luis.end_security.core.network.dto.AcceptedDto
import com.luis.end_security.core.network.dto.AccountDto
import com.luis.end_security.core.network.dto.ActivityDto
import com.luis.end_security.core.network.dto.ActivityEventDto
import com.luis.end_security.core.network.dto.AuthResponseDto
import com.luis.end_security.core.network.dto.CodeRequestDto
import com.luis.end_security.core.network.dto.EmailChangeRequestDto
import com.luis.end_security.core.network.dto.EmailRequestDto
import com.luis.end_security.core.network.dto.KdfDto
import com.luis.end_security.core.network.dto.KeyVersionDto
import com.luis.end_security.core.network.dto.LoginRequestDto
import com.luis.end_security.core.network.dto.MasterPasswordRequestDto
import com.luis.end_security.core.network.dto.PreloginResponseDto
import com.luis.end_security.core.network.dto.ProblemDto
import com.luis.end_security.core.network.dto.ProtectedVaultKeyDto
import com.luis.end_security.core.network.dto.PutItemRequestDto
import com.luis.end_security.core.network.dto.ReauthDto
import com.luis.end_security.core.network.dto.ReauthRequestDto
import com.luis.end_security.core.network.dto.RecoveryCodeRequestDto
import com.luis.end_security.core.network.dto.RecoveryCodesDto
import com.luis.end_security.core.network.dto.RefreshRequestDto
import com.luis.end_security.core.network.dto.RegisterRequestDto
import com.luis.end_security.core.network.dto.SessionDto
import com.luis.end_security.core.network.dto.SessionsDto
import com.luis.end_security.core.network.dto.TokensDto
import com.luis.end_security.core.network.dto.TwoFactorSetupDto
import com.luis.end_security.core.network.dto.TwoFactorVerifyRequestDto
import com.luis.end_security.core.network.dto.VaultChangesDto
import com.luis.end_security.core.network.dto.VaultItemDto
import com.luis.end_security.core.network.dto.VerifyEmailRequestDto
import com.luis.end_security.core.network.dto.WriteResultDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Una petición al backend real, relativa a `/api/`. El cuerpo nunca se imprime. */
class BackendCall(val method: String, val path: String, val body: String?, val bearer: String?) {
    override fun toString(): String = "BackendCall($method ${path.substringBefore('?')})"
}

class BackendReply(val status: Int, val body: String?, val retryAfterSeconds: Int? = null) {
    override fun toString(): String = "BackendReply($status)"
}

/**
 * Traduce el contrato de la app (`docs/api`, lo que arman `ApiClient` y las `*ApiImpl`) al backend real y de vuelta
 * (specs/001-adaptador-backend-real/contracts/translation.md). Es puro salvo [send]: las pruebas le pasan respuestas reales.
 *
 * Guarda en memoria (data-model.md) los desafíos de correo (BR-08), las llaves del último acceso y el cursor de la bóveda
 * (BR-15). Nada se escribe en disco ni en logs.
 */
class BackendTranslator(
    private val deviceName: () -> String,
    private val now: () -> Instant = Instant::now,
) {
    private val emailChallenges = ConcurrentHashMap<String, String>()
    /** Intentos fallidos del código vigente, para `attemptsRemaining` (el backend no lo devuelve). */
    private val codeFailures = ConcurrentHashMap<String, Int>()

    @Volatile
    private var knownKeys: KnownKeys? = null

    @Volatile
    private var vaultCursor: Long = 0

    suspend fun translate(request: HttpRequest, send: suspend (BackendCall) -> BackendReply): HttpResponse {
        val route = request.path.substringBefore('?')
        val query = parseQuery(request.path.substringAfter('?', ""))
        val ctx = Ctx(request, send)
        return when {
            request.method == "POST" && route == "/auth/prelogin" -> ctx.prelogin()
            request.method == "POST" && route == "/auth/register" -> ctx.register()
            request.method == "POST" && route == "/auth/email/verify" -> ctx.verifyEmail()
            request.method == "POST" && route == "/auth/email/resend" -> ctx.resendEmail()
            request.method == "POST" && route == "/auth/login" -> ctx.login()
            request.method == "POST" && route == "/auth/2fa/verify" -> ctx.twoFactorLogin(recovery = false)
            request.method == "POST" && route == "/auth/2fa/recovery" -> ctx.twoFactorLogin(recovery = true)
            request.method == "POST" && route == "/auth/refresh" -> ctx.refresh()
            request.method == "POST" && route == "/auth/logout" -> ctx.logout()
            request.method == "GET" && route == "/account" -> ctx.getAccount()
            request.method == "PATCH" && route == "/account" -> ctx.updateAccount()
            request.method == "POST" && route == "/account/email" -> ctx.requestEmailChange()
            request.method == "POST" && route == "/account/email/verify" -> ctx.confirmEmailChange()
            request.method == "POST" && route == "/account/master-password" -> ctx.changeMasterPassword()
            request.method == "POST" && route == "/account/delete" -> ctx.deleteAccount()
            request.method == "POST" && route == "/account/2fa/setup" -> ctx.twoFactorSetup()
            request.method == "POST" && route == "/account/2fa/confirm" -> ctx.twoFactorConfirm()
            request.method == "POST" && route == "/account/2fa/disable" -> ctx.twoFactorSensitive("auth/2fa/disable")
            request.method == "POST" && route == "/account/2fa/recovery-codes" -> ctx.twoFactorSensitive("auth/2fa/recovery-codes")
            request.method == "GET" && route == "/sessions" -> ctx.listSessions()
            request.method == "DELETE" && route.startsWith("/sessions/") -> ctx.revokeSession(route.removePrefix("/sessions/"))
            request.method == "GET" && route == "/activity" -> ctx.activity(query)
            request.method == "GET" && route == "/vault/items" -> ctx.vaultChanges(query)
            request.method == "PUT" && route.startsWith("/vault/items/") -> ctx.putItem(route.removePrefix("/vault/items/"))
            request.method == "DELETE" && route.startsWith("/vault/items/") ->
                ctx.deleteItem(route.removePrefix("/vault/items/"), query["expectedVersion"]?.toLongOrNull())
            request.method == "GET" && route.startsWith("/vault/items/") -> ctx.getItem(route.removePrefix("/vault/items/"))
            else -> problem(404, "not_found")
        }
    }

    // ---- Una petición del contrato ----

    private inner class Ctx(val request: HttpRequest, val send: suspend (BackendCall) -> BackendReply) {

        private fun <T> body(serializer: KSerializer<T>): T = ApiJson.decodeFromString(serializer, request.body.orEmpty())

        private suspend fun <T> call(method: String, path: String, payload: T?, serializer: KSerializer<T>?): BackendReply =
            send(
                BackendCall(
                    method, path,
                    if (payload == null || serializer == null) null else ApiJson.encodeToString(serializer, payload),
                    request.bearerToken,
                ),
            )

        private suspend fun get(path: String) = call<Unit>("GET", path, null, null)

        // -- Acceso (BR-05, BR-08, BR-09) --

        suspend fun prelogin(): HttpResponse {
            val reply = call("POST", "auth/prelogin", body(EmailRequestDto.serializer()), EmailRequestDto.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Public)
            return ok(PreloginResponseDto.serializer(), PreloginResponseDto(data(reply, BePrelogin.serializer()).kdf.toApp()))
        }

        suspend fun register(): HttpResponse {
            val dto = body(RegisterRequestDto.serializer())
            val payload = BeRegister(
                userId = dto.userId,
                email = dto.email,
                displayName = dto.displayName,
                authHash = std(dto.authKey),
                kdf = dto.kdf.toBackend(),
                pvk = dto.protectedVaultKey.toBackend(),
                deviceName = deviceName().take(DEVICE_NAME_MAX),
                marketingOptIn = dto.marketingOptIn,
            )
            val reply = call("POST", "auth/register", payload, BeRegister.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Public)
            rememberChallenge(dto.email, data(reply, BeChallenge.serializer()).challengeToken)
            return accepted()
        }

        suspend fun verifyEmail(): HttpResponse {
            val dto = body(VerifyEmailRequestDto.serializer())
            val key = normalizeEmail(dto.email)
            val token = emailChallenges[key] ?: return problem(401, "challenge_expired")
            val reply = call(
                "POST", "auth/email/verify/confirm", BeChallengeCode(token, dto.code), BeChallengeCode.serializer(),
            )
            if (!reply.ok) {
                // Correo ya verificado (409): no hay código que confirmar; que inicie sesión.
                if (reply.status == 409) return problem(401, "challenge_expired")
                return errorOf(reply, Kind.Challenge, failuresKey = "verify:$key")
            }
            emailChallenges.remove(key)
            codeFailures.remove("verify:$key")
            val auth = data(reply, BeAuth.serializer())
            return authenticated(auth) ?: problem(500, "server_error")
        }

        suspend fun resendEmail(): HttpResponse {
            val key = normalizeEmail(body(EmailRequestDto.serializer()).email)
            val token = emailChallenges[key] ?: return problem(401, "challenge_expired")
            val reply = call("POST", "auth/email/verify/resend", BeChallenge(token), BeChallenge.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Challenge)
            codeFailures.remove("verify:$key")
            return accepted()
        }

        suspend fun login(): HttpResponse {
            val dto = body(LoginRequestDto.serializer())
            val reply = call("POST", "auth/login", BeLogin(dto.email, std(dto.authKey), dto.deviceName.take(DEVICE_NAME_MAX)), BeLogin.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Login)
            val auth = data(reply, BeAuth.serializer())
            return when {
                auth.requiresEmailVerification -> {
                    // La app ya lleva a la pantalla del código con email_not_verified; el desafío queda para confirmarlo.
                    rememberChallenge(dto.email, requireNotNull(auth.challengeToken))
                    problem(403, "email_not_verified")
                }
                auth.requires2FA -> ok(
                    AuthResponseDto.serializer(),
                    AuthResponseDto(
                        status = "two_factor_required",
                        challengeToken = requireNotNull(auth.challengeToken),
                        challengeExpiresIn = TWO_FACTOR_CHALLENGE_SECONDS,
                    ),
                )
                else -> authenticated(auth) ?: problem(500, "server_error")
            }
        }

        suspend fun twoFactorLogin(recovery: Boolean): HttpResponse {
            val payload = if (recovery) {
                val dto = body(RecoveryCodeRequestDto.serializer())
                BeTwoFactorLogin(dto.challengeToken, recoveryCode = dto.recoveryCode)
            } else {
                val dto = body(TwoFactorVerifyRequestDto.serializer())
                BeTwoFactorLogin(dto.challengeToken, code = dto.code)
            }
            val reply = call("POST", "auth/2fa/verify", payload, BeTwoFactorLogin.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Challenge)
            val auth = data(reply, BeAuth.serializer())
            if (!recovery) return authenticated(auth) ?: problem(500, "server_error")
            // El backend no devuelve cuántos quedan (BR-09): se pregunta con el token recién emitido.
            val token = auth.tokens?.accessToken ?: return problem(500, "server_error")
            val status = send(BackendCall("GET", "auth/2fa", null, token))
            // shortcut: si esa consulta falla, se asume que quedan muchos y no se muestra el aviso; upgrade si el backend lo devuelve.
            val remaining = if (status.ok) data(status, BeTwoFactorStatus.serializer()).recoveryCodesRemaining else RECOVERY_CODES
            return authenticated(auth, remaining) ?: problem(500, "server_error")
        }

        suspend fun refresh(): HttpResponse {
            val reply = call("POST", "auth/refresh", BeRefresh(body(RefreshRequestDto.serializer()).refreshToken), BeRefresh.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Refresh)
            return ok(TokensDto.serializer(), data(reply, BeTokens.serializer()).toApp())
        }

        suspend fun logout(): HttpResponse {
            val reply = call<Unit>("POST", "auth/logout", null, null)
            if (!reply.ok) return errorOf(reply, Kind.Session)
            forgetAccount()
            return noContent()
        }

        // -- Cuenta (BR-15, BR-16, BR-17, BR-18, BR-20) --

        suspend fun getAccount(): HttpResponse {
            val reply = get("users/me")
            if (!reply.ok) return errorOf(reply, Kind.Session)
            return accountOf(data(reply, BeUser.serializer()))
        }

        suspend fun updateAccount(): HttpResponse {
            // Mismos nombres de campo; `displayName: null` no existe en el backend y responde 400 validation_failed.
            val reply = send(BackendCall("PATCH", "users/me", request.body, request.bearerToken))
            if (!reply.ok) return errorOf(reply, Kind.Session)
            return accountOf(data(reply, BeUser.serializer()))
        }

        suspend fun requestEmailChange(): HttpResponse {
            val dto = body(EmailChangeRequestDto.serializer())
            val payload = BeEmailChange(dto.newEmail, std(dto.reauth.authKey), dto.reauth.totpCode)
            val reply = call("POST", "auth/email/change", payload, BeEmailChange.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Reauth)
            codeFailures.remove(EMAIL_CHANGE)
            return accepted()
        }

        suspend fun confirmEmailChange(): HttpResponse {
            val reply = call("POST", "auth/email/change/confirm", BeCode(body(CodeRequestDto.serializer()).code), BeCode.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Session, failuresKey = EMAIL_CHANGE)
            codeFailures.remove(EMAIL_CHANGE)
            return accountOf(data(reply, BeUser.serializer()))
        }

        suspend fun changeMasterPassword(): HttpResponse {
            val dto = body(MasterPasswordRequestDto.serializer())
            val payload = BePasswordChange(
                currentAuthHash = std(dto.reauth.authKey),
                newAuthHash = std(dto.newAuthKey),
                kdf = dto.newKdf.toBackend(),
                pvk = dto.newProtectedVaultKey.toBackend(),
                code = dto.reauth.totpCode,
                expectedKeyVersion = dto.expectedKeyVersion,
            )
            val reply = call("POST", "auth/password", payload, BePasswordChange.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Reauth)
            val keyVersion = data(reply, BeKeyVersion.serializer()).keyVersion
            knownKeys = knownKeys?.copy(kdf = dto.newKdf, pvk = dto.newProtectedVaultKey, keyVersion = keyVersion)
            return ok(KeyVersionDto.serializer(), KeyVersionDto(keyVersion))
        }

        suspend fun deleteAccount(): HttpResponse {
            val reauth = body(ReauthRequestDto.serializer()).reauth
            val reply = call("DELETE", "users/me", reauth.toBackend(), BeReauth.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Reauth)
            forgetAccount()
            return noContent()
        }

        suspend fun twoFactorSetup(): HttpResponse {
            // BR-20: el backend pide la contraseña maestra; la app la manda desde la hoja de reautenticación.
            val reauth = request.body?.let { body(ReauthRequestDto.serializer()).reauth } ?: return problem(403, "reauth_required")
            val reply = call("POST", "auth/2fa/setup", BeAuthHash(std(reauth.authKey)), BeAuthHash.serializer())
            if (!reply.ok) {
                if (reply.status == 409) return problem(409, "two_factor_already_enabled")
                return errorOf(reply, Kind.Reauth)
            }
            val setup = data(reply, BeTwoFactorSetup.serializer())
            return ok(TwoFactorSetupDto.serializer(), otpAuthToSetup(setup.secret, setup.otpauthUrl))
        }

        suspend fun twoFactorConfirm(): HttpResponse {
            val reply = call("POST", "auth/2fa/activate", BeCode(body(CodeRequestDto.serializer()).code), BeCode.serializer())
            if (!reply.ok) return errorOf(reply, Kind.SessionCode)
            return ok(RecoveryCodesDto.serializer(), RecoveryCodesDto(data(reply, BeRecoveryCodes.serializer()).recoveryCodes))
        }

        suspend fun twoFactorSensitive(path: String): HttpResponse {
            val reauth = body(ReauthRequestDto.serializer()).reauth
            val reply = call("POST", path, reauth.toBackend(), BeReauth.serializer())
            if (!reply.ok) return errorOf(reply, Kind.Reauth)
            return if (path.endsWith("disable")) {
                noContent()
            } else {
                ok(RecoveryCodesDto.serializer(), RecoveryCodesDto(data(reply, BeRecoveryCodes.serializer()).recoveryCodes))
            }
        }

        /** La cuenta del contrato: `/users/me` + `keyVersion` de `/vault/sync` + llaves conocidas (BR-15, research R6). */
        private suspend fun accountOf(user: BeUser): HttpResponse {
            val sync = get("vault/sync?since=$vaultCursor")
            if (!sync.ok) return errorOf(sync, Kind.Session)
            val vault = data(sync, BeVaultSync.serializer())
            vaultCursor = vault.revision
            val known = knownKeys?.takeIf { it.userId == user.id && it.keyVersion == vault.keyVersion }
            val kdf = known?.kdf ?: run {
                val prelogin = call("POST", "auth/prelogin", EmailRequestDto(user.email), EmailRequestDto.serializer())
                if (!prelogin.ok) return errorOf(prelogin, Kind.Public)
                data(prelogin, BePrelogin.serializer()).kdf.toApp()
            }
            // shortcut: sin llaves recordadas (la app se reabrió sin iniciar sesión) la pvk va vacía; hoy ningún usuario de
            // getAccount() la lee (research R6). Upgrade: que /users/me devuelva las llaves.
            val pvk = known?.pvk ?: ProtectedVaultKeyDto(Base64Url.encode(ByteArray(NONCE_BYTES)), "")
            return ok(AccountDto.serializer(), user.toApp(vault.keyVersion, kdf, pvk))
        }

        // -- Sesiones y actividad (BR-21) --

        suspend fun listSessions(): HttpResponse {
            val reply = get("auth/sessions")
            if (!reply.ok) return errorOf(reply, Kind.Session)
            val sessions = data(reply, kotlinx.serialization.builtins.ListSerializer(BeSession.serializer()))
            return ok(
                SessionsDto.serializer(),
                SessionsDto(sessions.map { SessionDto(it.id, it.deviceName, it.createdAt, it.lastUsedAt ?: it.createdAt, it.current) }),
            )
        }

        suspend fun revokeSession(id: String): HttpResponse {
            val reply = call<Unit>("DELETE", "auth/sessions/$id", null, null)
            return if (reply.ok) noContent() else errorOf(reply, Kind.Session)
        }

        suspend fun activity(query: Map<String, String>): HttpResponse {
            val params = listOfNotNull(query["limit"]?.let { "limit=$it" }, query["before"]?.let { "before=$it" }).joinToString("&")
            val reply = get(if (params.isEmpty()) "audit" else "audit?$params")
            if (!reply.ok) return errorOf(reply, Kind.Session)
            val page = data(reply, BeAuditPage.serializer())
            val events = page.items.mapNotNull { item ->
                activityKind(item)?.let { ActivityEventDto(item.id, item.occurredAt, it, item.outcome, item.deviceName) }
            }
            return ok(ActivityDto.serializer(), ActivityDto(events, hasMore = page.nextCursor != null))
        }

        // -- Bóveda (BR-12, BR-13, BR-14) --

        suspend fun vaultChanges(query: Map<String, String>): HttpResponse {
            val since = query["since"]?.toLongOrNull() ?: 0
            val reply = get("vault/sync?since=$since")
            if (!reply.ok) return errorOf(reply, Kind.Session)
            val sync = data(reply, BeVaultSync.serializer())
            vaultCursor = sync.revision
            if (sync.fullResync && since > 0) return problem(410, "cursor_expired")
            return ok(
                VaultChangesDto.serializer(),
                VaultChangesDto(sync.items.map { it.toApp() }, nextSince = sync.revision, hasMore = false, currentRevision = sync.revision),
            )
        }

        suspend fun putItem(id: String): HttpResponse {
            val dto = body(PutItemRequestDto.serializer())
            val change = BeVaultChange(
                id = id,
                baseVersion = dto.expectedVersion ?: 0,
                nonce = std(dto.nonce),
                ciphertext = std(dto.ciphertext),
                formatVersion = dto.formatVersion,
            )
            return push(change, if (dto.expectedVersion == null) "created" else "updated")
        }

        suspend fun deleteItem(id: String, expectedVersion: Long?): HttpResponse {
            expectedVersion ?: return problem(400, "validation_failed")
            return push(BeVaultChange(id = id, baseVersion = expectedVersion, deleted = true), "deleted")
        }

        /** Un elemento por lote (D-4 resuelto con lotes de 1, BR-13): cada conflicto es de ese elemento. */
        private suspend fun push(change: BeVaultChange, result: String): HttpResponse {
            val reply = call("POST", "vault/sync", BeVaultPush(listOf(change)), BeVaultPush.serializer())
            if (!reply.ok) {
                if (reply.status == 409) return vaultConflict(reply)
                return errorOf(reply, Kind.Session)
            }
            val applied = data(reply, BeVaultPushed.serializer()).applied.single()
            return ok(WriteResultDto.serializer(), WriteResultDto(result, applied.version, applied.revision))
        }

        /** BR-14: no hay `GET /vault/items/{id}`; vigentes con `since=0` y lápidas con `since=1` (un borrado tiene revisión ≥ 2). */
        suspend fun getItem(id: String): HttpResponse {
            for (since in listOf(0, 1)) {
                val reply = get("vault/sync?since=$since")
                if (!reply.ok) return errorOf(reply, Kind.Session)
                val item = data(reply, BeVaultSync.serializer()).items.firstOrNull { it.id == id }
                if (item != null) return ok(VaultItemDto.serializer(), item.toApp())
            }
            return problem(404, "not_found")
        }

        // -- Respuestas --

        private fun authenticated(auth: BeAuth, recoveryCodesRemaining: Int? = null): HttpResponse? {
            val tokens = auth.tokens ?: return null
            val user = auth.user ?: return null
            val keys = auth.keys ?: return null
            val kdf = keys.kdf.toApp()
            val pvk = keys.pvk.toApp()
            knownKeys = KnownKeys(user.id, kdf, pvk, keys.keyVersion)
            return ok(
                AuthResponseDto.serializer(),
                AuthResponseDto(
                    status = "authenticated",
                    tokens = tokens.toApp(),
                    account = user.toApp(keys.keyVersion, kdf, pvk),
                    recoveryCodesRemaining = recoveryCodesRemaining,
                ),
            )
        }

        /** Error del backend → `code` del contrato (BR-03; tabla en contracts/translation.md). */
        private fun errorOf(reply: BackendReply, kind: Kind, failuresKey: String? = null): HttpResponse {
            val error = parseError(reply)
            val name = error?.error.orEmpty()
            val details = error?.details
            return when (reply.status) {
                429 -> problem(
                    429, "rate_limited",
                    retryAfterSeconds = details?.get("retryAfterSeconds")?.jsonPrimitive?.intOrNull ?: reply.retryAfterSeconds,
                )
                400 -> when (name) {
                    "InvalidCode" -> invalidCode(failuresKey)
                    "SecondFactorRequired" -> problem(403, "reauth_required")
                    else -> problem(400, "validation_failed")
                }
                401 -> when (kind) {
                    Kind.Login -> problem(401, "invalid_credentials")
                    Kind.Challenge -> if (name == "InvalidSecondFactor") invalidCode(failuresKey) else problem(401, "challenge_expired")
                    Kind.Refresh -> problem(401, "session_revoked")
                    Kind.Reauth -> if (name == "InvalidSecondFactor") problem(403, "reauth_required") else problem(401, "token_expired")
                    Kind.SessionCode -> if (name == "InvalidSecondFactor") invalidCode(failuresKey) else problem(401, "token_expired")
                    Kind.Session -> problem(401, "token_expired")
                    Kind.Public -> problem(401, "invalid_credentials")
                }
                403 -> problem(403, "reauth_required")
                404 -> problem(404, "not_found")
                409 -> if (name == "KeyVersionMismatch") problem(409, "key_version_conflict") else problem(500, "server_error")
                else -> problem(500, "server_error")
            }
        }

        private fun invalidCode(failuresKey: String?): HttpResponse {
            val failures = failuresKey?.let { key -> codeFailures.merge(key, 1, Int::plus) }
            val remaining = failures?.let { (CODE_ATTEMPTS - it).coerceAtLeast(0) }
            return problem(400, "invalid_code", attemptsRemaining = remaining)
        }

        private fun vaultConflict(reply: BackendReply): HttpResponse {
            val conflict = parseError(reply)?.details?.get("conflicts")?.jsonArray?.firstOrNull()?.jsonObject
                ?: return problem(500, "server_error")
            if (conflict["reason"]?.jsonPrimitive?.contentOrNull == "not_found") return problem(404, "not_found")
            val current = conflict["currentVersion"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull ?: 0
            return problem(409, "version_conflict", currentVersion = current)
        }
    }

    private enum class Kind { Public, Login, Challenge, Refresh, Session, SessionCode, Reauth }

    // ---- Estado en memoria ----

    private data class KnownKeys(val userId: String, val kdf: KdfDto, val pvk: ProtectedVaultKeyDto, val keyVersion: Int) {
        override fun toString(): String = "KnownKeys(keyVersion=$keyVersion, ***)"
    }

    private fun rememberChallenge(email: String, token: String) {
        val key = normalizeEmail(email)
        emailChallenges[key] = token
        codeFailures.remove("verify:$key")
    }

    private fun forgetAccount() {
        knownKeys = null
        vaultCursor = 0
        codeFailures.remove(EMAIL_CHANGE)
    }

    // ---- Conversión de formatos (BR-04, BR-05) ----

    private fun BeKdf.toApp() = KdfDto("argon2id", memoryKib, iterations, parallelism, url(salt))

    private fun KdfDto.toBackend() = BeKdf(std(salt), memoryKiB, iterations, parallelism)

    private fun BePvk.toApp() = ProtectedVaultKeyDto(nonce = url(nonce), ciphertext = url(ciphertext))

    private fun ProtectedVaultKeyDto.toBackend() = BePvk(ciphertext = std(ciphertext), nonce = std(nonce))

    private fun ReauthDto.toBackend() = BeReauth(std(authKey), totpCode)

    private fun BeTokens.toApp() = TokensDto(
        accessToken = accessToken,
        accessTokenExpiresIn = expiresIn,
        refreshToken = refreshToken,
        // shortcut: el backend no devuelve el vencimiento; se usa su valor por defecto (7 días desde esta renovación). Upgrade si cambia
        // JWT_REFRESH_EXPIRES_IN en el servidor o si el backend agrega el vencimiento a la respuesta.
        refreshTokenExpiresAt = now().plus(REFRESH_TTL).toString(),
        // El refresh token es `<sessionId>.<secreto>` (TokenService del backend).
        sessionId = refreshToken.substringBefore('.'),
    )

    private fun BeUser.toApp(keyVersion: Int, kdf: KdfDto, pvk: ProtectedVaultKeyDto) = AccountDto(
        userId = id,
        email = email,
        emailVerified = emailVerified,
        displayName = displayName.ifEmpty { null },
        keyVersion = keyVersion,
        kdf = kdf,
        protectedVaultKey = pvk,
        twoFactorEnabled = twoFactorEnabled,
        marketingOptIn = marketingOptIn,
    )

    private fun BeVaultItem.toApp() = VaultItemDto(
        id = id,
        version = version,
        revision = revision,
        formatVersion = formatVersion,
        deleted = deleted,
        nonce = nonce?.let(::url),
        ciphertext = ciphertext?.let(::url),
        updatedAt = updatedAt,
    )

    companion object {
        private const val TWO_FACTOR_CHALLENGE_SECONDS = 300
        private const val RECOVERY_CODES = 10
        private const val CODE_ATTEMPTS = 5
        private const val NONCE_BYTES = 12
        /** `@MaxLength(64)` del backend; la app admite 100. */
        private const val DEVICE_NAME_MAX = 64
        private const val EMAIL_CHANGE = "email-change"
        private val REFRESH_TTL: Duration = Duration.ofDays(7)

        private val BackendReply.ok get() = status in 200..299

        /** Base64url sin relleno (la app) → Base64 estándar (el backend). */
        fun std(url: String): String = Base64.getEncoder().encodeToString(Base64Url.decode(url))

        /** Base64 estándar (el backend) → Base64url sin relleno (la app). */
        fun url(std: String): String = Base64Url.encode(Base64.getDecoder().decode(std))

        /** Igual que el servidor (DEC-63): recortar, minúsculas y NFC. */
        fun normalizeEmail(email: String): String = Normalizer.normalize(email.trim().lowercase(), Normalizer.Form.NFC)

        /** Desenvuelve `{success, data, timestamp}` (BR-02). */
        private fun <T> data(reply: BackendReply, serializer: KSerializer<T>): T {
            val data: JsonElement = ApiJson.parseToJsonElement(reply.body.orEmpty()).jsonObject["data"] ?: JsonNull
            return ApiJson.decodeFromJsonElement(serializer, data)
        }

        private fun parseError(reply: BackendReply): BeError? =
            runCatching { ApiJson.decodeFromString(BeError.serializer(), reply.body.orEmpty()) }.getOrNull()

        private fun <T> ok(serializer: KSerializer<T>, value: T) = HttpResponse(200, ApiJson.encodeToString(serializer, value))

        private fun accepted() = HttpResponse(202, ApiJson.encodeToString(AcceptedDto.serializer(), AcceptedDto("accepted")))

        private fun noContent() = HttpResponse(204, null)

        /** *problem+json* del contrato; el `title` es el `code` (la app nunca lo muestra). */
        fun problem(
            status: Int,
            code: String,
            retryAfterSeconds: Int? = null,
            attemptsRemaining: Int? = null,
            currentVersion: Long? = null,
        ): HttpResponse = HttpResponse(
            status,
            ApiJson.encodeToString(
                ProblemDto.serializer(),
                ProblemDto("about:blank", code, status, code, retryAfterSeconds, attemptsRemaining, currentVersion, null),
            ),
            retryAfterSeconds,
        )

        private fun parseQuery(query: String): Map<String, String> = query.split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

        /** BR-21: evento del backend → `ActivityKind.wire` de la app; `null` = se oculta. Sin IP ni agente. */
        internal fun activityKind(item: BeAuditItem): String? {
            fun meta(key: String) = item.metadata?.get(key)?.jsonPrimitive?.contentOrNull
            return when (item.eventType) {
                "auth.register" -> "account_registered".takeIf { item.outcome == "success" }
                "email.verify_request" -> if (meta("purpose") == "change_email") "email_change_requested" else "email_verification_sent"
                "email.verify_confirm" -> "email_verified"
                "auth.login" -> if (meta("reason") == "account_locked") "account_locked" else "login"
                "2fa.verify" -> if (meta("method") == "recovery_code") "recovery_code_login" else "totp_verification"
                "auth.refresh_reuse" -> "token_reuse_detected"
                "auth.logout" -> "logout"
                "auth.session_revoke", "auth.sessions_revoke_others" -> "session_revoked"
                "account.password_change" -> "master_password_changed"
                "account.email_change" -> "email_changed"
                "2fa.activate" -> "two_factor_enabled"
                "2fa.disable" -> "two_factor_disabled"
                "2fa.recovery_regenerate" -> "recovery_codes_regenerated"
                "account.delete" -> "account_deleted"
                else -> null
            }
        }

        /** BR-20: los datos de la clave salen del `otpauth://totp/Emisor:cuenta?secret=…&issuer=…` del servidor. */
        internal fun otpAuthToSetup(secret: String, otpauthUrl: String): TwoFactorSetupDto {
            val uri = URI(otpauthUrl)
            val label = uri.path.removePrefix("/")
            val params = parseQuery(uri.rawQuery.orEmpty())
            val issuer = params["issuer"] ?: label.substringBefore(':', "End Security")
            return TwoFactorSetupDto(
                secret = secret,
                issuer = issuer,
                accountName = label.substringAfter(':'),
                algorithm = params["algorithm"] ?: "SHA1",
                digits = params["digits"]?.toIntOrNull() ?: 6,
                periodSeconds = params["period"]?.toIntOrNull() ?: 30,
            )
        }
    }
}
