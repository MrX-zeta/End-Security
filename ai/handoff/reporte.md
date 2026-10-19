# Reporte · Tarea 09 · Ajustes de crypto, Room local, `VaultSession` y cifrado de elementos

**Estado:** hecha
**Fecha:** 2026-10-18 (los Pasos 3 y 4 empezaron después de que la persona aprobó el plan en el chat)

## Qué se hizo

### Paso 1 · Ajustes de `core/crypto`
- **`SecureBytes.use` → `withBytes`:** `SecureBytes` sigue siendo `AutoCloseable`, así que ahora `use { }` es el de Kotlin y cierra (tiene prueba). Actualicé `.claude/rules/crypto.md`.
- **`ProtectedVaultKey.wrap/unwrap` reciben `UUID`:** la AAD usa `UUID.toString()`, que es la forma canónica. Una prueba con el UUID en mayúsculas lo comprueba.
- **`KdfParams`:**
  - el piso pasa a 65 536 KiB, t ≥ 3 y p ≥ 1, en lugar de la lista de OWASP; los topes se quedan;
  - `KdfParams.DEFAULT` = (65 536, 3, 1);
  - 46 MiB/t1 y 19 MiB/t2 ahora se rechazan, y 64/t3 y 128/t3 se aceptan.
- **`Argon2BenchmarkTest`** usaba `KdfParams`, no la función interna como suponía la tarea. La cambié a `Argon2idKdf.argon2id(...)` para que pueda seguir midiendo 19 y 46 MiB.
- **Vector propio recalculado con los parámetros reales** (64 MiB, t=3, p=1), usando argon2-cffi con `tools/vectors/master_key_chain.py` (actualizado):
  - `master_key` `2af2fd06…b0f01f81`;
  - `enc_key` `7c90a1fd…3bd96d70`;
  - `auth_key` `dcf81e4c…1b37b3ec`.

  La prueba los fija con `KdfParams(65_536, 3, 1)` explícito, no con `DEFAULT`, para que no cambie si un día suben los de las cuentas nuevas.

### Paso 2 · Room (`end_security.db`, modelo de datos §8)
- **Room 2.8.5** con KSP y el plugin `androidx.room`. `exportSchema = true` y el esquema en `app/schemas/…/1.json`.
- **Tablas:**
  - `account`: una sola fila, con `user_id` como UUID canónico;
  - `vault_items`: `sync_state` ∈ `synced`/`pending`/`conflict` con `TypeConverter`, `local_seq` e índice en `sync_state`;
  - `breach_checks`: FK a `vault_items` con `CASCADE`;
  - `health_snapshots`.
  - **Ninguna columna guarda texto claro de un elemento.** Las entidades no son `data class` y su `toString()` no muestra bloques ni correo.
- **DAOs:**
  - `AccountDao`: `get`, `observe`, `exists` y `replace`. `replace` deja una sola fila; si cambia el `user_id`, borra también los datos de la cuenta anterior (hallazgo 10).
  - `VaultItemDao`:
    - `observeVisible` (`Flow`, sin lápidas), `get` e `insert`;
    - `updateContent` y `delete` llevan el `local_seq` esperado (regla 8);
    - `delete`: si el elemento nunca se subió, se borra la fila; si ya se subió, deja lápida `pending`; si estaba en `conflict`, se queda en `conflict`.
  - `BreachCheckDao` y `HealthSnapshotDao` (`upsert`, y `deleteBefore` para conservar 12 meses).
  - Al cerrar sesión, `clearAllTables()`.
- **Respaldo:** las reglas de respaldo ya excluían `database`, y `allowBackup="false"`.

### Paso 3 · `VaultSession`, autobloqueo y `LockGuard`
- **`core/session/VaultSession`** (`@Singleton`):
  - estado `Locked` / `Unlocked(userId)` en `StateFlow`; la `vault_key`, en un `SecureBytes` privado;
  - `unlock` se queda con la llave y no deja abrir otra cuenta sin bloquear antes;
  - `lock()` borra la llave fuera del monitor (espera a que termine un `withBytes` en curso);
  - `withVaultKey { reader -> }` presta la llave **solo para leer**: `SecretReader`, envuelto para que ni con un *cast* se pueda borrar;
  - lanza `VaultLockedException` si está bloqueada, si se bloquea a la mitad o **si venció el plazo de autobloqueo**.
- **`core/session/AutoLocker`:**
  - en `ProcessLifecycleOwner` (lo registra `EndSecurityApp`);
  - al salir programa el bloqueo y deja el plazo en la sesión; al volver compara con `elapsedRealtime`;
  - si la bóveda se abre con la app ya en segundo plano, la cuenta empieza en ese momento (hallazgo 1);
  - con 0, bloquea al salir.
- **`autolock_minutes` en `SettingsStore`:** 5 por defecto. Un valor que no esté en {0, 1, 5, 15, 30} se trata como 5 (hallazgo 7).
- **`LockGuard`** (`navigation/`):
  - al pasar de `Unlocked` a `Locked` va a `UNLOCK` con la pila vacía si hay cuenta (`AccountRepository.hasLocalAccount()` → `SELECT EXISTS`), o a `LOGIN` si no;
  - el `Locked` del arranque no cuenta.
- **«Bloquear ahora»:** `lock()` está listo, pero el único candado está deshabilitado en Bóveda · cargando, y la Bóveda se conecta en la tarea 10.

### Paso 4 · `ItemCipher` (DEC-29)
- **Serialización:** `kotlinx-serialization-json` 1.9.0 (la última compatible con Kotlin 2.2.10) y su plugin.
- **`feature/vault/domain/VaultItem`:** Kotlin puro, con `Instant`; `toString` = `VaultItem(***)`.
- **`feature/vault/data/ItemCipher`:**
  - `encrypt(vaultKey: SecretReader, itemId, userId, item)` → `Sealed` y `decrypt(...)` → `VaultItem`;
  - AAD = `itemId|userId|formatVersion` en ASCII;
  - `ItemJsonV1` con los nombres de DEC-29 y fechas ISO-8601;
  - `ignoreUnknownKeys`;
  - el JSON en bytes se borra en `finally`;
  - una etiqueta que no coincide da `AuthenticationFailed`; un bloque que abre pero no es v1 da `ItemFormatException` (sin mensaje).

## Compilación y pruebas (salida real)
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` y `lintDebug`: `BUILD SUCCESSFUL`, y lint con **0 errores**.
  - Las 11 advertencias son de antes o de «hay versión más nueva» (serialization 1.11 y lifecycle 2.12 piden Kotlin 2.3 o son alfa).
- **Unitarias: 239, 0 fallas.** Nuevas o cambiadas:
  - **`VaultSessionTest` (9):**
    - empieza bloqueada; presta la llave solo para leer; `lock()` borra;
    - no deja abrir otra cuenta; pasado el plazo bloquea y no presta;
    - una referencia guardada fuera deja de servir;
    - bloquear a la mitad da `VaultLockedException` y nunca ceros; `lock()` espera a un `withBytes` en curso.
  - **`AutoLockerTest` (10):**
    - a los **4:59 no** bloquea y a los **5:00 sí**; volver a los 4:59 cancela;
    - volver tras un congelamiento (`elapsedRealtime` +6 min) bloquea; con **0**, bloquea al salir; con 1 min, al minuto;
    - estando bloqueada no programa nada;
    - **desbloquear con la app ya en segundo plano** también bloquea, y con 0 al instante;
    - un valor inválido se trata como 5; el plazo queda en la sesión.
  - **`LockGuardViewModelTest` (3):** con cuenta va a Desbloquear; sin cuenta, a Iniciar sesión; el arranque no cuenta.
  - **`ItemCipherTest` (8):**
    - ida y vuelta; el bloque no contiene el texto;
    - otro `itemId`, otro `userId` u otro `formatVersion` fallan; el bloque de A no abre como B; otra `vault_key` falla;
    - los campos desconocidos se ignoran; un JSON que no es v1 da `ItemFormatException`; `toString`.
  - `SecureBytesTest`, `KdfParamsTest`, `ProtectedVaultKeyTest` y `MasterKeyChainTest`, actualizadas. `wipe` dentro de `withBytes` lanza error en vez de colgarse (hallazgo 6).
- **Instrumentadas en el emulador: 29, OK.** Nuevas:
  - **`DaoTest` (10):** una sola fila en `account`; cambiar de cuenta borra los datos de la anterior; crear, editar y `Flow`; `local_seq` viejo; `conflict` se conserva; borrado físico o lápida; `CASCADE` de `breach_checks`; salud una por día y recorte; `clearAllTables`.
  - **`SchemaPlaintextTest`:** sobre el esquema real de SQLite, ninguna tabla tiene `name`, `username`, `password`, `url`, `notes`… y `vault_items` tiene exactamente las 10 columnas de §8.1.
- **App en el emulador:** arranca con `AutoLocker` inyectado y vuelve de segundo plano sin errores.
- **Emulador:** le borré los datos (`-wipe-data`), como autorizó la revisión de la tarea 08. Ahora tiene 84 % libre.

## Revisión de seguridad (`security-reviewer`, Pasos 1, 3 y 4, y vistazo a Room)
Confirmó que la criptografía nueva está bien: AAD, piso del KDF, borrado de bytes y R8 con kotlinx.serialization. Los problemas estaban en el ciclo de vida de la sesión.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Alta | Si el desbloqueo terminaba con la app ya en segundo plano, no se programaba el autobloqueo y la bóveda quedaba abierta indefinidamente | **Corregido:** `AutoLocker` sabe si la app está en primer plano y escucha el estado; la cuenta empieza al abrir. Hay pruebas |
| 2 | Media | `delay()` no avanza con el teléfono dormido ni con la app congelada: la llave podía quedar en RAM pasado el plazo, y un trabajo en segundo plano todavía la recibía | **En parte:** el plazo queda en `VaultSession`, que bloquea al prestar la llave pasado el plazo. Sin implementar: bloquear al apagar la pantalla, `onTrimMemory` o `AlarmManager` (ver dudas) |
| 3 | Media | `LockGuard` funciona por evento: la Bóveda se ve unos 0,7 s durante la transición; en segundo plano, los ViewModel conservan datos; tras la muerte del proceso se restaura la pila sin aviso; sin cuenta no hacía nada | **En parte:** sin cuenta ahora va a Iniciar sesión. Ocultar por estado las pantallas protegidas rompería el flujo temporal de hoy (2FA → Bóveda · cargando sin desbloquear), así que queda como **`TODO(tarea 10)`** en `LockGuard` |
| 4 | Media | `ignoreUnknownKeys`: una app vieja que edite un elemento perdería en silencio un campo nuevo | **Documentado** en `ItemCipher` como regla: un campo nuevo sube `format_version`. Ver dudas |
| 5 | Baja | `withVaultKey` prestaba el `SecureBytes` dueño; con el `use` de Kotlin se borraba la llave y el estado seguía `Unlocked` | **Corregido:** se presta como `SecretReader` envuelto; si la llave aparece borrada, la sesión se bloquea |
| 6 | Baja | `wipe()` dentro de `withBytes` del mismo hilo se colgaba para siempre | **Corregido:** lanza `IllegalStateException` |
| 7 | Baja | `autolock_minutes` aceptaba cualquier valor | **Corregido** |
| 8 | Baja | Hay más copias en claro del JSON de las que decía el comentario (`String` del JSON y búferes de kotlinx) | **Documentado.** Conviene agregarlo a DEC-27 |
| 9 | Baja | Borrar físicamente un elemento «nunca subido» mientras se está subiendo puede hacerlo reaparecer | Para la tarea de sincronización |
| 10 | Baja | `account.replace` dejaba los datos de la cuenta anterior | **Corregido** |
| Info | — | `unlock` de otra cuenta sin pasar por `Locked`; `SELECT EXISTS`; KDoc de la AAD «UTF-8»; `json{v:1}` en las reglas; el vector con `DEFAULT`; prefijo de tipo en la AAD de HIBP y salud | **Corregidos**, o anotados en `.claude/rules/crypto.md` |

## Diferencias con la tarea
- **`Argon2BenchmarkTest`:** pasó a la función interna (usaba `KdfParams`).
- **El vector propio cambió**, porque cambiaron los parámetros (64 MiB/t3). El script versionado lo reproduce.
- **`ItemCipher.encrypt/decrypt` reciben `SecretReader`** (lo que presta `VaultSession`), no `SecureBytes`.
- **`LockGuard` sin cuenta va a Iniciar sesión**, en vez de no hacer nada.
- **Dependencias nuevas:** `lifecycle-process` 2.11.0 y kotlinx.serialization 1.9.0.

## Dudas o decisiones que tomé
- **Hallazgo 2:** ¿bloqueo también al apagar la pantalla (`ACTION_SCREEN_OFF`) o cuando el sistema pide memoria (`onTrimMemory`)? Cambia DEC-26. Hoy el plazo se aplica al volver a la app o al pedir la llave.
- **Hallazgo 3, para la tarea 10:** las pantallas con datos ocultan su contenido en cuanto la bóveda está `Locked`, sus ViewModel vacían los elementos y la pila restaurada se revisa al arrancar. ¿Navegación sin transición hacia Desbloquear?
- **Hallazgo 4:** ¿regla de que todo campo nuevo sube `format_version` (con solo lectura en apps viejas), o conservar los campos desconocidos al volver a cifrar? Hoy dejé documentada la primera.
- **DEC-27:** agregar las copias del JSON (la `String` completa y los búferes de kotlinx.serialization).
- **DEC-26:** tampoco existe el autobloqueo por inactividad en primer plano. No estaba en la tarea; conviene anotarlo como pendiente.
- **Por error volví a crear un archivo basura** fuera del repo (`C:\Users\brian\UPchiapas\Securidad-NO.txt`, con una «x») y lo borré enseguida.

## Revisión
- [x] **REQUIERE REVISIÓN HUMANA** de `VaultSession`, `AutoLocker` e `ItemCipher`, en especial los hallazgos 1 a 3 antes de conectar Desbloquear en la tarea 10. Pasaron por `security-reviewer`.
