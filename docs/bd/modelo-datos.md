# End Security — Modelo de datos

> **Estado:** propuesta v1 · 2026-10-14 · pendiente de aprobación del equipo
> **Archivos:** `schema.sql` (DDL PostgreSQL) · `pruebas_esquema.sql` (36 pruebas automáticas + 1 de concurrencia; todas pasan en PostgreSQL 16) · diagrama ER en FigJam: <https://www.figma.com/board/w1hMfKUxNDxk6hFTV2h9JW>
> **Base:** tabla de operaciones del equipo + `ai/context/knowledge-graph.json`

---

## 1. Dónde vive cada dato

| Almacén | Dónde | Qué guarda | ¿Datos en claro? |
|---|---|---|---|
| **PostgreSQL** | Servidor | Cuentas, bloques cifrados, sesiones, 2FA, verificaciones de correo, bitácora | Solo correo, nombre visible e IP (esta última se borra con el tiempo) |
| **Redis** | Servidor | Contadores de intentos, reto de login entre contraseña y TOTP, sesiones revocadas | No |
| **Room** | Teléfono | Espejo de los bloques cifrados, parámetros para desbloquear sin red, caché de HIBP cifrada | No |
| **DataStore** | Teléfono | Ajustes y tokens (cifrados con llave del Keystore) | Solo ajustes |

Regla de oro: **ninguna tabla tiene columnas para nombre de cuenta, usuario, contraseña, URL o notas.** Todo eso va dentro de `ciphertext`.

---

## 2. Decisiones de modelado

Estas decisiones salieron al bajar el grafo a tablas. Las marcadas con ⚠ cambian algo del grafo y hay que actualizarlo.

| # | Decisión | Por qué |
|---|---|---|
| MD-01 | Cada elemento tiene **`version`** (concurrencia optimista) y **`revision`** (posición en el historial del usuario) | `version` detecta conflictos por elemento. `revision` es el cursor de sincronización: "dame todo lo que cambió después de la revisión 120". Usar fechas como cursor falla con relojes desfasados y transacciones concurrentes. |
| MD-02 | `users.purged_through_revision` | Cuando se purgan lápidas viejas, un teléfono que estuvo desconectado más tiempo nunca se enteraría de esos borrados. Si su cursor es menor que este valor, el servidor responde **410** y el cliente hace descarga completa. |
| MD-03 ⚠ | **Los usuarios nunca se borran físicamente**: baja lógica (`deleted_at`) → anonimización a los 30 días (`anonymized_at`) | La bitácora encadenada referencia al usuario. Borrar la fila rompería la cadena o la integridad referencial. La fila anonimizada no contiene ningún dato personal ni de llaves (lo garantiza un CHECK). |
| MD-04 ⚠ | La IP y el user-agent viven en **`audit_log_pii`**, separados de `audit_log` | Para anonimizar hay que borrar la IP, pero modificar `audit_log` rompería la cadena de hashes. Al separarlos, `audit_log` nunca necesita UPDATE ni DELETE. |
| MD-05 | `recovery_codes` referencia a **`two_factor`**, no a `users` | Es imposible que existan códigos sin 2FA, y desactivar el 2FA los borra en cascada. |
| MD-06 ⚠ | El **anti-replay del TOTP** vive en `two_factor.last_used_step` (PostgreSQL), no en Redis | Se valida con un solo UPDATE atómico, dentro de la misma transacción que crea la sesión. |
| MD-07 | `users.key_version` | Sube al cambiar la contraseña maestra. Los demás teléfonos detectan que sus parámetros KDF y su `pvk` locales quedaron obsoletos. |
| MD-08 | Llaves primarias: **UUID generado por el cliente** para elementos (para crear sin conexión), **UUID del servidor** para sesiones y verificaciones (se exponen en la API sin revelar cuántas hay) y **`bigint` consecutivo** para la bitácora (el orden es parte de la cadena) | Los UUID aleatorios fragmentan índices en tablas enormes; con cientos de filas por usuario es irrelevante. En PostgreSQL 18 se puede usar `uuidv7()` si se quiere. |
| MD-09 | Los códigos de recuperación y de correo se guardan como **HMAC-SHA256 con un *pepper*** del servidor, no con SHA-256 simple | Un código de 6 dígitos o de ~50 bits se rompe por fuerza bruta en minutos si solo está hasheado. Con HMAC, robar la BD no basta: falta el pepper, que vive en una variable de entorno. |
| MD-10 | La lógica con concurrencia delicada vive en **funciones de la BD** (`vault_write_item`, `session_rotate`) | Es la parte más fácil de romper con código generado por IA. Dentro de la BD es atómica, está probada y la API solo la invoca. |
| MD-11 | `display_name` se conserva como **opcional** (resuelve PEND-07) | Lo mantuviste en la tabla de operaciones. No es único ni sirve para iniciar sesión. |
| MD-12 | Dos roles de BD: **`endsec_api`** y **`endsec_jobs`** | Mínimo privilegio. Ni la API ni los jobs pueden modificar la bitácora, y la API no puede borrar usuarios. |

---

## 3. Entidades del servidor

El detalle completo de columnas y restricciones está en `schema.sql`. Aquí va lo esencial.

### 3.1 `users`
Una fila por cuenta. Guarda lo que el cliente necesita para derivar sus llaves, más el hash del hash de autenticación.

- **Clave:** `id uuid`. **Único:** `email` entre cuentas no eliminadas (índice parcial).
- **Material criptográfico (no secreto para el servidor):**
  - `kdf_salt` (16 B) y los parámetros `kdf_memory_kib`, `kdf_iterations`, `kdf_parallelism`, con mínimos de OWASP;
  - `pvk_nonce` (12 B) y `pvk_ciphertext` (48 B).
- `auth_hash`: hash PHC `$argon2id$…` calculado **en el servidor** sobre `auth_key`.
- **Ciclo de vida:** activo → `deleted_at` (periodo de gracia) → `anonymized_at` (vacía). Lo controlan 3 CHECK.

### 3.2 `vault_items`
Un bloque cifrado por elemento.

- **Clave:** `id uuid` generado por el cliente. **Único:** `(user_id, revision)`, que además sirve de índice para la sincronización.
- **Elemento vivo:** `nonce` (12 B) + `ciphertext` (17 B a 32 KiB).
- **Lápida:** `deleted_at` con `nonce` y `ciphertext` en NULL. Lo garantiza un CHECK.
- **AAD del cifrado** (no se guarda, se reconstruye): `id | user_id | format_version`.

### 3.3 `sessions`
Una por dispositivo con sesión iniciada.

- Solo se guarda el **SHA-256 del refresh token**, que es opaco de 32 B aleatorios, así que no requiere Argon2.
- `previous_refresh_token_hash` permite detectar que alguien reutilizó un token ya rotado.
- `revoke_reason` ∈ `logout`, `remote_logout`, `password_changed`, `token_reuse`, `account_deleted`.

### 3.4 `two_factor` (1:0..1 con `users`)
- `status` ∈ `pending` / `active`; un CHECK obliga a `activated_at` cuando está activo.
- El secreto va cifrado con una llave del servidor; `server_key_id` permite rotarla.
- `last_used_step` impide reutilizar el mismo código.

### 3.5 `recovery_codes`
- 10 por usuario, `code_hash` HMAC, `used_at` para el uso único.
- **Único:** `(user_id, code_hash)`.

### 3.6 `email_verifications`
- `purpose` ∈ `verify_email` / `change_email`; `target_email` es el correo que se está verificando.
- `attempts` ≤ 5 y `expires_at` (sugerido: 15 min).
- **Un solo código vigente por usuario y propósito** (índice único parcial).

### 3.7 `audit_log` + `audit_log_pii`
- `id`, `prev_hash` y `hash` los asigna un **trigger** bajo un candado. El cliente no puede falsificarlos; la prueba lo comprueba enviando `id=999` y un hash falso.
- `hash = SHA-256(prev_hash ‖ id | fecha UTC | user_id | session_id | event_type | outcome | metadata)`.
- Triggers que bloquean UPDATE, DELETE y TRUNCATE (incluso para el dueño), más permisos que no los otorgan.
- `app.verify_audit_chain()` recorre la cadena y devuelve el primer evento alterado. Las pruebas demuestran que si un superusuario desactiva el trigger y modifica un evento, la verificación lo detecta.
- `event_type` es una lista cerrada de 18 eventos; `metadata` es un JSON de máximo 2 KB **sin datos personales**.
- `audit_log_pii` guarda IP y user-agent, que se borran a los 90 días o al anonimizar la cuenta.

---

## 4. Relaciones

| Relación | Cardinalidad | Al borrar el padre | Nota |
|---|---|---|---|
| users → vault_items | 1 : 0..N | CASCADE | En la práctica los usuarios no se borran (MD-03) |
| users → sessions | 1 : 0..N | CASCADE | |
| users → two_factor | 1 : 0..1 | CASCADE | PK = FK |
| two_factor → recovery_codes | 1 : 0..N | CASCADE | Desactivar el 2FA borra los códigos |
| users → email_verifications | 1 : 0..N | CASCADE | |
| users → audit_log | 0..1 : 0..N | **RESTRICT** | `user_id` NULL en eventos sin usuario identificado |
| sessions ⇢ audit_log | 0..1 : 0..N | sin FK | Las sesiones se purgan; la historia se conserva |
| audit_log → audit_log_pii | 1 : 0..1 | RESTRICT | |

---

## 5. Operación → tablas

| Operación | Lee | Escribe | Tx |
|---|---|---|---|
| Registrar cuenta | users | users, email_verifications, audit_log(+pii) | T1 |
| Verificar correo / editar correo | email_verifications, users | email_verifications, users, audit_log | T13 |
| Editar nombre de la cuenta | — | users.display_name | simple |
| Obtener parámetros de derivación | users | — | — |
| Iniciar sesión | users, two_factor | sessions, audit_log(+pii) · Redis | T3 |
| Verificar TOTP en login | two_factor | two_factor, sessions, audit_log · Redis | T4 |
| Iniciar sesión con código de recuperación | recovery_codes | recovery_codes, sessions, audit_log | T5 |
| Renovar token | sessions | sessions (+audit si hay robo) | T6 |
| Cerrar sesión / cerrar sesión remota | sessions | sessions, audit_log · Redis | T7 |
| Listar sesiones / dispositivos | sessions | — | — |
| Registrar / editar / eliminar contraseña | Room | Room → (sync) vault_items, users.vault_revision | local + T8 |
| Subir cambios pendientes | users, vault_items | vault_items, users | T8 |
| Descarga inicial / descargar cambios | users, vault_items | — | T9 |
| Cambiar contraseña maestra | users | users, sessions, audit_log · Room | T10 |
| Iniciar / confirmar 2FA | two_factor | two_factor, recovery_codes, audit_log | T11 |
| Desactivar 2FA / regenerar códigos | two_factor | two_factor, recovery_codes, audit_log | T12 |
| Eliminar cuenta | users | vault_items, sessions, two_factor, email_verifications, users, audit_log | T14 |
| Consultar mi actividad | audit_log, audit_log_pii | — | — |
| Registrar evento en bitácora | — | audit_log(+pii) | dentro de cada tx |
| Bloquear por intentos fallidos | Redis | Redis + audit_log (`account_locked`) | — |
| Procesos programados | todas | todas salvo audit_log | T15 |
| Ver / buscar / copiar / salud / generar / desbloquear / biometría / ajustes | Room, DataStore | Room (`breach_checks`), DataStore | locales |
| Consultar filtraciones (HIBP) | Room | Room (`breach_checks`) | local |

---

## 6. Transacciones

**Reglas generales:**
1. **Nivel de aislamiento:** `READ COMMITTED` (el predeterminado) con candados explícitos (`FOR UPDATE`). No hace falta `SERIALIZABLE`: los conflictos se resuelven con candados de fila y versiones.
2. **Orden de candados**, siempre el mismo para evitar interbloqueos: `users → vault_items → sessions → two_factor → recovery_codes → email_verifications → audit_log`.
3. **Argon2 nunca dentro de una transacción.** Tarda ~100 ms o más y mantendría candados abiertos. Primero se verifica, después se abre la transacción.
4. **El evento de bitácora se inserta al final**, porque su trigger serializa a todos los que escriben en la bitácora hasta el COMMIT.
5. **Correos y escrituras en Redis van después del COMMIT.** Si el correo falla, la transacción no se deshace; el usuario puede pedir reenvío.

| Tx | Operación | Pasos |
|---|---|---|
| **T1** | Registro | Rate limit (Redis) → Argon2id(auth_key) fuera de la tx → **BEGIN**: INSERT users · INSERT email_verifications · INSERT audit_log `account_registered` → **COMMIT** → enviar correo. Si el correo ya existe (unique_violation): ROLLBACK y **misma respuesta genérica** (opcionalmente avisar por correo al dueño real). |
| **T2** | Prelogin | SELECT de los parámetros KDF. Si el correo no existe, devolver parámetros falsos: sal = HMAC(secreto, correo). Sin tx. |
| **T3** | Login, paso 1 | SELECT user → verificar Argon2 fuera de la tx. Si falla: audit `login/failure` + contador en Redis. Si tiene 2FA: crear `login_challenge` en Redis (5 min) y no tocar la BD. Si no tiene 2FA: **BEGIN**: INSERT sessions · INSERT audit `login/success` (+pii) → **COMMIT**. |
| **T4** | Login, paso 2 (TOTP) | GETDEL del reto en Redis → calcular el paso que coincide → **BEGIN**: `UPDATE two_factor SET last_used_step = $paso WHERE … AND (last_used_step IS NULL OR last_used_step < $paso)`; si afecta 0 filas → ROLLBACK (código repetido) · INSERT sessions · INSERT audit → **COMMIT**. |
| **T5** | Login con código de recuperación | **BEGIN**: `UPDATE recovery_codes SET used_at = now() WHERE user_id = $1 AND code_hash = $2 AND used_at IS NULL` (debe afectar 1 fila) · INSERT sessions · INSERT audit `recovery_code_login` → **COMMIT** → alerta por correo y aviso de cuántos códigos quedan. |
| **T6** | Renovar token | `app.session_rotate(hash_presentado, hash_nuevo, expira)`. Si devuelve `reuse_detected`: INSERT audit `token_reuse_detected` → COMMIT → Redis `session_revoked` + alerta. ⚠ **El cliente debe serializar las renovaciones** (una a la vez en `TokenAuthenticator`); dos renovaciones simultáneas del mismo teléfono se verían como robo. |
| **T7** | Logout / cierre remoto | **BEGIN**: `UPDATE sessions SET revoked_at, revoke_reason WHERE id = $1 AND user_id = $2` · INSERT audit → **COMMIT** → Redis `session_revoked:{id}`. |
| **T8** | Subir cambios | **Una tx por lote** (máx. 100 elementos): `app.vault_write_item(...)` por elemento. La función bloquea primero `users` (serializa las escrituras del usuario y mantiene `revision` sin huecos visibles) y luego el elemento. Los conflictos no son errores: se devuelven en la respuesta elemento por elemento. |
| **T9** | Descargar cambios | Si `cursor > 0` y `cursor < purged_through_revision` → **410**. Si no: `SELECT … WHERE user_id = $1 AND revision > $cursor ORDER BY revision LIMIT 500`. **Descarga inicial** (cursor 0): en la primera página se fija `hasta = vault_revision` y se pagina con `revision ≤ hasta` y `deleted_at IS NULL`. Lo que cambie durante la descarga tendrá una revisión mayor y llegará en la siguiente sincronización. |
| **T10** | Cambiar contraseña maestra | Reautenticación (auth_key actual + TOTP) fuera de la tx → **BEGIN**: SELECT users FOR UPDATE · `UPDATE users SET auth_hash, kdf_*, pvk_*, key_version = key_version + 1 WHERE id = $1 AND key_version = $esperada` · UPDATE sessions (revocar todas menos la actual, `password_changed`) · INSERT audit → **COMMIT** → Redis + alerta. El teléfono reemplaza su fila `account` local **solo después** de la respuesta 200. Los elementos no se re-cifran. |
| **T11** | Configurar 2FA | **Inicio:** `INSERT two_factor (pending) ON CONFLICT (user_id) DO UPDATE … WHERE two_factor.status = 'pending'`; si ya está activo → 409. **Confirmación:** **BEGIN**: SELECT FOR UPDATE (pending) → validar código → UPDATE `active`, `activated_at`, `last_used_step` · DELETE recovery_codes · INSERT 10 códigos · INSERT audit → **COMMIT**. Los códigos en claro se muestran una sola vez. |
| **T12** | Desactivar 2FA / regenerar códigos | Reautenticación → **BEGIN**: DELETE two_factor (cascada a los códigos) *o* DELETE recovery_codes + INSERT 10 · INSERT audit → **COMMIT** → alerta. |
| **T13** | Verificar / cambiar correo | **Solicitud:** **BEGIN**: DELETE del código vigente del mismo propósito · INSERT nuevo · INSERT audit → **COMMIT** → enviar. **Verificación:** tx 1: SELECT FOR UPDATE; si el código no coincide → `attempts + 1` → **COMMIT** (el intento fallido debe quedar guardado). Si coincide, tx 2: `consumed_at` · `UPDATE users SET email = target, email_verified_at = now()` (si hay unique_violation → "correo no disponible") · INSERT audit → **COMMIT** → avisar al correo anterior. |
| **T14** | Eliminar cuenta | Reautenticación → **BEGIN**: SELECT users FOR UPDATE · DELETE vault_items · UPDATE sessions (revocar, `account_deleted`) · DELETE two_factor · DELETE email_verifications · `UPDATE users SET deleted_at = now()` · INSERT audit → **COMMIT** → Redis + correo. El teléfono borra Room, DataStore y las llaves del Keystore. |
| **T15** | Mantenimiento diario | `select app.run_maintenance()` con el rol `endsec_jobs`:<br>• purga lápidas de más de 90 días;<br>• anonimiza cuentas con más de 30 días de baja;<br>• purga sesiones, códigos y 2FA pendientes vencidos;<br>• borra IPs de más de 90 días.<br>Devuelve un resumen en JSON. |

---

## 7. Roles y privilegios

| Tabla | `endsec_api` | `endsec_jobs` |
|---|---|---|
| users | SELECT, INSERT, UPDATE (**sin DELETE**) | SELECT, UPDATE |
| vault_items, sessions, two_factor, recovery_codes, email_verifications | SELECT, INSERT, UPDATE, DELETE | SELECT, DELETE |
| audit_log | SELECT, INSERT | SELECT, INSERT |
| audit_log_pii | SELECT, INSERT | SELECT, DELETE |

En el despliegue se crean roles con login que heredan de estos:

```sql
create role endsec_api_login login password '…' in role endsec_api;
```

Las migraciones las ejecuta un tercer rol dueño del esquema. La API **nunca** se conecta como superusuario ni como dueño.

---

## 8. Base local (Room)

### 8.1 Tablas

**`account`** — una sola fila, la cuenta con sesión en este teléfono.

| Columna | Tipo | Nota |
|---|---|---|
| user_id | TEXT PK | |
| email, display_name | TEXT | |
| kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism | BLOB / INTEGER | Para desbloquear sin red |
| pvk_nonce, pvk_ciphertext | BLOB | vault_key envuelta |
| key_version | INTEGER | Se compara con el servidor en cada sincronización |
| sync_cursor | INTEGER | Última `revision` aplicada |
| last_sync_at | INTEGER | epoch ms |
| bio_iv, bio_wrapped_vault_key | BLOB NULL | vault_key envuelta con la llave biométrica del Keystore |

**`vault_items`** — espejo del servidor más el estado de sincronización.

| Columna | Tipo | Nota |
|---|---|---|
| id | TEXT PK | UUID generado en el teléfono |
| format_version | INTEGER | |
| nonce, ciphertext | BLOB NULL | NULL = lápida local pendiente de subir |
| server_version | INTEGER NULL | Última versión confirmada por el servidor; es la "versión esperada" al subir. NULL = nunca se subió |
| server_revision | INTEGER NULL | |
| local_seq | INTEGER | Sube con cada edición local; evita marcar como sincronizado algo que se editó mientras se subía |
| sync_state | TEXT | `synced` / `pending` / `conflict` (enum con TypeConverter) |
| deleted_at, updated_at | INTEGER | epoch ms |

Índice en `sync_state`.

**`breach_checks`** — caché del resultado de HIBP, **cifrada**.

| Columna | Tipo | Nota |
|---|---|---|
| item_id | TEXT PK, FK → vault_items (CASCADE) | |
| checked_local_seq | INTEGER | Si difiere del `local_seq` del elemento, la contraseña cambió y hay que volver a consultar |
| nonce, ciphertext | BLOB | `{ "pwned_count": n }` |
| checked_at | INTEGER | |

No hace falta SQLCipher: Room solo contiene bloques cifrados, y con `allowBackup=false` no sale del teléfono. Room no soporta CHECK en anotaciones, así que las reglas se hacen cumplir con tipos de Kotlin (enums) y en los DAO.

### 8.2 Ciclo de un elemento local

| Evento | Efecto en Room |
|---|---|
| Crear | INSERT `pending`, `server_version` NULL, `local_seq` 1 |
| Editar | Nuevo blob, `local_seq + 1`, `pending` (si estaba en `conflict`, se queda en `conflict`) |
| Borrar sin haberse subido nunca | DELETE físico |
| Borrar ya subido | Lápida local (`nonce`/`ciphertext` NULL), `pending` |
| Respuesta `created` / `updated` | Si `local_seq` no cambió → `synced` + `server_version`/`server_revision`. Si cambió → sigue `pending` con el `server_version` nuevo |
| Respuesta `deleted` | DELETE físico local |
| Respuesta `conflict` | `sync_state = conflict` (ver abajo) |
| Respuesta `not_found` al editar | Se purgó en el servidor: `server_version` NULL y se vuelve a crear |
| Cambio remoto sobre fila `synced` | Reemplazar, o borrar si es lápida |
| Cambio remoto sobre fila `pending` | `conflict` |
| Respuesta **410** | Descarga completa y reconciliación |

**Resolver un conflicto requiere la `vault_key`**, porque la copia "(conflicto)" lleva un id nuevo, el id forma parte del AAD y por eso hay que volver a cifrarla. Como `SyncWorker` puede correr con el almacén bloqueado, solo marca `conflict`. Al desbloquear, `ConflictResolver`:
1. descarga la versión del servidor;
2. descifra la local;
3. la guarda como elemento nuevo `pending` con sufijo "(conflicto)";
4. reemplaza la fila original con la del servidor.

### 8.3 DataStore

- **Ajustes:** `autolock_minutes`, `clipboard_clear_seconds`, `biometric_enabled`.
- **Tokens:** access y refresh, cifrados con `TokenKeyManager` (AES-GCM del Keystore).
- `device_name` para la tabla de sesiones.

---

## 9. Redis

Ninguna clave contiene un correo en claro: se usa `HMAC(secreto, correo)`.

| Clave | Valor | TTL | Uso |
|---|---|---|---|
| `rl:login:ip:{ip}` | contador | 15 min | Limitar intentos por IP |
| `rl:login:acct:{hmac(correo)}` | contador | 15 min | Bloqueo tras 5 fallos → audit `account_locked` |
| `rl:totp:{user_id}` | contador | 15 min | Intentos de TOTP / código de recuperación |
| `rl:prelogin:ip:{ip}` · `rl:register:ip:{ip}` · `rl:email:{user_id}` | contador | 15 min – 1 h | Abuso de prelogin, registro y envío de correos |
| `login_challenge:{aleatorio}` | `{user_id, device_name}` | 5 min | Puente entre contraseña y TOTP; se consume con GETDEL (un solo uso) |
| `session_revoked:{session_id}` | `1` | 15 min (vida del access token) | El guard rechaza access tokens de sesiones revocadas sin consultar Postgres |

---

## 10. Observaciones sobre la tabla de operaciones

1. **"Cerrar sesión remota"** está como *Lectura* → es **Escritura**.
2. **"Generar contraseña"** está como *Escritura* → es un **cálculo local**; no toca ninguna base.
3. **"Consultar contraseñas filtradas"**: lo que sale es el prefijo de 5 caracteres del SHA-1, no la contraseña. La frecuencia es media (al desbloquear o bajo demanda).
4. **"Registrar cuenta"**: la contraseña maestra se **captura** pero **no se persiste**. Lo que se guarda está en §3.1.
5. **Falta "Verificar correo"**, al registrarse y al cambiar el correo. Ya está modelada (T13).
6. Las primeras 10 filas no tienen llena la columna "Dónde". Con §5 se puede completar.

---

## 11. Pendientes

| Pendiente | Propuesta |
|---|---|
| Parámetros de Argon2id (PEND-01) | El esquema ya acepta cualquier valor ≥ mínimos de OWASP; falta medir en un teléfono real |
| Plazos (PEND-05) | Lápidas 90 días · gracia de baja 30 días · IPs 90 días. Son parámetros de `run_maintenance()` |
| ORM (PEND-06) | Cualquiera sirve. Las funciones se llaman con SQL directo (`$queryRaw` en Prisma o `query()` en TypeORM). Recomiendo usar `schema.sql` como **migración 001** con node-pg-migrate o las migraciones del ORM |
| Versión de PostgreSQL | Probado en **16**. Usar la imagen `postgres:16` o `postgres:17` en Docker. En desarrollo se puede montar `schema.sql` en `/docker-entrypoint-initdb.d/` |
| Actualizar el grafo | Registrar MD-01…MD-12 y corregir el anti-replay (MD-06), el borrado de usuarios (MD-03) y la PII de la bitácora (MD-04) |
