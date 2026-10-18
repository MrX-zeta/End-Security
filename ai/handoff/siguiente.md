# Tarea 08 · Ícono, medición en release, `SecureBytes` y AES-256-GCM

**Estado:** hecha
**Escrita:** 2026-10-18 · sesión de diseño

## Revisión de la tarea 07
Aprobada, incluida la revisión humana del Paso 3. Revisé en GitHub `KdfParams`, `Argon2idKdf`, `Hkdf` y
`MasterPasswordEncoder`, y ejecuté por mi cuenta `tools/vectors/master_key_chain.py` con argon2-cffi: da exactamente
`0bfa616a…`, `3d11ddea…` y `146cb3e7…`. El vector de RFC 9106 §5.3 (`0d640df5…6b01e659`) es el oficial.

También aprobado:
- la sal de 16 B exactos;
- los topes de `KdfParams`;
- la búsqueda de surrogates sueltos antes de codificar;
- `@VisibleForTesting`.

Respuestas (grafo v1.3.9):
- **Medición en release (hallazgo 2): sí.** Crea un `buildType` `benchmark`, solo para medir (ver Paso 2).
- **Piso de parámetros (hallazgo 1): sí, en dos partes** (DEC-05):
  1. cuando elija los parámetros, el mínimo de `KdfParams` sube a esos valores;
  2. cuando exista la fila `account` (TBL-L-ACCOUNT ya guarda la sal y los parámetros), el prelogin que devuelva otra sal o parámetros más bajos se rechaza.

  La parte 1 espera la tabla del teléfono; la 2 llega con OP-13.
- **`SecureBytes`: sí** (DEC-27). `ByteArray` se queda dentro de las primitivas de `core/crypto`; en cuanto un secreto sale de ahí, va en `SecureBytes`. Ajusta `.claude/rules/crypto.md` en ese sentido; yo no puedo editar `.claude/`.
- **Hallazgo 6:** anotado en DEC-07 (la sal falsa se trunca a 16 B).
- **Hallazgo 4:** queda en el `TODO(OP-19)`, como dijiste.
- **Hallazgo 12:** sin cambios; DEC-08 lo acepta.
- **Claves del bloqueo:** registradas en KV-DATASTORE.

## Paso 1 · Ícono de la app (DEC-59)
Sigue `ai/handoff/icono/LEEME.md`. Al terminar, borra la carpeta `ai/handoff/icono/` en el mismo commit. Del ícono, en
el repositorio solo quedan los recursos y el tablero de Figma «Ícono de la app · Final» (`139:3719`).

## Paso 2 · `buildType` de medición
- Llámalo `benchmark`, con `initWith(release)`:
  - `isDebuggable = false`;
  - firma de depuración;
  - sin R8 (`optimization { enable = false }`), para que no quite el código de la prueba;
  - `matchingFallbacks += "release"`.
- `testBuildType = "benchmark"` solo cuando se pida por propiedad (por ejemplo `-PbenchmarkBuild`), para no cambiar las pruebas normales.
- Deja en el reporte el comando exacto para la persona del equipo. La tabla debe decir «depurable: no».
- Si AGP 9 no permite instrumentar un `buildType` no depurable, no lo fuerces: repórtalo y propón otra forma, por ejemplo una pantalla de depuración oculta que mida en un APK de release firmado con la llave de depuración.

## Paso 3 · `SecureBytes` (`core/crypto`)
- La clase envuelve un `ByteArray` propio (copia defensiva al crear) y ofrece:
  - `use { bytes -> … }` para leerlo sin sacarlo;
  - `wipe()`;
  - `AutoCloseable`, donde `close()` hace `wipe()`;
  - `toString()` = `"SecureBytes(***)"`;
  - `equals` y `hashCode` por identidad, para que no se comparen secretos por contenido sin querer;
  - leer después de `wipe()` lanza `IllegalStateException`.
- Las primitivas de la tarea 07 no cambian de firma. Agrega funciones de alto nivel que devuelvan `SecureBytes`, como `deriveMasterKey(...)` y `encKey(SecureBytes)`.
- Pruebas: `toString`, error tras `wipe`, `use` no deja fuera la referencia y `close` borra.

## Paso 4 · AES-256-GCM (DEC-05, DEC-28)
**Es código cripto: presenta primero el plan y espera el visto bueno.**
- `AesGcm` con JCA (`Cipher.getInstance("AES/GCM/NoPadding")`), sin Bouncy Castle:
  - `seal(key, plaintext, aad)` → `Sealed(nonce: 12 B, ciphertext: cifrado || etiqueta de 16 B)`;
  - el nonce siempre se genera con `SecureRandom` dentro de `seal`. Para los vectores hay una función con nonce explícito, `@VisibleForTesting`;
  - `open(key, sealed, aad)` → texto claro. Si la etiqueta no coincide, lanza una excepción propia (`AuthenticationFailed`), sin detalles;
  - llave de exactamente 32 B y nonce de exactamente 12 B.
- `VaultKey.generate()`: 32 bytes de `SecureRandom` en `SecureBytes`.
- `ProtectedVaultKey`: `wrap(encKey, vaultKey, userId, keyVersion)` y `unwrap(...)`, con AAD = `"end-security/vault-key|" + userId + "|" + keyVersion` en UTF-8 (DEC-05).
- **Pruebas:**
  - vectores de **NIST GCM** (los de AES-256 con nonce de 96 bits de la especificación de McGrew-Viega o del CAVS de NIST): al menos uno sin AAD y otro con AAD y texto;
  - cambiar 1 bit del cifrado, la etiqueta, el nonce o la AAD hace fallar `open`;
  - 1 000 llamadas a `seal` con la misma llave no repiten nonce;
  - `wrap`/`unwrap` de ida y vuelta, y `unwrap` con otro `userId` o `keyVersion` falla.
- Instrumentada: los mismos vectores en el teléfono, porque el proveedor de Android, Conscrypt, no es el de la JVM.
- **Todavía no conectes nada a las pantallas ni a Room.**

## Terminado cuando
- El ícono se ve en el lanzador del emulador, con forma normal y con «Íconos temáticos».
- Pasan las pruebas unitarias e instrumentadas, y también `assembleRelease` y `assembleBenchmark`.
- `security-reviewer` revisa los Pasos 3 y 4. **REQUIERE REVISIÓN HUMANA** del Paso 4.
- Los commits están hechos y subidos. Deja el comando de la medición listo para la persona del equipo.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
