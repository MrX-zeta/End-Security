# Tarea 10 · Capa de API con backend falso y flujos reales de registro y acceso

**Estado:** hecha
**Escrita:** 2026-10-19 · sesión de diseño

## Novedad importante: división del trabajo (DEC-61)
El backend lo desarrolla la otra persona del equipo. **Tú no escribes código del backend.** El límite es el contrato
nuevo **`docs/api/openapi.yaml` + `docs/api/README.md`** (v1.0.0, nodos `EP-01…EP-25` en el grafo v1.5.1). Léelos
completos antes de empezar; `CLAUDE.md` ya tiene la sección «Backend y API». Mientras no exista el servidor, la app usa
un **backend falso** que se comporta exactamente como el contrato.

## Revisión de la tarea 09
Aprobada, con la revisión humana: revisé `VaultSession`, `AutoLocker` e `ItemCipher` en GitHub.
- Encontrar la bóveda abierta para siempre si se desbloqueaba con la app en segundo plano (hallazgo 1) fue un buen hallazgo.
- También están bien `SecretReader`, la revisión del plazo al prestar la llave y el vector recalculado.

Respuestas (grafo v1.5.1):
- **Hallazgo 2 (DEC-26):** no se agrega bloqueo al apagar la pantalla, porque apagarla ya manda la app a segundo plano y empieza el plazo. **Sí se agrega `onTrimMemory`:** con `level ≥ TRIM_MEMORY_BACKGROUND` y la app en segundo plano, se bloquea de inmediato. No hace falta `AlarmManager`; el plazo ya se revisa al pedir la llave.
- **Autobloqueo por inactividad en primer plano:** no se implementa. Queda como riesgo aceptado en DEC-26.
- **Hallazgo 3:** sí, en esta tarea (Paso 5).
- **Hallazgo 4 (DEC-04):** un campo nuevo sube `format_version`. Una app que no conoce esa versión muestra el elemento en solo lectura («Actualiza la app para editarla»). Se queda `ignoreUnknownKeys`.
- **DEC-27:** quedan registradas las copias del JSON.
- **Archivos basura fuera del repo:** van tres veces. Revisa qué comando los crea (parece una redirección o un `echo x >` en la carpeta de arriba) y evítalo.

## Paso 1 · Capa de API (`core/network`, `feature/*/domain`)
- Interfaces de dominio que siguen el contrato al pie de la letra: `AuthApi` (EP-01…09), `VaultApi` (EP-10…12), `AccountApi` (EP-13…22) y `SessionApi` (EP-23…25).
  - Usan modelos de dominio (`ByteArray`/`SecureBytes`, `UUID` e `Instant`), no JSON.
  - Los errores llegan como `ApiError` sellado con los `code` del contrato (`InvalidCredentials`, `RateLimited(retryAfterSeconds)`, `VersionConflict(currentVersion)`…), más `NetworkUnavailable`.
- DTOs `@Serializable` con los nombres del contrato, y un codec Base64url sin relleno (`java.util.Base64.getUrlEncoder().withoutPadding()`).
  - **Pruebas de los DTOs** contra ejemplos JSON escritos a mano a partir de `openapi.yaml`, para que la versión con Retrofit use los mismos.
- Un módulo de Hilt elige la implementación con `BuildConfig.USE_FAKE_BACKEND`, que por ahora vale `true` en todas las compilaciones. **Retrofit todavía no.**

## Paso 2 · Backend falso (`core/network/fake/`)
Simula el servidor con fidelidad, porque también sirve para la presentación:
- Persiste su estado en un archivo propio (`fake_server.json` en `filesDir`). Solo guarda lo que guardaría el servidor real: hash de la `authKey`, sal, parámetros, pvk, bloques, versiones y revisiones. Así cerrar la app no borra las cuentas.
- **Reglas del README de `docs/api`:**
  - prelogin siempre 200, con sal falsa de 16 B por HMAC para correos inexistentes;
  - registro y reenvío siempre 202;
  - `invalid_credentials` igual para correo inexistente y para `authKey` incorrecta;
  - el hash de `authKey` con Argon2id de 19 MiB, t = 2;
  - 5 fallos → `rate_limited` 15 min;
  - `challengeToken` de 5 min y un solo uso;
  - `version_conflict` y `cursor_expired` como en `app.vault_write_item()`.
- **Código de verificación de correo:** solo en compilaciones de depuración, la pantalla Verificar correo muestra un aviso discreto, «Código de prueba: 123456», que viene del backend falso. Nunca se escribe en logcat.
- **TOTP:** HMAC-SHA1 de RFC 6238 con JCA, 30 s y ±1 paso, con el anti-replay de `last_used_step`. El backend falso permite marcar una cuenta con 2FA con un secreto fijo de depuración (documéntalo en `docs/api/backend-falso.md`, para que la persona del equipo lo agregue a su app autenticadora). La configuración de 2FA desde Ajustes llega después.
- Latencia simulada de 300–800 ms, para que se vean los estados de carga.

## Paso 3 · Registro real (SCR-05 → SCR-06 → Bóveda)
1. Crear cuenta (`TODO(OP-11)`):
   - genera `userId`, sal y `vault_key`;
   - deriva las llaves con `KdfParams.DEFAULT`, fuera del hilo principal y con el botón en estado de carga;
   - envuelve la `vault_key` con AAD `…|userId|1`;
   - llama a `AuthApi.register` y navega a Verificar correo.
2. Verificar correo (`TODO(OP-12)`):
   - `verifyEmail` con `deviceName` (`Build.MANUFACTURER` + `Build.MODEL`, recortado a 100);
   - si responde bien: guarda la fila `account`, guarda los tokens y llama a `VaultSession.unlock` con la `vault_key` que se generó en el paso 1. La `vault_key` se conserva en un `SecureBytes` en memoria entre las dos pantallas; nunca se escribe en disco ni en `SavedStateHandle`;
   - navega a la Bóveda vacía (SCR-11, por ahora el marcador de `Routes.VAULT`).
   - Si el proceso muere entre las dos pantallas, al volver se pide iniciar sesión normalmente.
3. Reenviar y errores según el contrato: `attemptsRemaining` y `challenge_expired`.

## Paso 4 · Inicio de sesión real (SCR-04 → SCR-07/08 → SCR-39)
1. `prelogin` → `KdfParams`.
   - **Piso del cliente** (DEC-05): si los parámetros vienen por debajo, se muestra el error genérico.
   - Si ya existe una fila `account` del mismo correo con otra sal o parámetros más bajos, se rechaza (hallazgo 1 de la tarea 07).
2. Deriva `master_key`, `auth_key` y `enc_key`, con la normalización y codificación de DEC-31. Llama a `login`:
   - `authenticated`: desenvuelve la pvk con `enc_key` (si falla, error genérico), guarda `account` y tokens, y llama a `unlock`;
   - `two_factor_required`: va a 2FA con el `challengeToken`. La `enc_key` se queda en un `SecureBytes` en memoria hasta completar el segundo paso o salir, y se borra en cualquier caso.
3. 2FA (`TODO(OP-15)`) y código de respaldo (`TODO(OP-16)`), conectados. Después de un código de respaldo, si `recoveryCodesRemaining ≤ 3`, la Bóveda muestra después el aviso «Te quedan N códigos de respaldo».
4. Bóveda · cargando (SCR-39) hace la descarga inicial real (`GET /vault/items?since=0`, paginada) a Room y después va al marcador de la Bóveda. **Quita la navegación temporal de 2 s.**
5. **TokenStore** (`core/crypto/keystore`, DEC-16):
   - tokens cifrados con una llave AES-GCM del Android Keystore (`setUserAuthenticationRequired(false)`), guardados en DataStore;
   - sin `EncryptedSharedPreferences`;
   - renovación de una en una (`Mutex`, regla 9);
   - `session_revoked` → cerrar sesión local y volver a Iniciar sesión.

## Paso 5 · Desbloquear real y pantallas protegidas (OP-19, hallazgo 3)
- Desbloquear: lee `account` y cuenta el intento **antes** de derivar (hallazgo 4 de la tarea 07, en `NonCancellable`). Deriva `enc_key`, desenvuelve la pvk con AAD `userId|keyVersion` y llama a `unlock`. Si falla, «Contraseña incorrecta.» y el bloqueo de DEC-08.
- Cerrar sesión (la hoja de Desbloquear): `AuthApi.logout`, aunque falle por red; borra los tokens y `clearAllTables()`; llama a `lock()` y va a Iniciar sesión.
- **`RequireUnlocked { }`**: un composable que envuelve el contenido de toda pantalla con datos de la bóveda. Si el estado es `Locked`, no dibuja nada en ese mismo cuadro, y `LockGuard` navega a Desbloquear **sin transición**.
- Los ViewModel con datos escuchan `VaultSession.state` y vacían su estado al bloquearse.
- **Arranque:** el destino inicial se decide así:
  - si hay cuenta: Desbloquear;
  - si no hay cuenta pero ya se vio el onboarding: Iniciar sesión;
  - si no: el onboarding.

  La pila restaurada tras la muerte del proceso no se usa, porque la llave no sobrevive.
- `AutoLocker`: `onTrimMemory`, como dice la revisión.

## Terminado cuando
- **Prueba de punta a punta en el emulador:** registrarse → código de prueba → Bóveda → cerrar la app → Desbloquear con la contraseña → cerrar sesión → Iniciar sesión → Bóveda.
- **Con 2FA:** en una cuenta de depuración con 2FA, Iniciar sesión → 2FA → Bóveda · cargando → Bóveda.
- **Errores:** contraseña incorrecta 5 veces muestra el bloqueo; un correo inexistente tarda lo mismo y da el mismo error.
- **Pruebas:** unitarias de los ViewModels conectados, de los DTOs contra los ejemplos del contrato, del backend falso (cada `code`) y de `TokenStore` (instrumentada con Keystore real). Todo pasa, igual que `assembleRelease` y lint.
- **Revisión:** `security-reviewer` revisa los Pasos 3, 4 y 5. **REQUIERE REVISIÓN HUMANA.**
- Si algo del contrato no alcanza o está mal, **no lo cambies en el código**: anótalo en el reporte y yo actualizo `docs/api/`.
- Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
