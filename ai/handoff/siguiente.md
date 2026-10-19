# Tarea 09 · Ajustes de crypto, Room local, `VaultSession` y cifrado de elementos

**Estado:** hecha
**Escrita:** 2026-10-18 · sesión de diseño

## Revisión de la tarea 08
Aprobada, con la revisión humana de los Pasos 3 y 4: revisé `AesGcm`, `Sealed`, `SecureBytes`, `VaultKey` y
`ProtectedVaultKey` en GitHub.
- La corrección de la carrera entre `use` y `wipe` (hallazgo 1) fue la más importante de todo el proyecto hasta ahora.
- `.benchmark` como app aparte, los 3 calentamientos y los 10 000 nonces están bien.
- El ícono también: ya está en el lanzador.

Respuestas (grafo **v1.4.0**):
- **PEND-01 resuelto: Argon2id con m = 64 MiB, t = 3 y p = 1.** En el Redmi tarda 343 ms y en el emulador 587 ms, ambos sin depuración. Si el teléfono de la otra persona del equipo pasa de 1,5 s, se revisa; los parámetros son por cuenta.
- **Renombrar: sí.** `withBytes { }` para leer; `use { }` queda como el de Kotlin, que cierra.
- **`userId` en la AAD: sí.** Las funciones reciben `java.util.UUID` y usan `toString()`, en minúsculas y con guiones. Lo mismo vale para la AAD de los elementos (DEC-29).
- **Módulo `:core:crypto`:** no por ahora. El error de lint de `VisibleForTests` basta para el alcance del proyecto.
- **DEC-27:** quedan registradas `SecretKeySpec`, las copias de Conscrypt/JNI y la falta de `Cleaner`.
- **Emulador lleno:** bórrale los datos desde Device Manager («Wipe Data») cuando lo necesites. No tiene nada que conservar.
- **El archivo basura fuera del repo:** no pasa nada, pero evita crear archivos fuera de `proyecto-U4`.

## Paso 1 · Ajustes de `core/crypto`
- `SecureBytes.use` → `withBytes`. Actualiza los usos y `.claude/rules/crypto.md`.
- `ProtectedVaultKey.wrap/unwrap` reciben `UUID`.
- **Piso de `KdfParams`:** memoria ≥ 65 536 KiB, t ≥ 3 y p ≥ 1. Reemplaza la lista de OWASP, porque el piso ya la supera; los topes se quedan. Agrega `KdfParams.DEFAULT` con esos valores.
  - `Argon2BenchmarkTest` sigue usando la función interna, así que no se rompe.
  - Pruebas: 46 MiB/t1 ahora se rechaza; 64/t3 y 128/t3 se aceptan.

## Paso 2 · Room (TBL-L-*, `docs/bd/modelo-datos.md`)
- Base de datos `end_security.db` con `exportSchema = true` y esquemas en `app/schemas/`. Agrega Room a `libs.versions.toml` (KSP, compatible con AGP 9).
- Entidades, según el grafo:
  - `account` (TBL-L-ACCOUNT): una sola fila;
  - `vault_items` (TBL-L-ITEMS): con `sync_state ∈ synced | pending | conflict` y `local_seq`;
  - `breach_checks` (TBL-L-BREACH);
  - `health_snapshots` (TBL-L-HEALTH).
- **Ninguna columna guarda texto claro de un elemento:** solo `nonce` y `ciphertext`. Los nombres de los elementos también van cifrados.
- DAOs con lo mínimo que piden OP-01…07 y OP-19: insertar o actualizar, leer todo en `Flow`, borrar con lápida, leer `account`, y borrar todo al cerrar sesión.
- Pruebas instrumentadas de los DAOs con base en memoria, más una que revise que el esquema exportado no tenga columnas como `name`, `password`, `username` o `url`.

## Paso 3 · `VaultSession` y autobloqueo (`core/session`, DEC-08, DEC-26, PEND-04)
- `VaultSession` (`@Singleton`) tiene un estado `Locked` o `Unlocked(userId: UUID)` en un `StateFlow`. La `vault_key` **no** va en el estado: vive en un `SecureBytes` privado.
- Operaciones:
  - `unlock(userId, vaultKey)`;
  - `lock()`, que borra la llave;
  - `withVaultKey { }`, que lanza un error si está bloqueada.
- **Autobloqueo:**
  - con `ProcessLifecycleOwner`, al pasar a segundo plano empieza la cuenta de `autolock_minutes` (DataStore, por defecto **5**; las opciones serán 1, 5, 15, 30 y 0 = «al salir de la app»);
  - al volver antes de tiempo se cancela;
  - usa `elapsedRealtime`;
  - **«Bloquear ahora»** (el candado, DEC-52) llama a `lock()`.
- **`LockGuard`** (`navigation/`): si la sesión pasa a `Locked` y existe la fila `account`, navega a `UNLOCK` con la pila vacía (hallazgo 10 de la tarea 06). Si no existe la cuenta, sigue el flujo de onboarding o Iniciar sesión de siempre.
- Pruebas:
  - `lock()` borra la llave (`isWiped`);
  - `withVaultKey` bloqueada lanza un error;
  - autobloqueo: a los 5 min sí y a los 4:59 no (reloj falso); 0 bloquea en cuanto la app pasa a segundo plano.

## Paso 4 · Cifrado de elementos (DEC-29)
- Formato `json_elemento_v1` (grafo, DEC-29): `name`, `username`, `password`, `url`, `notes`, `created_at` y `password_changed_at`. Serialízalo con `kotlinx.serialization` (agrega el plugin).
- `ItemCipher` (`core/crypto` o `feature/vault/data`):
  - `encrypt(vaultKey, itemId: UUID, userId: UUID, item)` → `Sealed`;
  - `decrypt(...)` → `item`;
  - AAD = `itemId|userId|formatVersion` (UUID canónicos y `formatVersion = 1`).
  - El JSON en claro es `ByteArray` y se borra después de cifrar o de leer. Las `String` del modelo son la limitación aceptada de DEC-27.
- Pruebas:
  - ida y vuelta;
  - cambiar `itemId`, `userId` o `formatVersion` hace fallar `decrypt`;
  - un bloque de un elemento no se abre como otro;
  - JSON con campos desconocidos: se ignoran, para compatibilidad futura.

## Terminado cuando
- Pasan las pruebas unitarias e instrumentadas, `assembleRelease` y lint.
- `security-reviewer` revisa los Pasos 1, 3 y 4. **REQUIERE REVISIÓN HUMANA** de `VaultSession` y `ItemCipher`.
- **Todavía no conectes Desbloquear ni la Bóveda.** Eso es la tarea 10, junto con una cuenta de prueba solo para depuración.
- Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
