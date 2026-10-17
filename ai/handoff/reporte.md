# Reporte · Tarea 07 · Cierre de DEC-31, bloqueo local y primer código de `core/crypto`

**Estado:** hecha
**Fecha:** 2026-10-17

## Qué se hizo

### Paso 1 · Ajustes de DEC-31 (v1.3.7)
- **`MasterPasswordNormalizer`:** quita U+FE0E/FE0F **antes** de NFKC. «A»+FE0F+U+030A ahora da «Å», y normalizar dos veces da lo mismo.
- **`MasterPasswordPolicy`:** además rechaza:
  - U+FFF9–FFFB;
  - el uso privado (U+E000–F8FF y los planos 15 y 16);
  - los no-caracteres (U+FDD0–FDEF y los que terminan en FFFE o FFFF).
  - ZWJ, ZWNJ y las etiquetas siguen aceptándose en cualquier posición: es el riesgo aceptado, y tiene una prueba que lo documenta.
- **Mensaje nuevo:** «Tu contraseña tiene caracteres que no se pueden usar (invisibles o emojis muy nuevos)».

### Paso 2 · Bloqueo local de Desbloquear (DEC-08)
- **`core/datastore/UnlockLockout`:** reglas puras. Después de 5 fallos, 1 min; luego 2, 4 y 8, hasta un tope de 15 min (sin desbordarse).
  - Cuánto falta:
    - en el mismo arranque manda `elapsedRealtime`;
    - si `elapsedRealtime` es menor que cuando empezó el bloqueo, el teléfono se reinició y manda el reloj de pared;
    - nunca devuelve más que la espera que toca.
- **`core/datastore/UnlockAttemptStore`:**
  - usa el archivo `settings` de DataStore;
  - `recordFailure`, `recordSuccess` (borra todo) y `remainingLockout`;
  - **claves nuevas para KV-DATASTORE:** `unlock_failures`, `unlock_locked_until_wall` y `unlock_locked_until_elapsed`.
- **`core/common/DeviceClock`:** reloj de pared y `elapsedRealtime`, inyectados con `TimeModule`.
- **`UnlockAttemptRepository`** (dominio) e implementación en `data`.
- **`UnlockViewModel`:**
  - al abrir, retoma un bloqueo que siga activo;
  - `onWrongPassword()` cuenta el fallo;
  - desde el quinto, el bloqueo reemplaza al error.
  - Se quitó `onLockedOut(minutes)`, porque el bloqueo ya no viene del servidor.

### Paso 3 · `core/crypto` (DEC-05, DEC-28), con el plan aprobado en el chat
- **Dependencia:** `org.bouncycastle:bcprov-jdk18on:1.86` (última estable, septiembre de 2026). Solo la API ligera, sin registrar un proveedor JCA.
- **`KdfParams`:**
  - acepta si se alcanza alguna configuración de OWASP (46 MiB/t1, 19/t2, 12/t3, 9/t4, 7/t5);
  - topes: 128 MiB, t ≤ 10, p ≤ 4 y memoria × pasadas ≤ 2 × (64 MiB × 3).
- **`Argon2idKdf.derive`:** `ARGON2_id`, versión 0x13, salida de 32 B y sal de **exactamente 16 B** (DEC-06, `users_kdf_salt_chk`). En `finally` borra las copias de la sal y los parámetros que guarda Bouncy Castle (`clear()`).
- **`Hkdf`:**
  - HKDF-SHA256 sin sal, con `encKey` (`end-security/enc`) y `authKey` (`end-security/auth`);
  - rechaza una `master_key` que no mida 32 B o que esté en ceros, por si se borró antes de tiempo.
- **`MasterPasswordEncoder`:** UTF-8 con `REPORT`.
  - Antes de codificar, busca surrogates sueltos (ICU y la JVM no tratan igual uno al final).
  - `OVERFLOW` también es error.
  - El búfer intermedio se borra en `finally`.
- **`wipe()`:** las llaves intermedias de las pruebas se borran en `finally`.
- Las funciones internas sin validación llevan `@VisibleForTesting(otherwise = PRIVATE)`.
- **Nada está conectado a las pantallas.**
- **`tools/vectors/master_key_chain.py`:** recalcula el vector propio con argon2-cffi 25.1.0 y `hmac`, y comprueba su HKDF contra RFC 5869.

### Paso 4 · Medición (PEND-01)
`Argon2BenchmarkTest` (instrumentada, sin interfaz) mide 1 calentamiento más 5 corridas por configuración. Deja la tabla en logcat (`Argon2Benchmark`) y en la salida de `am instrument`. Atrapa `OutOfMemoryError`.

**Emulador** (Google sdk_gphone64_x86_64 · Android 16, API 36 · `Runtime.maxMemory()` = 192 MiB · APK depurable: sí):

| Memoria | t | p | Mediana (ms) | Peor (ms) | Corridas (ms) |
|---|---|---|---|---|---|
| 19 MiB | 2 | 1 | 416 | 447 | 397, 416, 378, 447, 444 |
| 46 MiB | 1 | 1 | 549 | 625 | 496, 525, 605, 625, 549 |
| 64 MiB | 3 | 1 | 1736 | 1929 | 1858, 1736, 1712, 1717, 1929 |

**Teléfono más modesto del equipo:** pendiente. Debe correrla una persona del equipo con el teléfono conectado (yo no toco los teléfonos físicos):

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luis.end_security.core.crypto.Argon2BenchmarkTest
```

La tabla aparece en `adb logcat -s Argon2Benchmark:I`. **Aviso (hallazgo 2):** la medición corre en un APK depurable, y en ART eso puede hacer a Bouncy Castle varias veces más lento que en release. Los números sirven para comparar las configuraciones entre sí, pero probablemente exageran el tiempo real.

## Compilación y pruebas (salida real)
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` y `assembleRelease`: `BUILD SUCCESSFUL`. **181 pruebas unitarias, 0 fallas.** Nuevas:
  - **`Argon2idKdfTest` (4):** **RFC 9106 §5.3** (`0d640df5…6b01e659`); no toca la contraseña ni la sal; otra sal da otra llave; la sal debe medir 16 B.
  - **`HkdfTest` (5):** **RFC 5869, casos 1, 2 y 3**; sal vacía igual a sin sal; `enc_key` ≠ `auth_key`.
  - **`MasterKeyChainTest` (6):**
    - vector propio «ñ» (NFD) + «２０２６» + «❤» con U+FE0F → UTF-8 → `master_key` `0bfa616a…`, `enc_key` `3d11ddea…` y `auth_key` `146cb3e7…`, calculados aparte con argon2-cffi;
    - surrogates sueltos al inicio, al final y en medio; emoji con su pareja; HKDF rechaza una llave en ceros.
  - **`KdfParamsTest` (4):** OWASP, mínimos, topes y costo conjunto.
  - **`UnlockLockoutTest` (9):** 5 → 1 min, 6 → 2 min, tope de 15; adelantar o atrasar la hora no cambia nada; tras un reinicio manda el reloj de pared.
  - **`UnlockAttemptStoreTest` (5):** usa un DataStore en memoria, porque el de archivo falla al renombrar en la JVM de Windows. Cubre: cerrar la app no reinicia el bloqueo, un acierto reinicia, cambiar la hora, reinicio.
  - **`UnlockViewModelTest` (9):** el quinto fallo bloquea 1 min; un bloqueo guardado sigue al abrir.
  - **`ForbiddenCharactersTest` (19)** y **`MasterPasswordNormalizerTest` (11):** casos nuevos del Paso 1.
- **Instrumentadas en el emulador: 11, OK.** Nuevas:
  - `MasterKeyChainDeviceTest` (3): **el mismo vector con el ICU del teléfono**, más el surrogate al final y el orden FE0F antes de NFKC;
  - `UnlockAttemptStoreDeviceTest` (1): DataStore de archivo real;
  - `Argon2BenchmarkTest`.
- **R8:** compila sin reglas.
  - Como todavía nada usa `core/crypto`, R8 quita Bouncy Castle entero.
  - Hice una compilación de prueba con `-keep` sobre `core/crypto`: R8 no dio ninguna advertencia de clases faltantes y el APK creció 27 KiB. Quité la regla temporal; `rules.keep` no cambió.
  - **No hizo falta ningún `-dontwarn`.**
- **Código fuente de Bouncy Castle 1.86, revisado:**
  - `Argon2BytesGenerator` borra sus bloques al devolverlos (`deallocate` → `clear`);
  - `Argon2Parameters` y su `Builder` tienen `clear()`;
  - `HKDFParameters` clona el IKM y no tiene `clear()`.

## Revisión de seguridad (`security-reviewer`, Pasos 1 y 3, y vistazo al 2)
Sin hallazgos críticos ni altos. Confirmó Argon2id y HKDF bien usados, los vectores de RFC y el Paso 1.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Media | Un servidor malicioso puede bajar los parámetros del KDF hasta el mínimo de OWASP, o mandar una sal fija para todos, y el cliente no lo nota. Atacar `auth_key` sin conexión le saldría unas 5 veces más barato | **No implementado:** cuando cierres PEND-01, subir el piso del cliente a los parámetros elegidos y recordar en `account` los parámetros y la sal de cada cuenta (ver dudas) |
| 2 | Media | La medición corre en un APK depurable, que en ART es más lento y empujaría a elegir parámetros más débiles | **En parte:** la tabla dice si el APK es depurable. Medir en release requiere un `buildType` de medición (ver dudas) |
| 3 | Media | El codificador y el vector solo se probaban en la JVM; el ICU de Android podría descartar en silencio un surrogate al final | **Corregido:** se buscan los surrogates sueltos antes de codificar, y el vector también corre en el teléfono (`MasterKeyChainDeviceTest`) |
| 4 | Media | Contar el fallo después de Argon2 y en `viewModelScope` permite esquivar el bloqueo matando la app; el bloqueo solo lo aplica la UI | **Anotado** en el `TODO(OP-19)`: contar el intento antes de derivar, en `NonCancellable`, y consultar al repositorio antes de cada intento. Se hace al conectar OP-19 |
| 5 | Baja | El tope de 256 MiB pasaba del heap por app de muchos teléfonos, y memoria × pasadas no tenía tope | **Corregido:** 128 MiB y tope de memoria × pasadas |
| 6 | Baja | La sal aceptaba `>= 16` B; DEC-06 dice 16 exactos, y el largo podría delatar la sal falsa de DEC-07 | **Corregido:** exactamente 16 B. Conviene anotar en DEC-07 que la sal falsa se trunca a 16 B |
| 7 | Baja | Una `master_key` ya borrada daría llaves calculables sin ningún error | **Corregido:** HKDF exige 32 B que no estén en ceros. `SecureBytes` lo resolvería del todo (ver dudas) |
| 8 | Baja | Copias en Bouncy Castle: H0 (72 B), `tmpBlockBytes`; en HKDF, IKM, PRK, estados de HMAC y T(1); en Android, ICU copia la entrada si es `String` | **Documentado** en los comentarios. No hay API para borrarlas (DEC-27) |
| 9 | Baja | La prueba de no-caracteres pasaba por la regla de «sin asignar» del falso, no por `isNoncharacter` | **Corregido:** se les da edad 1.1 en esa prueba |
| 10 | Baja | `OVERFLOW` no se trataba como error | **Corregido** |
| 11 | Baja | Las funciones `internal` sin validación se podían usar en todo el módulo | **Corregido:** `@VisibleForTesting(otherwise = PRIVATE)` |
| 12 | Baja/Info | Un error de disco (o un archivo corrupto) deja intentar sin límite; reiniciar más cambiar la hora esquiva el bloqueo (DEC-08 lo acepta) | **Documentado.** Mejora opcional: `Settings.Global.BOOT_COUNT` (cambia DEC-08) |
| 13 | Info | El script del vector propio no estaba en el repo | **Corregido:** `tools/vectors/master_key_chain.py` |

## Diferencias con la tarea
- **Sal de 16 B exactos**, no «al menos 16», por DEC-06 y el hallazgo 6.
- **Topes de `KdfParams`:** la tarea pedía validar solo los mínimos de OWASP; agregué los topes para que un servidor malicioso no pueda congelar la app.
- **`.claude/rules/crypto.md` pide envolver los secretos en `SecureBytes`.** La tarea pide firmas con `ByteArray` y seguí la tarea.
- **Pruebas del store:** el `UnlockAttemptStoreTest` unitario usa un DataStore en memoria; la prueba con archivo real es instrumentada.

## Dudas o decisiones que tomé
- **Parámetros finales (PEND-01):**
  - Falta la tabla del teléfono más modesto.
  - En el emulador (depurable), 64 MiB/t=3 tarda 1,7 s, y 19 MiB/t=2 y 46 MiB/t=1 tardan 0,4–0,6 s.
  - ¿Agrego un `buildType` de medición no depurable (`initWith(release)`, firma de depuración, sin R8) para medir como en release?
- **Piso de parámetros en el cliente (hallazgo 1):** al elegir los parámetros, ¿subo el mínimo de `KdfParams` a esos valores? ¿Y `account` guarda los parámetros y la sal de cada cuenta para rechazar una bajada en el prelogin?
- **`SecureBytes`:** el revisor recomienda `ByteArray` solo dentro de las primitivas de `core/crypto`, y `SecureBytes` (`toString` «***», `wipe`, `AutoCloseable` y error si se lee después de borrar) en cuanto un secreto sale de ahí, empezando por `VaultSession`. ¿Se ajusta así `.claude/rules/crypto.md`?
- **Tema aparte:** el bloqueo local se muestra con el mismo texto de DEC-32 («Demasiados intentos. Vuelve a intentarlo en N min.»).

## Revisión
- [x] **REQUIERE REVISIÓN HUMANA:** el Paso 3 es el primer código de `core/crypto`.
  - Revisar `KdfParams`, `Argon2idKdf`, `Hkdf`, `MasterPasswordEncoder` y sus vectores.
  - El vector propio se puede volver a calcular con `tools/vectors/master_key_chain.py`.
  - Los Pasos 1 y 3 pasaron por `security-reviewer`.
