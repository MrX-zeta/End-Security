# Research: adaptador prod

## R1. Cliente HTTP
- **Decision:** OkHttp 4.12 (sin Retrofit).
- **Rationale:** el backend usa `PATCH /api/users/me` y `DELETE /api/users/me` con cuerpo. `HttpURLConnection` (lo que usa
  `HibpClient`) no admite `PATCH` en la JVM y su cuerpo en `DELETE` depende de la versión de Android. OkHttp está en el
  stack de CLAUDE.md y se integra con `network_security_config` (y después con los pines SPKI de BR-26).
- **Alternatives considered:** Retrofit (otra capa para 25 rutas que ya arma `ApiClient`); `HttpURLConnection` con
  `X-HTTP-Method-Override` (Express no lo atiende sin middleware).

## R2. Dónde traducir
- **Decision:** en un `ApiTransport` de `prod` (`BackendTransport` + `BackendTranslator`).
- **Rationale:** `ApiClient` ya resuelve renovación de tokens, errores por `code` y mapeo a dominio; si el transporte habla
  «contrato», nada más cambia y `demo` sigue igual. La traducción queda en un archivo probado sin red.
- **Alternatives considered:** `*ApiImpl` nuevas para prod (duplicaría el mapeo a dominio y los `ApiError`).

## R3. Desafíos (BR-08, BR-09)
- **Decision:** mapa en memoria `correo normalizado → challengeToken` para el correo sin verificar; el de 2FA ya viaja en el
  contrato (`challengeToken`).
- **Rationale:** el contrato confirma con `{email, code}`. Un login con correo sin verificar devuelve `email_not_verified`
  (la app ya navega a la pantalla del código) y guarda el desafío nuevo.
- **Sin desafío** (la app se cerró): confirmar o reenviar responde `challenge_expired`; volver a iniciar sesión lo repone.

## R4. Sesión revocada (BR-11)
- **Decision:** un 401 en una ruta con `Authorization` se traduce a `token_expired`; un 401 de `/auth/refresh`, a
  `session_revoked`. `ApiClient` + `TokenManager` hacen el resto (una renovación, single-flight).

## R5. Bóveda (BR-12, BR-13, BR-14)
- **Decision:** `GET /vault/items?since` → `GET /api/vault/sync?since`; `fullResync` con `since > 0` → 410
  `cursor_expired`; con `since = 0`, la lista completa. `PUT`/`DELETE` de un elemento → `POST /api/vault/sync` con un solo
  cambio. `GET /vault/items/{id}` → `sync?since=0` (vigentes) y, si no está, `sync?since=1` (lápidas: un borrado siempre
  tiene revisión ≥ 2 porque cada lote de 1 sube la revisión).
- **Conflictos:** `409 VaultSyncConflict` → `version_conflict` con `currentVersion`; `not_found` → 404.

## R6. Cuenta sin llaves (BR-15)
- **Decision:** `GET /account` = `GET /api/users/me` + `keyVersion` de `GET /api/vault/sync?since=<último cursor>`;
  `kdf` y `protectedVaultKey` salen de las llaves recordadas del último acceso si su `keyVersion` coincide. Si no hay
  (la app se reabrió sin iniciar sesión), `kdf` sale de `prelogin` y la pvk queda como marcador.
- **Rationale:** los usos de `getAccount()` solo leen `keyVersion`, `twoFactorEnabled`, `marketingOptIn` y `email`
  (ChangeEmail, ChangeMasterPassword, DeleteAccount, Settings, AccountEmailSync). Se deja un `shortcut:` con el límite.

## R7. Códigos de respaldo (BR-10)
- **Decision:** `BackupCodeFormat` deja de ser un objeto fijo: dos formatos, Crockford `XXXX-XXXX` (demo) y el del servidor
  `xxxxx-xxxxx` en minúsculas con `abcdefghjkmnpqrstuvwxyz23456789` (prod), elegido por `BuildConfig.USE_FAKE_BACKEND`.

## R8. Activación de 2 pasos (BR-20)
- **Decision:** `AccountApi.startTwoFactorSetup(reauth)`; la pantalla pide la contraseña con `ReauthSheet` antes del paso 1.
  En demo el servidor falso ignora el cuerpo (no cambia su contrato). La clave, emisor y parámetros salen de `otpauthUrl`.

## R9. Actividad (BR-21)
- **Decision:** tabla de eventos del backend → `ActivityKind.wire`; los demás se ocultan; IP y agente nunca pasan.
  `2fa.verify` se separa por `metadata.method`; `email.verify_request` por `metadata.purpose`; `auth.login` con
  `reason = account_locked` es `account_locked`.

## R10. URL y texto plano
- **Decision:** `buildConfigField("String", "API_BASE_URL", …)` en `prod`: `http://127.0.0.1:3000/api/` en `debug`, `""` en
  `release` (`TODO(BR-26)`). Un `network_security_config` en `src/debug` permite texto plano solo a `127.0.0.1` y `10.0.2.2`.
  Sin URL, el transporte responde «sin conexión».
