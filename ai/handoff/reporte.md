# Reporte · Tarea 08 · Ícono, medición en release, `SecureBytes` y AES-256-GCM

**Estado:** hecha
**Fecha:** 2026-10-18

## Qué se hizo

### Paso 1 · Ícono (DEC-59)
- **Instalación:** recursos copiados según `LEEME.md`. Se borraron el fondo de la plantilla y los 10 `.webp`; `colors.xml` quedó solo con `ic_launcher_background`.
  - Ya está en el commit `6330eb6` (`feat: ícono de la app (DEC-59)`), hecho por la persona.
  - La carpeta `ai/handoff/icono/` nunca se versionó; la borré después de comprobar que los recursos instalados son idénticos.
- **Comprobación en el emulador (Android 16):**
  - en el cajón, el escudo amarillo sobre fondo carbón;
  - con «Themed icons», el sistema tiñe la capa monocromática y la cerradura queda como hueco. El lanzador de Pixel solo tematiza la pantalla de inicio, así que para verlo puse el ícono ahí un momento;
  - en la pantalla de inicio de Android 12+, el ícono sobre su fondo.
  - Al terminar dejé el emulador como estaba.
  - Lint no da advertencias del ícono.

### Paso 2 · `buildType` `benchmark`
- **Configuración** en `app/build.gradle.kts`:
  - `initWith(release)`, `isDebuggable = false`, firma de depuración, sin R8 y `matchingFallbacks += "release"`;
  - además, `applicationIdSuffix = ".benchmark"` y `versionNameSuffix = "-benchmark"` (hallazgo 8). Así es otra app: no reemplaza ni la puede reemplazar la de release o debug, y no hace falta desinstalar End Security para medir.
- **Pruebas:** `testBuildType = "benchmark"` solo con `-PbenchmarkBuild`. Sin la propiedad, las pruebas siguen en debug.
- **AGP 9 lo instrumenta sin problema.** `aapt2` lo confirma: el APK es `com.luis.end_security.benchmark`, la prueba es `com.luis.end_security.benchmark.test` y apunta a ese paquete.
- **`Argon2BenchmarkTest`:** subí el calentamiento a **3 corridas por configuración**. Justo después de instalar, ART todavía no compila la app y la primera corrida salía de 3 a 6 veces más lenta (en el emulador, 746 ms contra 115 ms para 19 MiB/t2). Con 3 calentamientos los números salen estables desde la primera instalación.
- **Comando para la persona del equipo** (con un solo dispositivo conectado, o eligiéndolo con `ANDROID_SERIAL`):

  ```bash
  ./gradlew connectedBenchmarkAndroidTest -PbenchmarkBuild -Pandroid.testInstrumentationRunnerArguments.class=com.luis.end_security.core.crypto.Argon2BenchmarkTest
  ```

  La tabla queda en `adb logcat -s Argon2Benchmark:I` y en `app/build/outputs/androidTest-results/connected/benchmark/<dispositivo>/logcat-…medir_argon2id.txt`. **`assembleBenchmark` no se distribuye.**

**Medición** (la persona la corrió el 2026-10-18 con su Redmi y el emulador conectados; salió en los dos):

| Dispositivo | Memoria | t | p | Mediana (ms) | Peor (ms) | Corridas (ms) |
|---|---|---|---|---|---|---|
| **Xiaomi 23090RA98G (Redmi Note 13 Pro 5G)**, Android 14 (API 34), `maxMemory` 256 MiB, depurable: **no** | 19 MiB | 2 | 1 | 57 | 60 | 57, 56, 57, 58, 60 |
| | 46 MiB | 1 | 1 | 129 | 135 | 124, 135, 129, 132, 105 |
| | 64 MiB | 3 | 1 | 343 | 345 | 345, 343, 336, 340, 344 |
| Emulador Pixel 7 Pro, Android 16 (API 36), `maxMemory` 192 MiB, depurable: **no** | 19 MiB | 2 | 1 | 132 | 161 | 132, 161, 126, 135, 124 |
| | 46 MiB | 1 | 1 | 201 | 288 | 193, 201, 288, 210, 194 |
| | 64 MiB | 3 | 1 | 587 | 615 | 587, 596, 615, 584, 540 |

**Ojo:** el Redmi Note 13 Pro (Snapdragon 7s Gen 2) es de gama media-alta. Si el teléfono de la otra persona del equipo es más modesto, conviene medirlo también antes de elegir.

### Paso 3 · `SecureBytes` (DEC-27)
- **`SecureBytes`:**
  - copia defensiva (`copyOf`) y `use { bytes -> … }` sin sacar los bytes;
  - `wipe()`; `close()` hace `wipe()`; leer después lanza `IllegalStateException`;
  - `toString()` = `"SecureBytes(***)"`, `equals`/`hashCode` por identidad;
  - `adopt` (`internal`) toma arreglos recién creados en `core/crypto` sin copiarlos.
  - **`use` y `wipe` se excluyen** con un `ReentrantReadWriteLock` (hallazgo 1): el autobloqueo no puede borrar la llave mientras otro hilo cifra con ella. `wipe` primero marca y después borra.
- **`KeyDerivation`:** `deriveMasterKey(normalizada, sal, params)`, `encKey(SecureBytes)` y `authKey(SecureBytes)` devuelven `SecureBytes` y borran los intermedios. Dan el mismo vector propio de la tarea 07.
- **`.claude/rules/crypto.md`:** dice dónde va `ByteArray` (dentro de las primitivas) y dónde `SecureBytes`, más la advertencia sobre `use`.

### Paso 4 · AES-256-GCM (DEC-05, DEC-28, DEC-29), con el plan aprobado
- **`AesGcm`:** JCA `AES/GCM/NoPadding`, etiqueta de 128 bits y un `Cipher` nuevo por llamada.
  - `seal`: nonce de 12 B con `SecureRandom` dentro.
  - `sealWithNonce`: solo para los vectores.
  - `open`: `AuthenticationFailed` (sin mensaje ni causa) cuando falla la etiqueta, y también con un cifrado truncado, `BadPaddingException` o `IllegalBlockSizeException` (hallazgo 3).
  - Llave de 32 B **que no esté en ceros** (hallazgo 1, en `seal` y `open`) y nonce de 12 B.
- **`Sealed`:** guarda copias; `toString` solo da tamaños.
- **`VaultKey.generate()`:** 32 B de `SecureRandom` dentro de `SecureBytes`.
- **`ProtectedVaultKey`:**
  - AAD = `end-security/vault-key|userId|keyVersion` en UTF-8; rechaza un `userId` vacío o con `|`, y `keyVersion` debe ser ≥ 1 (como `users_key_version_chk`);
  - `wrap` exige una `vault_key` de 32 B que no esté en ceros (hallazgo 6);
  - `unwrap` exige un texto claro de 32 B que no esté en ceros; si no, borra y lanza `AuthenticationFailed`.
- **`app/lint.xml`:** `VisibleForTests` es error, así que `sealWithNonce`, `argon2id` y `sha256` no se pueden usar fuera de las pruebas (hallazgo 4).
- Nada está conectado a pantallas ni a Room.

## Compilación y pruebas (salida real)
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` y `lintDebug`: `BUILD SUCCESSFUL` y lint con 0 errores. `assembleBenchmark` también pasa.
- **206 pruebas unitarias, 0 fallas.** Nuevas:
  - **`AesGcmTest` (11):**
    - **McGrew-Viega casos 13, 14, 15 y 16** (los vectores de AES-256 con nonce de 96 bits que usa el CAVS de NIST);
    - alterar 1 bit del cifrado, la etiqueta, el nonce, la AAD o la llave → `AuthenticationFailed`;
    - **10 000** `seal` sin nonce repetido (la regla pide 10 000 y la tarea 1 000; hice 10 000);
    - truncado → `AuthenticationFailed`; llave en ceros rechazada; tamaños; `toString`.
  - **`ProtectedVaultKeyTest` (6):** ida y vuelta (`pvk` de 12 + 48 B); fallo con otro `userId`, otra `keyVersion` u otra `enc_key`; `pvk` alterada; texto de 16 B con la AAD correcta; `wrap` con una `vault_key` de 16 B o en ceros; `userId` con `|` o vacío.
  - **`SecureBytesTest` (9):** `toString`, copia defensiva, error tras `wipe`/`close`, la referencia prestada queda en ceros, **`wipe` espera a que termine un `use` en otro hilo**, identidad y `KeyDerivation` con el vector propio.
- **Instrumentadas en el emulador: 18, OK.** `AesGcmDeviceTest` (7):
  - **el proveedor es `AndroidOpenSSL` (Conscrypt)**;
  - casos 13, 14, 15 y 16;
  - alterar la etiqueta, el cifrado, el nonce o la AAD;
  - `pvk` de ida y vuelta.
- **Medición con el sufijo `.benchmark`:** no la pude repetir en el emulador porque se quedó sin espacio (340 MB libres, por debajo del mínimo de Android) y no instala nada. No borré otras apps del emulador. Comprobé con `aapt2` que los paquetes están bien. Ahora el emulador tampoco tiene End Security instalada.

## Revisión de seguridad (`security-reviewer`, Pasos 3 y 4, y vistazo al `benchmark`)
Sin hallazgos críticos. Confirmó el uso de JCA, los vectores 13 a 16, que `AuthenticationFailed` no filtra nada, que `open` no devuelve nada parcial y el `SecureRandom` compartido.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Alta | Carrera entre `use` y `wipe`: el autobloqueo podía borrar la llave mientras otro hilo cifraba, y se cifraría con ceros sin error | **Corregido:** `ReentrantReadWriteLock` (la bandera va antes de borrar); `AesGcm.seal`/`open` y `wrap`/`unwrap` rechazan llaves en ceros. Prueba con dos hilos |
| 2 | Media | El `use` miembro de `SecureBytes` sombrea al `AutoCloseable.use` de Kotlin; mi propia prueba no cerraba `enc_key` y `auth_key` | **Prueba corregida** con `try/finally`. **Renombrar** (`withBytes`/`read`) cambia la API que pidió la tarea: ver dudas |
| 3 | Baja | Un cifrado truncado lanzaba `IllegalArgumentException`; otras excepciones del proveedor salían tal cual | **Corregido:** todo eso es `AuthenticationFailed` |
| 4 | Baja | `internal` en un solo módulo no aísla `sealWithNonce` ni `adopt` | **En parte:** `VisibleForTests` es error de lint. Un módulo `:core:crypto` aislaría de verdad (ver dudas) |
| 5 | Baja | La AAD se ata al texto del `userId`: un UUID en mayúsculas daría `AuthenticationFailed`. Además, `toByteArray` cambia los surrogates sueltos por «?» | **No implementado:** recibir `UUID` y usar su forma canónica en minúsculas cambia DEC-05 (ver dudas) |
| 6 | Baja | `wrap` no validaba el tamaño de la `vault_key` | **Corregido** |
| 7 | Baja | Faltaban pruebas: 10 000 nonces, caso 15 y alteraciones en el teléfono, aserción del proveedor, `pvk` alterada, texto de 16 B, y un nombre engañoso | **Corregido** |
| 8 | Baja | `benchmark` tenía el mismo `applicationId` y la firma de depuración: si sale del equipo, se podría instalar encima un APK de depuración y conservar los datos | **Corregido:** `applicationIdSuffix = ".benchmark"`. No se distribuye |
| 9 | Info | Copias que no se pueden borrar: `SecretKeySpec`, el contexto nativo de Conscrypt y las copias JNI; `SecureBytes` sin `Cleaner` (necesita API 33) | **Documentado.** Conviene agregarlas a DEC-27 |
| 10 | Info | Nonce aleatorio de 96 bits frente al límite de 2^32 de NIST: con 10^7 cifrados por `vault_key`, la probabilidad de colisión es cercana a 2^-50 | Aceptable. Cuidar que la sincronización no vuelva a cifrar en masa |

## Diferencias con la tarea
- **Sufijo `.benchmark`:** no venía en la tarea; lo agregué por el hallazgo 8. El comando es el mismo.
- **3 calentamientos** en vez de 1.
- **10 000 nonces** en vez de 1 000, por `.claude/rules/crypto.md`.
- **Llaves en ceros:** `seal`/`open` las rechazan (las funciones públicas; los vectores 13 y 14 usan llave cero por `sealWithNonce`).

## Dudas o decisiones que tomé
- **Parámetros de Argon2 (PEND-01):** con la tabla del Redmi, hasta 64 MiB/t=3 tarda unos 0,34 s. ¿Hay un teléfono más modesto en el equipo que deba medirse antes de elegir?
- **Renombrar `SecureBytes.use` (hallazgo 2):** propongo `withBytes { }` para leer, y dejar `use` como el de Kotlin, que cierra. ¿Lo cambio?
- **`userId` en la AAD (hallazgo 5):** ¿la AAD usa `UUID.toString()` canónico (minúsculas, con guiones)? Cambia DEC-05.
- **Módulo `:core:crypto` (hallazgo 4):** aislaría `internal` de verdad. ¿Vale la pena antes de `VaultSession`?
- **DEC-27:** agregar a las limitaciones aceptadas `SecretKeySpec`, Conscrypt/JNI y la falta de `Cleaner` en minSdk 28.
- **Emulador lleno:** para seguir usándolo hay que ampliar su almacenamiento interno o borrar sus datos desde Device Manager, como en la sesión anterior.
- **Por error volví a crear un archivo basura** fuera del repo (`C:\Users\brian\UPchiapas\Securidad-tmp-unused.txt`, con una «x») y lo borré enseguida.

## Revisión
- [x] **REQUIERE REVISIÓN HUMANA** del Paso 4 (AES-GCM y `ProtectedVaultKey`) y del Paso 3 (`SecureBytes` con candado). Los dos pasaron por `security-reviewer`.
