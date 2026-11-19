# Contrato de traducción: app (contrato 1.4.0) ↔ backend real (`integracion/app`)

Hacia la app: Base64url sin relleno, *problem+json* con `code`. Hacia el backend: prefijo `/api`, Base64 estándar,
respuestas en `{success, data, timestamp}`, errores `{statusCode, error, message, details?}`.

## Rutas

| Contrato (app) | Backend | Notas |
|---|---|---|
| `POST /auth/prelogin` | `POST /api/auth/prelogin` | `kdf.memoryKib` → `memoryKiB`, `algorithm = argon2id` |
| `POST /auth/register` | `POST /api/auth/register` | `authKey` → `authHash`, `protectedVaultKey` → `pvk`, + `deviceName`; guarda el desafío; responde 202 |
| `POST /auth/email/verify` | `POST /api/auth/email/verify/confirm` | `{email}` → desafío guardado |
| `POST /auth/email/resend` | `POST /api/auth/email/verify/resend` | 202 |
| `POST /auth/login` | `POST /api/auth/login` | tokens → `authenticated`; `requires2FA` → `two_factor_required`; `requiresEmailVerification` → 403 `email_not_verified` |
| `POST /auth/2fa/verify` | `POST /api/auth/2fa/verify` `{challengeToken, code}` | |
| `POST /auth/2fa/recovery` | `POST /api/auth/2fa/verify` `{challengeToken, recoveryCode}` + `GET /api/auth/2fa` | `recoveryCodesRemaining` de la segunda |
| `POST /auth/refresh` | `POST /api/auth/refresh` | |
| `POST /auth/logout` | `POST /api/auth/logout` | |
| `GET /account` | `GET /api/users/me` + `GET /api/vault/sync?since=<cursor>` | `keyVersion` de la segunda (BR-15) |
| `PATCH /account` | `PATCH /api/users/me` | `displayName: null` → `""` no se admite: 400 |
| `POST /account/email` | `POST /api/auth/email/change` | `reauth` → `authHash` + `code` |
| `POST /account/email/verify` | `POST /api/auth/email/change/confirm` | + `keyVersion` como en `GET /account` |
| `POST /account/master-password` | `POST /api/auth/password` | `newKdf` → `kdf`, `newProtectedVaultKey` → `pvk` |
| `POST /account/delete` | `DELETE /api/users/me` | 204 |
| `POST /account/2fa/setup` | `POST /api/auth/2fa/setup` | `reauth` (BR-20); `otpauthUrl` → `issuer`, `accountName`, `algorithm`, `digits`, `periodSeconds` |
| `POST /account/2fa/confirm` | `POST /api/auth/2fa/activate` | |
| `POST /account/2fa/disable` | `POST /api/auth/2fa/disable` | 204; el backend exige `code` |
| `POST /account/2fa/recovery-codes` | `POST /api/auth/2fa/recovery-codes` | |
| `GET /sessions` | `GET /api/auth/sessions` | `lastUsedAt` nulo → `createdAt` |
| `DELETE /sessions/{id}` | `DELETE /api/auth/sessions/{id}` | |
| `GET /activity?limit&before` | `GET /api/audit?limit&before` | tabla de eventos (abajo); `hasMore = nextCursor != null` |
| `GET /vault/items?since&limit` | `GET /api/vault/sync?since` | `fullResync` con `since > 0` → 410 `cursor_expired`; `hasMore = false` |
| `PUT /vault/items/{id}` | `POST /api/vault/sync` (1 cambio) | `expectedVersion` → `baseVersion` (null → 0); `created`/`updated` |
| `DELETE /vault/items/{id}?expectedVersion` | `POST /api/vault/sync` (1 cambio, `deleted`) | `deleted` |
| `GET /vault/items/{id}` | `GET /api/vault/sync?since=0`, luego `since=1` | lápidas en la segunda |

## Errores (backend `status` + `error` → `code`)

| Backend | `code` |
|---|---|
| 400 `Bad Request` | `validation_failed` |
| 400 `InvalidCode` | `invalid_code` |
| 400 `SecondFactorRequired`, 403 `Forbidden`, 401 `InvalidSecondFactor` en reautenticación | `reauth_required` |
| 401 `InvalidSecondFactor` en el login de 2 pasos | `invalid_code` |
| 401 en `/auth/login` | `invalid_credentials` |
| 401 en `/auth/email/verify/*` o `/auth/2fa/verify` (desafío) | `challenge_expired` |
| 401 en `/auth/refresh` | `session_revoked` |
| 401 en cualquier ruta con sesión | `token_expired` |
| 404 | `not_found` |
| 409 `VaultSyncConflict` | `version_conflict` (+ `currentVersion`) o 404 si `reason = not_found` |
| 409 `KeyVersionMismatch` | `key_version_conflict` |
| 409 en `/auth/2fa/setup` | `two_factor_already_enabled` |
| 429 | `rate_limited` (+ `details.retryAfterSeconds` o `Retry-After`) |
| otro | `server_error` |

## Actividad (backend → `ActivityKind.wire`)

| Backend | App |
|---|---|
| `auth.register` (éxito) | `account_registered` |
| `email.verify_request` (`purpose` ≠ `change_email`) | `email_verification_sent` |
| `email.verify_request` (`purpose = change_email`) | `email_change_requested` |
| `email.verify_confirm` | `email_verified` |
| `auth.login` (`reason = account_locked`) | `account_locked` |
| `auth.login` | `login` |
| `2fa.verify` (`method = recovery_code`) | `recovery_code_login` |
| `2fa.verify` | `totp_verification` |
| `auth.refresh_reuse` | `token_reuse_detected` |
| `auth.logout` | `logout` |
| `auth.session_revoke`, `auth.sessions_revoke_others` | `session_revoked` |
| `account.password_change` | `master_password_changed` |
| `account.email_change` | `email_changed` |
| `2fa.activate` | `two_factor_enabled` |
| `2fa.disable` | `two_factor_disabled` |
| `2fa.recovery_regenerate` | `recovery_codes_regenerated` |
| `account.delete` | `account_deleted` |
| los demás (`auth.register` fallido, `auth.2fa_challenge`, `2fa.setup`, `vault.*`, `account.profile_update`) | se ocultan |
