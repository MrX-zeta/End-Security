package com.luis.end_security.core.network.fake

import com.luis.end_security.core.crypto.Argon2idKdf
import com.luis.end_security.core.crypto.Base32
import com.luis.end_security.core.crypto.BackupCodeAlphabet
import com.luis.end_security.core.crypto.Hashes
import com.luis.end_security.core.crypto.KdfParams
import com.luis.end_security.core.crypto.RandomBytes
import com.luis.end_security.core.crypto.Totp
import com.luis.end_security.core.network.ApiJson
import com.luis.end_security.core.network.Base64Url
import com.luis.end_security.core.network.HttpRequest
import com.luis.end_security.core.network.HttpResponse
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
import com.luis.end_security.core.network.dto.UpdateAccountRequestDto
import com.luis.end_security.core.network.dto.VaultChangesDto
import com.luis.end_security.core.network.dto.VaultItemDto
import com.luis.end_security.core.network.dto.VerifyEmailRequestDto
import com.luis.end_security.core.network.dto.WriteResultDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Lógica del backend falso: responde **exactamente** como el contrato `docs/api` (DEC-61), con sus
 * reglas de seguridad (README, «Reglas de seguridad»). Recibe y devuelve JSON, así que también
 * prueba los DTO. No es concurrente: [handle] se llama de uno en uno.
 *
 * @param now reloj de pared en epoch ms (inyectado para las pruebas).
 * @param onCodeIssued solo en depuración: el código de verificación en claro, para mostrarlo en la
 *   pantalla. Nunca se guarda en el estado ni se registra en logcat.
 */
class FakeServer(
    private val state: FakeState,
    private val now: () -> Long,
    private val onCodeIssued: (email: String, code: String) -> Unit = { _, _ -> },
) {

    private class Fail(val response: HttpResponse) : Exception()

    fun handle(request: HttpRequest): HttpResponse = try {
        route(request)
    } catch (fail: Fail) {
        fail.response
    } catch (_: SerializationException) {
        problem(400, "validation_failed")
    } catch (_: IllegalArgumentException) {
        problem(400, "validation_failed")
    }

    private fun route(request: HttpRequest): HttpResponse {
        val path = request.path.substringBefore('?')
        val query = parseQuery(request.path.substringAfter('?', ""))
        val body = request.body
        return when {
            request.method == "POST" && path == "/auth/prelogin" -> prelogin(parse(body, EmailRequestDto.serializer()))
            request.method == "POST" && path == "/auth/register" -> register(parse(body, RegisterRequestDto.serializer()))
            request.method == "POST" && path == "/auth/email/verify" -> verifyEmail(parse(body, VerifyEmailRequestDto.serializer()))
            request.method == "POST" && path == "/auth/email/resend" -> resend(parse(body, EmailRequestDto.serializer()))
            request.method == "POST" && path == "/auth/login" -> login(parse(body, LoginRequestDto.serializer()))
            request.method == "POST" && path == "/auth/2fa/verify" -> verifyTotp(parse(body, TwoFactorVerifyRequestDto.serializer()))
            request.method == "POST" && path == "/auth/2fa/recovery" -> verifyRecovery(parse(body, RecoveryCodeRequestDto.serializer()))
            request.method == "POST" && path == "/auth/refresh" -> refresh(parse(body, RefreshRequestDto.serializer()))
            request.method == "POST" && path == "/auth/logout" -> withSession(request) { session, user -> logout(session, user) }
            request.method == "GET" && path == "/vault/items" -> withSession(request) { _, user -> listItems(user, query) }
            path.startsWith("/vault/items/") -> withSession(request) { _, user ->
                val id = canonicalUuid(path.removePrefix("/vault/items/"))
                when (request.method) {
                    "PUT" -> putItem(user, id, parse(body, PutItemRequestDto.serializer()))
                    "DELETE" -> deleteItem(user, id, query["expectedVersion"]?.toLongOrNull() ?: throw IllegalArgumentException())
                    else -> problem(404, "not_found")
                }
            }
            path == "/account" && request.method == "GET" -> withSession(request) { _, user -> ok(AccountDto.serializer(), user.toDto()) }
            path == "/account" && request.method == "PATCH" -> withSession(request) { _, user ->
                updateAccount(user, parse(body, UpdateAccountRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/email" -> withSession(request) { _, user ->
                requestEmailChange(user, parse(body, EmailChangeRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/email/verify" -> withSession(request) { _, user ->
                confirmEmailChange(user, parse(body, CodeRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/master-password" -> withSession(request) { session, user ->
                changeMasterPassword(session, user, parse(body, MasterPasswordRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/delete" -> withSession(request) { session, user ->
                deleteAccount(session, user, parse(body, ReauthRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/2fa/setup" -> withSession(request) { _, user -> startTwoFactor(user) }
            request.method == "POST" && path == "/account/2fa/confirm" -> withSession(request) { session, user ->
                confirmTwoFactor(session, user, parse(body, CodeRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/2fa/disable" -> withSession(request) { session, user ->
                disableTwoFactor(session, user, parse(body, ReauthRequestDto.serializer()))
            }
            request.method == "POST" && path == "/account/2fa/recovery-codes" -> withSession(request) { session, user ->
                regenerateRecoveryCodes(session, user, parse(body, ReauthRequestDto.serializer()))
            }
            request.method == "GET" && path == "/sessions" -> withSession(request) { session, user -> listSessions(session, user) }
            request.method == "DELETE" && path.startsWith("/sessions/") -> withSession(request) { session, user ->
                revokeSession(session, user, canonicalUuid(path.removePrefix("/sessions/")))
            }
            request.method == "GET" && path == "/activity" -> withSession(request) { _, user -> listActivity(user, query) }
            else -> problem(404, "not_found")
        }
    }

    // --- EP-01…EP-04: prelogin, registro y correo ---

    private fun prelogin(dto: EmailRequestDto): HttpResponse {
        val email = normalizeEmail(dto.email)
        val user = activeUserByEmail(email)
        val kdf = user?.kdf?.toDto() ?: KdfDto(
            algorithm = "argon2id",
            memoryKiB = KdfParams.DEFAULT.memoryKiB,
            iterations = KdfParams.DEFAULT.iterations,
            parallelism = KdfParams.DEFAULT.parallelism,
            // DEC-07: sal falsa determinista, HMAC truncado a 16 B.
            salt = Base64Url.encode(Hashes.hmacSha256(b64(state.preloginSecret), email.toByteArray()).copyOf(SALT_BYTES)),
        )
        return ok(PreloginResponseDto.serializer(), PreloginResponseDto(kdf))
    }

    private fun register(dto: RegisterRequestDto): HttpResponse {
        val userId = canonicalUuid(dto.userId)
        val email = normalizeEmail(dto.email)
        dto.displayName?.let { require(it.length in 1..50) }
        val authKey = bytes(dto.authKey, 32)
        val kdf = validKdf(dto.kdf)
        validPvk(dto.protectedVaultKey)
        // Siempre 202: si el correo o el userId ya existen no se crea nada (DEC-13).
        if (activeUserByEmail(email) != null || state.users.containsKey(userId)) return accepted()
        val authSalt = RandomBytes.next(SALT_BYTES)
        val user = FakeUser(
            id = userId,
            email = email,
            emailVerified = false,
            displayName = dto.displayName,
            authSalt = Base64Url.encode(authSalt),
            authHash = Base64Url.encode(Argon2idKdf.serverAuthHash(authKey, authSalt)),
            kdf = FakeKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism, kdf.salt),
            pvkNonce = dto.protectedVaultKey.nonce,
            pvkCiphertext = dto.protectedVaultKey.ciphertext,
        )
        if (FakeBackendDebug.isTwoFactorDebugEmail(email)) {
            user.twoFactor = FakeTwoFactor(
                secret = FakeBackendDebug.TOTP_SECRET,
                enabled = true,
                recoveryCodeHashes = FakeBackendDebug.RECOVERY_CODES.map { hashCode(it) }.toMutableList(),
            )
        }
        state.users[userId] = user
        event(user, "account_registered", true, null)
        issueVerification(user)
        return accepted()
    }

    private fun verifyEmail(dto: VerifyEmailRequestDto): HttpResponse {
        val email = normalizeEmail(dto.email)
        val code = sixDigits(dto.code)
        val deviceName = deviceName(dto.deviceName)
        val user = activeUserByEmail(email)
        val verification = user?.verification
        if (user == null || user.emailVerified || verification == null) return problem(401, "challenge_expired")
        if (verification.expiresAt <= now() || verification.attempts >= MAX_CODE_ATTEMPTS) {
            return problem(401, "challenge_expired")
        }
        if (!Hashes.constantTimeEquals(b64(verification.codeHash), hashCodeBytes(code))) {
            verification.attempts++
            return problem(401, "invalid_code", attemptsRemaining = MAX_CODE_ATTEMPTS - verification.attempts)
        }
        user.emailVerified = true
        user.verification = null
        event(user, "email_verified", true, deviceName)
        return authenticated(user, deviceName)
    }

    private fun resend(dto: EmailRequestDto): HttpResponse {
        val email = normalizeEmail(dto.email)
        val user = activeUserByEmail(email)
        if (user != null && !user.emailVerified) {
            val time = now()
            user.resends.removeAll { it < time - HOUR }
            val last = user.resends.maxOrNull()
            if (last != null && time - last < RESEND_GAP) return rateLimited(RESEND_GAP - (time - last))
            if (user.resends.size >= MAX_RESENDS_PER_HOUR) return rateLimited(user.resends.min() + HOUR - time)
            issueVerification(user)
        }
        return accepted()
    }

    private fun issueVerification(user: FakeUser) {
        val code = RandomBytes.nextInt(0, 1_000_000).toString().padStart(6, '0')
        user.verification = FakeVerification(Base64Url.encode(hashCodeBytes(code)), 0, now() + VERIFICATION_TTL)
        user.resends += now()
        event(user, "email_verification_sent", true, null)
        onCodeIssued(user.email, code)
    }

    // --- EP-05…EP-09: acceso y tokens ---

    private fun login(dto: LoginRequestDto): HttpResponse {
        val email = normalizeEmail(dto.email)
        val authKey = bytes(dto.authKey, 32)
        val deviceName = deviceName(dto.deviceName)
        val limitKey = Base64Url.encode(Hashes.hmacSha256(b64(state.pepper), "login:$email".toByteArray()))
        val limit = state.loginLimits.getOrPut(limitKey) { FakeLimit() }
        if (limit.lockedUntil > now()) return rateLimited(limit.lockedUntil - now())
        val user = activeUserByEmail(email)
        // Con un correo inexistente se calcula un Argon2 igual para no revelar nada por el tiempo.
        val salt = user?.let { b64(it.authSalt) } ?: Hashes.hmacSha256(b64(state.pepper), email.toByteArray()).copyOf(SALT_BYTES)
        val hash = Argon2idKdf.serverAuthHash(authKey, salt)
        if (user == null || !Hashes.constantTimeEquals(hash, b64(user.authHash))) {
            limit.failures++
            if (user != null) event(user, "login", false, deviceName)
            if (limit.failures >= MAX_LOGIN_FAILURES) {
                limit.failures = 0
                limit.lockedUntil = now() + LOCKOUT
                if (user != null) event(user, "account_locked", true, deviceName)
                // Contrato 1.1.0: el quinto fallo ya responde 429 (también con un correo inexistente).
                return rateLimited(LOCKOUT)
            }
            return problem(401, "invalid_credentials")
        }
        limit.failures = 0
        if (!user.emailVerified) return problem(403, "email_not_verified")
        if (user.twoFactor?.enabled == true) {
            val token = Base64Url.encode(RandomBytes.next(32))
            state.challenges[sha(token)] = FakeChallenge(user.id, deviceName, now() + CHALLENGE_TTL)
            return ok(
                AuthResponseDto.serializer(),
                AuthResponseDto(status = "two_factor_required", challengeToken = token, challengeExpiresIn = 300),
            )
        }
        event(user, "login", true, deviceName)
        return authenticated(user, deviceName)
    }

    private fun verifyTotp(dto: TwoFactorVerifyRequestDto): HttpResponse {
        val code = sixDigits(dto.code)
        val (challengeKey, challenge, user) = openChallenge(dto.challengeToken)
        val twoFactor = user.twoFactor ?: return problem(401, "challenge_expired")
        if (user.twoFactorLockedUntil > now()) return rateLimited(user.twoFactorLockedUntil - now())
        val step = Totp.verify(Base32.decode(twoFactor.secret), code, now() / 1000, twoFactor.lastUsedStep)
        if (step == null) return secondFactorFailure(user, "totp_verification", challenge.deviceName)
        twoFactor.lastUsedStep = step
        state.challenges.remove(challengeKey)
        user.twoFactorFailures = 0
        event(user, "totp_verification", true, challenge.deviceName)
        event(user, "login", true, challenge.deviceName)
        return authenticated(user, challenge.deviceName)
    }

    private fun verifyRecovery(dto: RecoveryCodeRequestDto): HttpResponse {
        require(BackupCodeAlphabet.isNormalized(dto.recoveryCode))
        val (challengeKey, challenge, user) = openChallenge(dto.challengeToken)
        val twoFactor = user.twoFactor ?: return problem(401, "challenge_expired")
        if (user.twoFactorLockedUntil > now()) return rateLimited(user.twoFactorLockedUntil - now())
        val hash = hashCode(dto.recoveryCode)
        val match = twoFactor.recoveryCodeHashes.firstOrNull { Hashes.constantTimeEquals(b64(it), b64(hash)) }
            ?: return secondFactorFailure(user, "recovery_code_login", challenge.deviceName)
        twoFactor.recoveryCodeHashes.remove(match)
        state.challenges.remove(challengeKey)
        user.twoFactorFailures = 0
        event(user, "recovery_code_login", true, challenge.deviceName)
        val base = authenticatedDto(user, challenge.deviceName)
        return ok(
            AuthResponseDto.serializer(),
            AuthResponseDto(
                status = base.status,
                tokens = base.tokens,
                account = base.account,
                recoveryCodesRemaining = twoFactor.recoveryCodeHashes.size,
            ),
        )
    }

    private fun openChallenge(token: String): Triple<String, FakeChallenge, FakeUser> {
        require(token.length in 32..512)
        val key = sha(token)
        val challenge = state.challenges[key]
        val user = challenge?.let { state.users[it.userId] }
        if (challenge == null || user == null || user.deleted || challenge.expiresAt <= now()) {
            state.challenges.remove(key)
            throw Fail(problem(401, "challenge_expired"))
        }
        return Triple(key, challenge, user)
    }

    private fun secondFactorFailure(user: FakeUser, eventType: String, deviceName: String): HttpResponse {
        user.twoFactorFailures++
        event(user, eventType, false, deviceName)
        if (user.twoFactorFailures >= MAX_LOGIN_FAILURES) {
            user.twoFactorFailures = 0
            user.twoFactorLockedUntil = now() + LOCKOUT
            event(user, "account_locked", true, deviceName)
            // Contrato 1.1.0: el quinto fallo del segundo paso ya responde 429.
            return rateLimited(LOCKOUT)
        }
        return problem(401, "invalid_code")
    }

    private fun refresh(dto: RefreshRequestDto): HttpResponse {
        val hash = Base64Url.encode(Hashes.sha256(bytes(dto.refreshToken, 32)))
        val current = state.sessions.values.firstOrNull { it.refreshHash == hash }
        if (current != null && !current.revoked && current.refreshExpiresAt > now()) {
            val user = state.users.getValue(current.userId)
            current.oldRefreshHashes += current.refreshHash
            return ok(TokensDto.serializer(), issueTokens(current, user))
        }
        // Reusar un refresh ya rotado revoca la sesión completa (DEC-16, app.session_rotate()).
        val reused = state.sessions.values.firstOrNull { hash in it.oldRefreshHashes }
        if (reused != null && !reused.revoked) {
            reused.revoked = true
            state.users[reused.userId]?.let { event(it, "token_reuse_detected", true, reused.deviceName) }
        }
        return problem(401, "session_revoked")
    }

    private fun logout(session: FakeSession, user: FakeUser): HttpResponse {
        session.revoked = true
        event(user, "logout", true, session.deviceName)
        return HttpResponse(204, null)
    }

    // --- EP-10…EP-12: bóveda ---

    private fun listItems(user: FakeUser, query: Map<String, String>): HttpResponse {
        val since = query["since"]?.toLongOrNull() ?: throw IllegalArgumentException()
        val limit = query["limit"]?.toIntOrNull() ?: 200
        require(since >= 0 && limit in 1..500)
        if (since > 0 && since < user.purgedThroughRevision) return problem(410, "cursor_expired")
        val changes = state.items.values.filter { it.userId == user.id && it.revision > since }.sortedBy { it.revision }
        val page = changes.take(limit)
        return ok(
            VaultChangesDto.serializer(),
            VaultChangesDto(
                items = page.map { it.toDto() },
                nextSince = page.lastOrNull()?.revision ?: since,
                hasMore = changes.size > limit,
                currentRevision = user.vaultRevision,
            ),
        )
    }

    private fun putItem(user: FakeUser, id: String, dto: PutItemRequestDto): HttpResponse {
        require(dto.formatVersion in 1..32767)
        dto.expectedVersion?.let { require(it >= 1) }
        bytes(dto.nonce, 12)
        require(Base64Url.decode(dto.ciphertext).size in 17..32_768)
        return write(user, id, dto.expectedVersion, dto.formatVersion, dto.nonce, dto.ciphertext)
    }

    private fun deleteItem(user: FakeUser, id: String, expectedVersion: Long): HttpResponse {
        require(expectedVersion >= 1)
        return write(user, id, expectedVersion, null, null, null)
    }

    /** `app.vault_write_item()` (DEC-44), traducido como dice el README (regla 5). */
    private fun write(user: FakeUser, id: String, expected: Long?, formatVersion: Int?, nonce: String?, ciphertext: String?): HttpResponse {
        val isDelete = nonce == null
        val item = state.items[id]
        if (item == null) {
            if (isDelete || expected != null) return problem(404, "not_found")
            user.vaultRevision++
            state.items[id] = FakeItem(id, user.id, 1, user.vaultRevision, formatVersion!!, nonce, ciphertext, false, now())
            return writeResult(201, "created", 1, user.vaultRevision)
        }
        if (item.userId != user.id) return problem(404, "not_found")
        if (expected == null || item.version != expected || item.deleted) {
            return problem(409, "version_conflict", currentVersion = item.version, currentRevision = item.revision)
        }
        user.vaultRevision++
        item.version++
        item.revision = user.vaultRevision
        if (!isDelete) item.formatVersion = formatVersion!!
        item.nonce = nonce
        item.ciphertext = ciphertext
        item.deleted = isDelete
        item.updatedAt = now()
        return writeResult(200, if (isDelete) "deleted" else "updated", item.version, item.revision)
    }

    // --- EP-13…EP-22: cuenta y 2FA ---

    private fun updateAccount(user: FakeUser, dto: UpdateAccountRequestDto): HttpResponse {
        dto.displayName?.let { require(it.length in 1..50) }
        user.displayName = dto.displayName
        return ok(AccountDto.serializer(), user.toDto())
    }

    private fun requestEmailChange(user: FakeUser, dto: EmailChangeRequestDto): HttpResponse {
        normalizeEmail(dto.newEmail)
        checkReauth(user, dto.reauth)
        // El cambio de correo con su código queda fuera del backend falso por ahora: 202 genérico.
        event(user, "email_change_requested", true, null)
        return accepted()
    }

    private fun confirmEmailChange(user: FakeUser, dto: CodeRequestDto): HttpResponse {
        sixDigits(dto.code)
        return problem(401, "challenge_expired")
    }

    private fun changeMasterPassword(session: FakeSession, user: FakeUser, dto: MasterPasswordRequestDto): HttpResponse {
        checkReauth(user, dto.reauth)
        val newAuthKey = bytes(dto.newAuthKey, 32)
        val kdf = validKdf(dto.newKdf)
        validPvk(dto.newProtectedVaultKey)
        if (dto.expectedKeyVersion != user.keyVersion) return problem(409, "key_version_conflict")
        val salt = RandomBytes.next(SALT_BYTES)
        user.authSalt = Base64Url.encode(salt)
        user.authHash = Base64Url.encode(Argon2idKdf.serverAuthHash(newAuthKey, salt))
        user.kdf = FakeKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism, kdf.salt)
        user.pvkNonce = dto.newProtectedVaultKey.nonce
        user.pvkCiphertext = dto.newProtectedVaultKey.ciphertext
        user.keyVersion++
        state.sessions.values.filter { it.userId == user.id && it.id != session.id }.forEach { it.revoked = true }
        event(user, "master_password_changed", true, session.deviceName)
        return ok(KeyVersionDto.serializer(), KeyVersionDto(user.keyVersion))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun deleteAccount(session: FakeSession, user: FakeUser, dto: ReauthRequestDto): HttpResponse {
        checkReauth(user, dto.reauth)
        user.deleted = true
        user.twoFactor = null
        user.verification = null
        state.items.values.removeAll { it.userId == user.id }
        state.sessions.values.filter { it.userId == user.id }.forEach { it.revoked = true }
        // `account_deleted` no está en el enum de ActivityEvent del contrato (ver reporte).
        return HttpResponse(204, null)
    }

    private fun startTwoFactor(user: FakeUser): HttpResponse {
        if (user.twoFactor?.enabled == true) return problem(409, "two_factor_already_enabled")
        val secret = Base32.encode(RandomBytes.next(20))
        user.twoFactor = FakeTwoFactor(secret = secret, enabled = false)
        return ok(TwoFactorSetupDto.serializer(), TwoFactorSetupDto(secret, "End Security", user.email, "SHA1", 6, 30))
    }

    private fun confirmTwoFactor(session: FakeSession, user: FakeUser, dto: CodeRequestDto): HttpResponse {
        val code = sixDigits(dto.code)
        val twoFactor = user.twoFactor
        if (twoFactor == null || twoFactor.enabled) return problem(401, "challenge_expired")
        val step = Totp.verify(Base32.decode(twoFactor.secret), code, now() / 1000, twoFactor.lastUsedStep)
            ?: return problem(401, "invalid_code")
        twoFactor.lastUsedStep = step
        twoFactor.enabled = true
        event(user, "two_factor_enabled", true, session.deviceName)
        return ok(RecoveryCodesDto.serializer(), RecoveryCodesDto(newRecoveryCodes(twoFactor)))
    }

    private fun disableTwoFactor(session: FakeSession, user: FakeUser, dto: ReauthRequestDto): HttpResponse {
        checkReauth(user, dto.reauth)
        user.twoFactor = null
        event(user, "two_factor_disabled", true, session.deviceName)
        return HttpResponse(204, null)
    }

    private fun regenerateRecoveryCodes(session: FakeSession, user: FakeUser, dto: ReauthRequestDto): HttpResponse {
        checkReauth(user, dto.reauth)
        val twoFactor = user.twoFactor?.takeIf { it.enabled } ?: return problem(403, "reauth_required")
        event(user, "recovery_codes_regenerated", true, session.deviceName)
        return ok(RecoveryCodesDto.serializer(), RecoveryCodesDto(newRecoveryCodes(twoFactor)))
    }

    private fun newRecoveryCodes(twoFactor: FakeTwoFactor): List<String> {
        val codes = List(10) { BackupCodeAlphabet.random() }
        twoFactor.recoveryCodeHashes.clear()
        twoFactor.recoveryCodeHashes += codes.map { hashCode(it) }
        return codes.map { "${it.substring(0, 4)}-${it.substring(4)}" }
    }

    /** `Reauth`: `authKey` y, si hay 2FA, `totpCode` (README, `reauth_required`). */
    private fun checkReauth(user: FakeUser, reauth: ReauthDto) {
        val authKey = bytes(reauth.authKey, 32)
        val okKey = Hashes.constantTimeEquals(Argon2idKdf.serverAuthHash(authKey, b64(user.authSalt)), b64(user.authHash))
        val twoFactor = user.twoFactor?.takeIf { it.enabled }
        val okTotp = twoFactor == null || reauth.totpCode?.let { code ->
            Totp.verify(Base32.decode(twoFactor.secret), sixDigits(code), now() / 1000, twoFactor.lastUsedStep)
                ?.also { twoFactor.lastUsedStep = it } != null
        } == true
        if (!okKey || !okTotp) throw Fail(problem(403, "reauth_required"))
    }

    // --- EP-23…EP-25: sesiones y actividad ---

    private fun listSessions(current: FakeSession, user: FakeUser): HttpResponse {
        val sessions = state.sessions.values
            .filter { it.userId == user.id && !it.revoked && it.refreshExpiresAt > now() }
            .sortedWith(compareByDescending<FakeSession> { it.id == current.id }.thenByDescending { it.lastUsedAt })
            .map { SessionDto(it.id, it.deviceName, iso(it.createdAt), iso(it.lastUsedAt), it.id == current.id) }
        return ok(SessionsDto.serializer(), SessionsDto(sessions))
    }

    private fun revokeSession(current: FakeSession, user: FakeUser, id: String): HttpResponse {
        val session = state.sessions[id]
        if (session == null || session.userId != user.id || session.revoked) return problem(404, "not_found")
        session.revoked = true
        event(user, "session_revoked", true, current.deviceName)
        return HttpResponse(204, null)
    }

    private fun listActivity(user: FakeUser, query: Map<String, String>): HttpResponse {
        val before = query["before"]?.toLongOrNull()
        val limit = query["limit"]?.toIntOrNull() ?: 50
        require(limit in 1..100)
        val events = state.events.filter { it.userId == user.id && (before == null || it.id < before) }.sortedByDescending { it.id }
        return ok(
            ActivityDto.serializer(),
            ActivityDto(
                events = events.take(limit).map {
                    ActivityEventDto(it.id, iso(it.occurredAt), it.eventType, if (it.success) "success" else "failure", it.deviceName)
                },
                hasMore = events.size > limit,
            ),
        )
    }

    // --- Sesiones y tokens ---

    private fun <T> withSession(request: HttpRequest, block: (FakeSession, FakeUser) -> T): T {
        val token = request.bearerToken ?: throw Fail(problem(401, "session_revoked"))
        val hash = sha(token)
        val session = state.sessions.values.firstOrNull { it.accessHash == hash }
        val user = session?.let { state.users[it.userId] }
        if (session == null || session.revoked || user == null || user.deleted) throw Fail(problem(401, "session_revoked"))
        if (session.accessExpiresAt <= now()) throw Fail(problem(401, "token_expired"))
        session.lastUsedAt = now()
        return block(session, user)
    }

    private fun authenticated(user: FakeUser, deviceName: String): HttpResponse =
        ok(AuthResponseDto.serializer(), authenticatedDto(user, deviceName))

    private fun authenticatedDto(user: FakeUser, deviceName: String): AuthResponseDto {
        val session = FakeSession(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            deviceName = deviceName,
            createdAt = now(),
            lastUsedAt = now(),
            accessHash = "",
            accessExpiresAt = 0,
            refreshHash = "",
            refreshExpiresAt = 0,
        )
        state.sessions[session.id] = session
        return AuthResponseDto(status = "authenticated", tokens = issueTokens(session, user), account = user.toDto())
    }

    private fun issueTokens(session: FakeSession, user: FakeUser): TokensDto {
        val access = Base64Url.encode(RandomBytes.next(32))
        val refresh = Base64Url.encode(RandomBytes.next(32))
        session.accessHash = sha(access)
        session.accessExpiresAt = now() + ACCESS_TTL
        session.refreshHash = Base64Url.encode(Hashes.sha256(Base64Url.decode(refresh)))
        session.refreshExpiresAt = now() + REFRESH_TTL
        session.lastUsedAt = now()
        return TokensDto(access, (ACCESS_TTL / 1000).toInt(), refresh, iso(session.refreshExpiresAt), session.id)
    }

    // --- Ayudantes ---

    private fun event(user: FakeUser, type: String, success: Boolean, deviceName: String?) {
        state.events += FakeEvent(state.nextEventId++, user.id, now(), type, success, deviceName)
    }

    private fun activeUserByEmail(email: String): FakeUser? = state.users.values.firstOrNull { it.email == email && !it.deleted }

    private fun FakeUser.toDto() = AccountDto(
        userId = id,
        email = email,
        emailVerified = emailVerified,
        displayName = displayName,
        keyVersion = keyVersion,
        kdf = kdf.toDto(),
        protectedVaultKey = ProtectedVaultKeyDto(pvkNonce, pvkCiphertext),
        twoFactorEnabled = twoFactor?.enabled == true,
    )

    private fun FakeKdf.toDto() = KdfDto("argon2id", memoryKiB, iterations, parallelism, salt)

    private fun FakeItem.toDto() = VaultItemDto(
        id = id,
        version = version,
        revision = revision,
        formatVersion = formatVersion,
        deleted = deleted,
        nonce = if (deleted) null else nonce,
        ciphertext = if (deleted) null else ciphertext,
        updatedAt = iso(updatedAt),
    )

    private fun validKdf(dto: KdfDto): KdfDto {
        require(dto.algorithm == "argon2id")
        bytes(dto.salt, SALT_BYTES)
        // El servidor rechaza parámetros por debajo del piso (README, regla 3) o por encima de los topes.
        KdfParams(dto.memoryKiB, dto.iterations, dto.parallelism)
        return dto
    }

    private fun validPvk(dto: ProtectedVaultKeyDto) {
        bytes(dto.nonce, 12)
        bytes(dto.ciphertext, 48)
    }

    private fun hashCode(code: String): String = Base64Url.encode(hashCodeBytes(code))

    private fun hashCodeBytes(code: String): ByteArray = Hashes.hmacSha256(b64(state.pepper), code.toByteArray())

    private fun sha(token: String): String = Base64Url.encode(Hashes.sha256(token.toByteArray()))

    private fun writeResult(status: Int, result: String, version: Long, revision: Long) =
        ok(WriteResultDto.serializer(), WriteResultDto(result, version, revision), status)

    private fun accepted() = HttpResponse(202, """{"status":"accepted"}""")

    private fun rateLimited(millis: Long): HttpResponse {
        val seconds = ((millis + 999) / 1000).toInt().coerceAtLeast(1)
        return problem(429, "rate_limited", retryAfterSeconds = seconds)
    }

    private fun <T> ok(serializer: KSerializer<T>, value: T, status: Int = 200) =
        HttpResponse(status, ApiJson.encodeToString(serializer, value))

    private fun problem(
        status: Int,
        code: String,
        retryAfterSeconds: Int? = null,
        attemptsRemaining: Int? = null,
        currentVersion: Long? = null,
        currentRevision: Long? = null,
    ): HttpResponse {
        val body = ProblemDto("about:blank", code, status, code, retryAfterSeconds, attemptsRemaining, currentVersion, currentRevision)
        return HttpResponse(status, ApiJson.encodeToString(ProblemDto.serializer(), body), retryAfterSeconds)
    }

    private fun <T> parse(body: String?, serializer: KSerializer<T>): T =
        ApiJson.decodeFromString(serializer, requireNotNull(body))

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private companion object {
        const val SALT_BYTES = 16
        const val MAX_CODE_ATTEMPTS = 5
        const val MAX_LOGIN_FAILURES = 5
        const val MAX_RESENDS_PER_HOUR = 5
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val LOCKOUT = 15 * MINUTE
        const val RESEND_GAP = MINUTE
        const val VERIFICATION_TTL = 15 * MINUTE
        const val CHALLENGE_TTL = 5 * MINUTE
        const val ACCESS_TTL = 15 * MINUTE
        const val REFRESH_TTL = 30 * 24 * HOUR

        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun normalizeEmail(raw: String): String {
            val email = raw.trim().lowercase()
            require(email.length <= 254 && EMAIL.matches(email))
            return email
        }

        fun canonicalUuid(text: String): String {
            require(UUID.fromString(text).toString() == text)
            return text
        }

        fun sixDigits(code: String): String {
            require(code.length == 6 && code.all { it in '0'..'9' })
            return code
        }

        fun deviceName(name: String): String {
            require(name.length in 1..100)
            return name
        }

        fun bytes(text: String, size: Int): ByteArray = Base64Url.decode(text).also { require(it.size == size) }

        fun b64(text: String): ByteArray = Base64Url.decode(text)

        fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).toString()
    }
}
