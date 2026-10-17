# Tarea 07 · Cierre de DEC-31, bloqueo local y primer código de `core/crypto` (Argon2id con Bouncy Castle)

**Estado:** hecha
**Escrita:** 2026-10-17 · sesión de diseño

## Revisión de la tarea 06
Aprobada. Revisar la edad Unicode también en el texto crudo (caso U+1FBF0) y la prueba instrumentada con el ICU real
fueron aciertos. También aprobados:
- vaciar el campo tras «Contraseña incorrecta»;
- «¿No eres tú?» sin nombre;
- la hoja de cerrar sesión con los botones de peligro y secundario;
- `buildConfig = true`;
- el candado deshabilitado.

Lo del tiempo de pantalla del emulador no importa. Respuestas (grafo v1.3.7):
- **Hallazgo 5:** se quitan U+FE0E/FE0F **antes** de NFKC.
- **Hallazgo 4:** se rechazan también Cf no ignorables (U+FFF9–FFFB), uso privado (Co) y no-caracteres (U+FDD0–FDEF y los que terminan en FFFE/FFFF).
- **Hallazgo 3:** no hay reglas de contexto. ZWJ, ZWNJ y etiquetas se aceptan en cualquier posición: es un riesgo aceptado, porque no cambia la llave entre teléfonos.
- **Hallazgo 8:** el mensaje pasa a «Tu contraseña tiene caracteres que no se pueden usar (invisibles o emojis muy nuevos)».
- **Hallazgo 11:** queda como riesgo conocido en DEC-31.
- **Hallazgo 2 (accesibilidad en API 28–33):** se queda como riesgo aceptado (DEC-26). No se toca `PasswordField`.
- **Hallazgo 6, bloqueo local (DEC-08):**
  - tras 5 fallos seguidos, espera de 1 min que se duplica con cada fallo siguiente, hasta 15 min; un acierto lo reinicia;
  - se guarda en DataStore: fallos y fin del bloqueo en reloj de pared y en `elapsedRealtime`;
  - mientras el teléfono no se reinicie manda `elapsedRealtime`, así que cambiar la hora no sirve; tras un reinicio manda el reloj de pared.
- **Argon2 (PEND-01):** la librería es **Bouncy Castle** (`org.bouncycastle:bcprov-jdk18on`), en Java puro, también para HKDF. Falta medir los parámetros, que es el Paso 3.

## Paso 1 · Ajustes de DEC-31
- Normalizador: FE0E/FE0F antes de NFKC. Política: Co, no-caracteres y U+FFF9–FFFB. Mensaje nuevo.
- Pruebas: «A»+FE0F+U+030A da «Å»; U+E000, U+FDD0, U+FFFE, U+1FFFE y U+FFF9 se rechazan.

## Paso 2 · Bloqueo local de Desbloquear (DEC-08)
- `UnlockAttemptStore` en `core/datastore`, con las claves `unlock_failures`, `unlock_locked_until_wall` y `unlock_locked_until_elapsed`. Agrégalas a KV-DATASTORE en tu reporte y yo las registro.
- Las fuentes de reloj de pared y de `elapsedRealtime` se inyectan, para probarlas.
- Pruebas: 5 fallos → 1 min; el 6.º → 2 min; tope de 15; acierto reinicia; cambiar el reloj de pared hacia adelante no acorta el bloqueo mientras `elapsedRealtime` sea coherente; tras un «reinicio» simulado (`elapsedRealtime` menor que el guardado) se usa el reloj de pared.

## Paso 3 · `core/crypto`: Argon2id y HKDF con Bouncy Castle (DEC-05, DEC-28)
**Es código cripto: presenta primero un plan corto en el reporte intermedio o en el chat y espera el visto bueno de la persona.**
- Agrega `bcprov-jdk18on` (la versión estable más reciente) a `libs.versions.toml`. Verifica con AGP 9 y R8; si R8 pide reglas, usa `-dontwarn` solo para lo que falte y explica por qué.
- **`Argon2idKdf`** (`core/crypto`): `derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray` de 32 bytes, con `Argon2BytesGenerator`, `ARGON2_id` y versión `0x13`. `KdfParams(memoryKiB, iterations, parallelism)` valida los mínimos de OWASP y rechaza el resto.
- **`Hkdf`**: HKDF-SHA256 con `HKDFBytesGenerator`, más las funciones `encKey(masterKey)` y `authKey(masterKey)` con los `info` de DEC-05 (`end-security/enc` y `end-security/auth`).
- **`MasterPasswordEncoder`** (`core/crypto` o dominio): texto normalizado → UTF-8 en `ByteArray` con `CodingErrorAction.REPORT`. Sobrescribe los búferes intermedios.
- Todos los `ByteArray` de llaves se sobrescriben en `finally` cuando ya no se usan, y las clases no exponen secretos en `toString()`.
- **Pruebas con vectores oficiales:**
  - **RFC 9106 §5.3** (Argon2id, con secreto y datos asociados);
  - **RFC 5869** casos 1 a 3 (HKDF-SHA256);
  - un vector propio de la cadena completa (contraseña → `master_key` → `enc_key`/`auth_key`), fijado en la prueba para detectar cualquier cambio futuro de normalización, codificación o librería.
- **No conectes nada a las pantallas todavía.**

## Paso 4 · Medición (PEND-01)
- Prueba instrumentada `Argon2BenchmarkTest`, sin interfaz. Mide `Argon2idKdf` con p = 1 en tres configuraciones:
  - 19 MiB y t = 2;
  - 46 MiB y t = 1;
  - 64 MiB y t = 3.

  Para cada una hace 1 calentamiento y 5 corridas, y registra la mediana y la peor en milisegundos, junto con el modelo del teléfono, la versión de Android y `Runtime.maxMemory()`. Si hay `OutOfMemoryError`, lo atrapa y lo reporta.
- Córrela en el emulador y pide a la persona que la corra en **el teléfono más modesto del equipo**. La salida va en el reporte como tabla. Yo elijo los parámetros con esos números.

## Terminado cuando
- Pasan las pruebas unitarias e instrumentadas, y `assembleRelease` pasa con Bouncy Castle.
- `security-reviewer` revisa los Pasos 1 y 3. **REQUIERE REVISIÓN HUMANA** del Paso 3.
- Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md` (con la tabla de la medición), cambia el estado de arriba a `hecha` y detente.
