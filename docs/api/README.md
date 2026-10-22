# Contrato de la API — End Security

> Versión **1.2.0** · 2026-10-22 · Fuente de verdad del límite entre la app y el backend.
> La especificación máquina-legible es [`openapi.yaml`](openapi.yaml) (OpenAPI 3.1). Este documento explica las reglas
> que el YAML no alcanza a expresar. Las decisiones vienen del grafo (`ai/context/knowledge-graph.json`): cada endpoint
> es un nodo `EP-*` conectado a su operación `OP-*`.

## Quién hace qué
La lista completa de actividades del backend (validación, limpieza de entradas, límites, syslog, proxy) está en
[`docs/backend/README.md`](../backend/README.md).

| Parte | Responsable | Dónde |
|---|---|---|
| Backend (NestJS + PostgreSQL + Redis) | Compañero del equipo | Repositorio o carpeta del backend; `docs/bd/schema.sql` es la migración 001 |
| App Android | Equipo + Claude Code | `app/` |
| Este contrato | Los dos | `docs/api/`; **se cambia aquí primero**, con nueva versión, y después en el código |

Mientras el backend no exista, la app usa implementaciones falsas de las mismas interfaces (`AuthApi`, `VaultApi`,
`AccountApi`, `SessionApi`) que responden exactamente como dice este contrato.

## Lo que el servidor nunca recibe
La contraseña maestra, la `master_key`, la `enc_key`, la `vault_key` ni ningún elemento en claro. Recibe `auth_key`
(32 B derivados en el teléfono, DEC-05), sal y parámetros KDF, la `protected_vault_key` y bloques cifrados.

## Convenciones
| Tema | Regla |
|---|---|
| Base | `https://<dominio>/v1` · solo HTTPS (TLS 1.3), con pinning SPKI en la app (DEC-22) |
| Formato | JSON UTF-8, nombres en `camelCase`, cuerpo máximo 64 KiB |
| Bytes | **Base64url sin relleno** (RFC 4648 §5): sal, `authKey`, `nonce`, `ciphertext`, `refreshToken` |
| UUID | Forma canónica: minúsculas y guiones (DEC-29). El servidor rechaza otras formas con 400 |
| Fechas | ISO-8601 en UTC con `Z` (`2026-10-19T20:15:00Z`) |
| Correo | El servidor lo recorta y lo pasa a minúsculas antes de todo (como `users_email_format_chk`) |
| Autenticación | `Authorization: Bearer <accessToken>` en todo lo que no sea `/auth/*` |
| Errores | `application/problem+json` (RFC 9457) con `code` estable (tabla abajo) |
| Límite de intentos | `429` con encabezado `Retry-After` (segundos) y `retryAfterSeconds` en el cuerpo |
| Rastreo | La app manda `X-Request-Id` (UUID); el servidor lo devuelve y lo registra. Nunca se registran cuerpos |

## Errores
Cuerpo: `{ "type": "about:blank", "title": "…", "status": 401, "code": "invalid_credentials", "retryAfterSeconds": 900 }`.
`title` es para depurar; **la app decide el texto a partir de `code`**.

| `code` | HTTP | Cuándo | Texto en la app |
|---|---|---|---|
| `validation_failed` | 400 | Campo con formato inválido (incluye UUID no canónico, Base64url inválido, tamaños) | Error genérico |
| `invalid_credentials` | 401 | Correo o `authKey` incorrectos, **o el correo no existe** (mismo cuerpo y tiempo) | «Correo o contraseña incorrectos.» |
| `invalid_code` | 401 | TOTP, código de respaldo o de correo incorrecto | Según la pantalla |
| `challenge_expired` | 401 | `challengeToken` del 2FA vencido o ya usado | Vuelve a Iniciar sesión |
| `token_expired` | 401 | `accessToken` vencido | La app renueva sola (no se muestra) |
| `session_revoked` | 401 | Refresh inválido, sesión cerrada o reúso detectado (DEC-16) | Cierra sesión local → Iniciar sesión |
| `email_not_verified` | 403 | Credenciales correctas pero correo sin verificar | Va a Verificar correo |
| `reauth_required` | 403 | Falta o falla la reautenticación (`authKey` + TOTP) en operaciones sensibles | «Contraseña o código incorrectos.» |
| `not_found` | 404 | Elemento o sesión que no existe **o es de otro usuario** (no se distingue) | — |
| `version_conflict` | 409 | La versión esperada no coincide (DEC-02) | Marca el elemento en conflicto (DEC-57) |
| `key_version_conflict` | 409 | `expectedKeyVersion` obsoleta al cambiar la contraseña maestra (DEC-41) | Pide sincronizar y reintentar |
| `two_factor_already_enabled` | 409 | `setup` con 2FA activo | — |
| `cursor_expired` | 410 | `since` menor que `purged_through_revision` (DEC-40) | Descarga completa |
| `rate_limited` | 429 | Límite de Redis (DEC-32) | «Demasiados intentos. Vuelve a intentarlo en N min.» |
| `server_error` | 500/503 | Cualquier otro | «No pudimos conectar. Intenta de nuevo.» |

## Reglas de seguridad que el backend debe cumplir
1. **Anti-enumeración (DEC-07, DEC-13).**
   - `prelogin` responde **siempre 200**. Para un correo inexistente, sal = `HMAC-SHA256(PRELOGIN_SECRET, correo)` truncada a 16 B, con los mismos parámetros que una cuenta real (`65536 / 3 / 1`).
   - `register` y `email/resend` responden **siempre 202**. Si el correo ya existe, no se crea nada y se manda un correo de aviso a esa dirección.
   - `login` con un correo inexistente calcula un Argon2id falso para igualar el tiempo y responde `invalid_credentials`.
2. **`authKey` en el servidor.**
   - Se guarda `auth_hash = Argon2id(authKey)` en formato PHC (`$argon2id$…`) con m = 19 MiB, t = 2 y p = 1. La `authKey` ya tiene 256 bits, así que no hace falta más.
   - Se compara con la verificación de la librería, en tiempo constante.
   - Argon2 va **fuera** de la transacción (DEC-46).
3. **Piso de KDF (DEC-05).** `register` y `master-password` rechazan parámetros por debajo de `65536 KiB / t = 3 / p = 1`. Se propone la **migración 002**, que sube `users_kdf_params_chk` a ese piso; hoy acepta desde 19 456.
4. **Tokens (DEC-16).**
   - `accessToken`: JWT de 15 min firmado por el servidor; la app lo trata como opaco.
   - `refreshToken`: 32 B aleatorios; solo se guarda `SHA-256` en `sessions.refresh_token_hash`.
   - La rotación se hace con `app.session_rotate()`. Si devuelve `reuse_detected`, la respuesta es `session_revoked` y se registra `token_reuse_detected`.
5. **Escritura de elementos.** Solo con `app.vault_write_item()` (DEC-44). Sus resultados se traducen así:

   | Resultado | Respuesta |
   |---|---|
   | `created` | 201 |
   | `updated` y `deleted` | 200 |
   | `conflict` | 409 `version_conflict` (con `currentVersion`) |
   | `not_found` y `forbidden` | 404 `not_found` (no se distingue) |
6. **Códigos cortos (DEC-43).** Los códigos de respaldo y de verificación de correo se guardan como HMAC con pepper. El intento fallido de un código se guarda en su propia transacción (DEC-46).
7. **Límites (DEC-32, en Redis, sin correos en claro en las claves).**

   | Qué | Límite |
   |---|---|
   | `login` por HMAC del correo | 5 fallos → 15 min. **El quinto fallo ya responde `429 rate_limited`** con `retryAfterSeconds = 900` (no `invalid_credentials`), y lo mismo para correos inexistentes |
   | `login` por IP | 20 por minuto |
   | `prelogin` y `register` por IP | 10 por minuto |
   | `2fa/verify` y `2fa/recovery` por reto y por cuenta | 5 fallos → 15 min (el quinto ya responde 429) |
   | `email/resend` por cuenta | 1 cada 60 s y 5 por hora |
   | Código de verificación de correo | Vale **15 min** y admite 5 intentos (`email_verifications.attempts`) |
8. **Bitácora (DEC-19).** Cada endpoint que cambia algo inserta su evento al final de la transacción. La tabla de abajo dice cuál.
9. **Nunca** se registran en logs los cuerpos, `authKey`, tokens, códigos, sal ni bloques.

## Flujos
**Registro (OP-11, OP-12)**
1. La app genera el `userId` (UUID v4), la sal y la `vault_key`, deriva las llaves y envuelve la `vault_key` con AAD `end-security/vault-key|userId|1`.
2. `POST /auth/register` → 202.
3. `POST /auth/email/verify` con el código y `deviceName` → 200 con tokens y cuenta → Bóveda vacía.

**Entrar (OP-13…16)**
1. `POST /auth/prelogin` → parámetros.
2. La app deriva la `authKey` (y comprueba el piso).
3. `POST /auth/login`:
   - si responde `authenticated`, la sesión está lista;
   - si responde `two_factor_required` con un `challengeToken` (5 min, un solo uso), sigue `POST /auth/2fa/verify` o `POST /auth/2fa/recovery`.
4. Descarga inicial: `GET /vault/items?since=0`, paginada.

**Sincronización (OP-32…34)**
- Bajar cambios: `GET /vault/items?since=<cursor>` hasta que `hasMore = false`. El cursor nuevo es `nextSince`.
- Subir pendientes: uno por uno, `PUT /vault/items/{id}` con `expectedVersion`. Las lápidas van con `DELETE` y `expectedVersion`.
- Si llega un `409`, el elemento queda en conflicto (DEC-47, DEC-57). Para resolverlo, la app pide la versión del servidor con `GET /vault/items/{id}`.
- La subida es **un elemento por petición**, en orden; la transacción T8 de `modelo-datos.md` se aplica por petición, no por lote.
- Si llega un `410`, se borra la copia local de bloques y se descarga todo de nuevo.

**Cambiar contraseña maestra (OP-27)**
1. La app re-deriva con una sal nueva.
2. Re-envuelve la **misma** `vault_key` con AAD `…|userId|keyVersion+1`.
3. `POST /account/master-password` con `expectedKeyVersion` → 200 con la `keyVersion` nueva. El servidor cierra las demás sesiones.

## Endpoints, operaciones y bitácora
| EP | Método y ruta | OP | Pantalla | Evento de bitácora |
|---|---|---|---|---|
| EP-01 | `POST /auth/prelogin` | OP-13 | SCR-04 | — |
| EP-02 | `POST /auth/register` | OP-11 | SCR-05 | `account_registered`, `email_verification_sent` |
| EP-03 | `POST /auth/email/verify` | OP-12 | SCR-06 | `email_verified`, `login` |
| EP-04 | `POST /auth/email/resend` | OP-12 | SCR-06 | `email_verification_sent` |
| EP-05 | `POST /auth/login` | OP-14 | SCR-04 | `login` (éxito o falla), `account_locked` |
| EP-06 | `POST /auth/2fa/verify` | OP-15 | SCR-07 | `totp_verification` |
| EP-07 | `POST /auth/2fa/recovery` | OP-16 | SCR-08 | `recovery_code_login` |
| EP-08 | `POST /auth/refresh` | OP-17 | — | `token_reuse_detected` si aplica |
| EP-09 | `POST /auth/logout` | OP-18 | SCR-09, SCR-29 | `logout` |
| EP-10 | `GET /vault/items` | OP-32, OP-33 | SCR-39, SCR-10 | — |
| EP-11 | `PUT /vault/items/{id}` | OP-01, OP-02, OP-34, OP-46 | SCR-14, SCR-21 | — |
| EP-12 | `DELETE /vault/items/{id}` | OP-03, OP-34 | SCR-16 | — |
| EP-26 | `GET /vault/items/{id}` | OP-46 | SCR-21 | — |
| EP-13 | `GET /account` | — | SCR-29 | — |
| EP-14 | `PATCH /account` | OP-42 | SCR-29 | — |
| EP-15 | `POST /account/email` | OP-26 | Ajustes | `email_change_requested` |
| EP-16 | `POST /account/email/verify` | OP-26 | Ajustes | `email_changed` |
| EP-17 | `POST /account/master-password` | OP-27 | SCR-35 | `master_password_changed` |
| EP-18 | `POST /account/delete` | OP-28 | SCR-36, SCR-37 | `account_deleted` |
| EP-19 | `POST /account/2fa/setup` | OP-22 | SCR-43, SCR-30 | — |
| EP-20 | `POST /account/2fa/confirm` | OP-23 | SCR-31, SCR-32 | `two_factor_enabled` |
| EP-21 | `POST /account/2fa/disable` | OP-24 | Ajustes | `two_factor_disabled` |
| EP-22 | `POST /account/2fa/recovery-codes` | OP-25 | Ajustes | `recovery_codes_regenerated` |
| EP-23 | `GET /sessions` | OP-29 | SCR-41, SCR-33 | — |
| EP-24 | `DELETE /sessions/{id}` | OP-30 | SCR-33 | `session_revoked` |
| EP-25 | `GET /activity` | OP-31 | SCR-42, SCR-34 | — |

Las operaciones locales (OP-04…10, OP-19…21, OP-35, OP-36, OP-43…47) no tienen endpoint. HIBP (OP-10) va directo
de la app a la API de Have I Been Pwned (DEC-21) y nunca pasa por el backend.

## Historial
| Versión | Fecha | Cambio |
|---|---|---|
| 1.0.0 | 2026-10-19 | Primera versión |
| 1.1.0 | 2026-10-22 | El quinto fallo de `login`/2FA responde 429 directamente; el código de correo dura 15 min |
| 1.2.0 | 2026-10-22 | `GET /vault/items/{id}` (EP-26) para resolver conflictos; subida de un elemento por petición |

## Cómo cambiar el contrato
1. Se propone el cambio en `openapi.yaml` y en este archivo, y se sube la versión: menor si se agrega algo compatible, mayor si se rompe algo.
2. La otra parte lo revisa antes de implementarlo.
3. Se actualizan los nodos `EP-*` del grafo.

Validación: `npx @redocly/cli lint docs/api/openapi.yaml`. En NestJS, se puede comparar contra el documento que genera `@nestjs/swagger`.
