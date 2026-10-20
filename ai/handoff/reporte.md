# Reporte · Tarea 10 · Capa de API con backend falso y flujos reales de registro y acceso

**Estado:** hecha
**Fecha:** 2026-10-19 (el código empezó después de que la persona aprobó el plan en el chat, incluido el correo con `+2fa`)

**REQUIERE REVISIÓN HUMANA:** toca `core/crypto`, `core/session`, autenticación, tokens y SQL de Room.

## Qué se hizo

### Paso 1 · Capa de API
- **`core/common/ApiResult`:** `Success`/`Failure` y `ApiError` sellado con todos los `code` del contrato, más `NetworkUnavailable`.
- **Interfaces de dominio** con modelos de dominio (`SecureBytes`, `UUID`, `Instant`), no JSON:
  - `AuthApi` (EP-01…09);
  - `VaultApi` (EP-10…12);
  - `AccountApi` (EP-13…22);
  - `SessionApi` (EP-23…25).
  - Los modelos con secretos o tokens ocultan su `toString()`.
- **`core/network`:**
  - `dto/Dtos.kt` con los nombres del contrato. Los opcionales no se envían; `expectedVersion` sí se envía aunque sea `null`.
  - `Base64Url` sin relleno.
  - `ApiClient` sobre un `ApiTransport` intercambiable. Traduce cada `code` a `ApiError` y nunca usa el `title`. Un JSON que no se puede leer cuenta como `ServerError`.
  - `api/*Impl` para las cuatro interfaces.
- **`core/di/NetworkModule`:** elige el transporte con `BuildConfig.USE_FAKE_BACKEND`, que vale `true` en todas las compilaciones. Sin Retrofit.

### Paso 2 · Backend falso (`core/network/fake/`)
- **`FakeServer`** implementa EP-01…25 con las reglas de `docs/api/README.md`:
  - sal falsa por HMAC en prelogin;
  - 202 siempre en registro y reenvío;
  - código de correo: 15 min y 5 intentos; reenvío: 60 s entre envíos y 5 por hora;
  - `invalid_credentials` idéntico y con el mismo Argon2 para un correo que no existe; 5 fallos → 15 min;
  - reto del 2FA: 5 min y un solo uso; TOTP ±1 paso con anti-replay;
  - códigos de respaldo con HMAC;
  - rotación del refresh con detección de reúso;
  - versión esperada en la bóveda, `cursor_expired` y reautenticación.
- **`FakeTransport`:**
  - espera de 300–800 ms;
  - un `Mutex` hace de transacción;
  - el estado se guarda en `filesDir/fake_server.json` con `AtomicFile`.
- **Cuentas de depuración:**
  - un correo con `+2fa` se crea con 2FA, con el secreto fijo `ENDSECURITYDEBUGTOTPSECRETKEY234` y los códigos de respaldo `DBGA-0001`…`DBGA-0010`;
  - el código del correo se ve en Verificar correo («Código de prueba: …»), solo en compilaciones de depuración y solo en memoria.
  - Todo está documentado en **`docs/api/backend-falso.md`** (archivo nuevo).
- **Cripto nueva para el falso:**
  - `core/crypto/Hashes` (HMAC-SHA256/SHA1, SHA-256 y comparación en tiempo constante) y `RandomBytes` (`SecureRandom`);
  - `Totp` (RFC 6238) y `Base32`;
  - `BackupCodeAlphabet`;
  - `Argon2idKdf.serverAuthHash` (19 MiB/t2), solo para el falso.

### Paso 3 · Tokens
- **`TokenKeyManager`:** llave AES-256-GCM del Keystore, alias `end_security_tokens`. Se borra al cerrar sesión.
- **`TokenStore`:** DataStore con los dos tokens cifrados. Si no se pueden descifrar, `read()` devuelve `null`.
- **`TokenManager`:**
  - caché en memoria y un solo `Mutex`, así que se renueva una vez a la vez (regla 9);
  - si otra llamada ya renovó, devuelve ese token;
  - `RefreshOutcome` tiene tres casos: `Renewed`, `Revoked` y `Unavailable` (sin red no es revocada);
  - `session_revoked` cierra la sesión local.

### Paso 4 · Registro y acceso reales
- **`AuthRepository` (dominio) y `AuthRepositoryImpl`:** aplican DEC-05.
  - **Registro:** `userId`, sal de 16 B, `KdfParams.DEFAULT`, `vault_key` y pvk.
  - **Acceso:**
    - piso del cliente y sal de 16 B;
    - rechaza la degradación respecto a una cuenta que ya está en el teléfono;
    - maneja 2FA, código de respaldo y `email_not_verified`.
  - Solo sale la `auth_key`. Argon2 corre fuera del hilo principal.
- **`PendingAuth`:** guarda en memoria la `vault_key` del registro y la `enc_key` del acceso mientras se completa el segundo paso o la verificación. Nunca van a disco ni a `SavedStateHandle`.
- **Pantallas conectadas:**
  - Crear cuenta, Verificar correo, Iniciar sesión, 2FA y Código de respaldo;
  - errores por `code`, botón con estado de carga;
  - «No pudimos conectar. Intenta de nuevo.» y «Demasiados intentos…» con los minutos.

### Paso 5 · Desbloquear real y pantallas protegidas
- **Desbloquear:**
  - lee `account` y cuenta el intento antes de Argon2, en `NonCancellable`;
  - abre la pvk y va directo a la Bóveda (los elementos ya están en Room);
  - si no hay cuenta, va a Iniciar sesión;
  - muestra el nombre real en «¿No eres …?»;
  - «Cerrar sesión» llama a `logout` aunque falle la red, borra tokens y Room, bloquea y va a Iniciar sesión.
- **`RequireUnlocked { }`** envuelve Bóveda · cargando y la Bóveda: con la bóveda bloqueada solo dibuja el fondo en ese mismo cuadro.
  - **Cambio respecto a lo pedido:** también avisa a `LockGuard`. Si Android restaura la pila tras matar el proceso, la pantalla aparece ya bloqueada, sin el paso de abierta a bloqueada que `LockGuard` espera. Sin el aviso, se quedaba en blanco. Probado en el emulador.
- **Desbloquear entra sin transición**, y las rutas protegidas salen sin transición.
- **Bóveda · cargando** hace la descarga inicial real a Room (paginada, solo bloques cifrados, lápidas fuera) y después va a la Bóveda. **Se quitó la navegación de 2 s.**
- **Bóveda (marcador):** muestra «Te quedan N códigos de respaldo» si entraste con uno y quedan 3 o menos. Su estado se vacía al bloquearse.
- **Arranque:**
  - con cuenta → Desbloquear;
  - sin cuenta y con el onboarding visto → Iniciar sesión;
  - si no → onboarding.
- **`AutoLocker.onTrimMemory`:** con `level ≥ TRIM_MEMORY_BACKGROUND` y la app en segundo plano, bloquea de inmediato.

### Revisión con `security-reviewer`
No encontró nada crítico.

**Corregí en esta tarea:**
1. **(Alto) Las llaves pendientes sobrevivían a su plazo.**
   - Ahora `PendingAuth` las borra al vencer, con un temporizador real, aunque nadie las vuelva a pedir.
   - También al pedir memoria el sistema en segundo plano, y al salir de Verificar correo (`onCleared`).
   - El plazo del registro baja de 60 a 15 min.
   - **No** las borro solo por pasar a segundo plano: el usuario sale a leer el código del correo o de su app autenticadora.
2. **(Medio) Al cancelar durante Argon2 o la red, las llaves derivadas no se borraban.**
   - `derive` ya no se cancela a la mitad y borra si quien llamó se canceló.
   - `login` borra la `enc_key` en un `finally` salvo que pase a `PendingAuth`.
   - `establish` y el final de `unlock` corren sin cancelarse y borran la llave si no llegó a la sesión.
3. **(Medio) Una renovación cancelada podía parecer reúso y cerrar la sesión.** Ahora `refresh` y el guardado corren en `NonCancellable`, y el par nuevo se guarda primero en memoria.
4. **(Bajo) Varios arreglos rápidos:**
   - el registro guarda en `account` la sal, los parámetros y la pvk generados en el teléfono, no los que devuelve el servidor;
   - las cuentas `+2fa` solo existen en depuración;
   - `SecureScreen()` en 2FA y Verificar correo;
   - la descarga falla si el servidor no avanza el cursor (evita el ciclo infinito);
   - `TokenStore.read()` tolera un Keystore inválido;
   - `applyRemote` no pisa ni borra filas `pending`/`conflict`. Marcarlas como conflicto queda para OP-33.
   - `EndSecurityApp` crea `AuthRepository` al arrancar, para que siempre haya quien escuche `session_revoked`.

**Quedan abiertos (necesitan decisión):**
- **(Medio) La contraseña maestra aparece en claro en el árbol de accesibilidad.** El `BasicSecureTextField` de `PasswordField` (tarea 05) publica el texto real aunque esté oculto. Lo confirmé con `uiautomator dump` en el emulador (API 36).
  - En Android 14+ cualquier servicio que se declare herramienta de accesibilidad lo puede leer; DEC-26 solo acepta el riesgo hasta Android 13.
  - Propuesta: publicar una máscara en la semántica del campo cuando está oculto, o ampliar el riesgo aceptado en DEC-26. No lo cambié porque es un componente de otra tarea y no es trivial.
- **(Bajo) `fake_server.json` guarda el pepper, el secreto de prelogin y el secreto TOTP en claro.** El servidor real los tendría en variables de entorno o cifrados. La tarea pedía «solo lo que guardaría el servidor real».
  - Con el archivo (que solo se puede leer con root o una compilación depurable) se pueden probar sin conexión los códigos de correo y de respaldo.
  - Quitarlos rompería los códigos guardados al reiniciar; se podrían cifrar con el Keystore. ¿Lo hago en una tarea aparte?
- **(Bajo) El falso calcula Argon2 dentro de su `Mutex`:** las peticiones esperan detrás de cada inicio de sesión. Es fidelidad del simulador, no seguridad del cliente.
- **(Bajo) Si se emite `session_revoked` cuando no hay tokens o no se pueden descifrar,** no se cierra la sesión local; solo se responde `SessionRevoked`. Hoy no hay pantallas que lo provoquen.

## Prueba de punta a punta (emulador Pixel 7 Pro, API 36)
- **Registro completo:** registro → «Código de prueba» → Bóveda · cargando → Bóveda → cerrar la app (`force-stop`) → **Desbloquear** (incorrecta: «Contraseña incorrecta.»; correcta: Bóveda) → Cerrar sesión → Iniciar sesión → Bóveda. ✔
  - Después de los arreglos de la revisión lo repetí con una cuenta nueva: registro → Bóveda → cerrar la app → Desbloquear. ✔
- **Muerte del proceso:** con la Bóveda abierta, Inicio y `am kill`; al volver abre Desbloquear, no una pantalla en blanco ni la Bóveda. ✔
- **Con 2FA:** `ana+2fa@correo.com` → Iniciar sesión → 2FA con el código de la app autenticadora (calculado con el secreto de depuración) → Bóveda · cargando → Bóveda. ✔
- **Errores:**
  - un correo que no existe da «Correo o contraseña incorrectos.» en 6,0 s;
  - una contraseña incorrecta de una cuenta real da el mismo mensaje en ~5,9 s;
  - tras el quinto fallo, el siguiente intento muestra «Demasiados intentos. Vuelve a intentarlo en 15 min.» ✔
  - El contrato dice «5 fallos → 15 min»: el quinto fallo todavía responde `invalid_credentials` y el bloqueo aparece al intentar otra vez. Si la UI debe mostrarlo ya en el quinto, falta un dato en el contrato.
- **Incidente del emulador (no es de la app):** con `-gpu host`, el diálogo «no responde» apareció tras horas de uso y ráfagas de teclas.
  - La traza mostraba el hilo principal esperando a `RenderThread`, atascado en `qemu_pipe_read` de `libEGL_emulation`.
  - Lo resolví reiniciando el emulador y el servidor de adb.

## Compilación y pruebas (salida real)
- **`testDebugUnitTest`:** 312 pruebas, 0 fallas. Nuevas o cambiadas:
  - `FakeBackendTest` (cada `code`, renovación y reúso, bóveda, `cursor_expired`, sin secretos en el estado);
  - `DtoContractTest` (ejemplos JSON escritos a mano del contrato);
  - `TotpAndHashesTest` (RFC 6238, RFC 4231 y Base32);
  - `PendingAuthTest` (vence a los 15/10 min sin que nadie la pida, memoria escasa, entrega a la sesión);
  - ViewModels conectados, con un `FakeAuthRepository`: Crear cuenta, Verificar correo, Iniciar sesión, 2FA, Código de respaldo, Desbloquear, Bóveda · cargando y Bóveda;
  - `StartDestinationViewModelTest` y `LockGuardViewModelTest` (pantalla restaurada ya bloqueada).
- **`connectedDebugAndroidTest`** (emulador): 33 pruebas, 0 fallas, entre ellas:
  - `TokenStoreDeviceTest` con el Keystore real;
  - `DaoTest.applyRemote_no_pisa_ni_borra_filas_con_cambios_locales`.
- **`assembleRelease` y `lintDebug`:** `BUILD SUCCESSFUL`, lint con 0 errores. Las 11 advertencias son de antes (versiones nuevas, `PluralsCandidate`, `RedundantLabel`, `ModifierParameter`).

## Diferencias, dudas y observaciones del contrato
1. **`CLAUDE.md` contra la tarea:**
   - `CLAUDE.md` dice que los falsos van «en memoria, en `data/fake/`»;
   - la tarea pedía `core/network/fake/` y persistir en `fake_server.json`. Seguí la tarea.
   - El falso está al nivel del transporte, así que las cuatro `*ApiImpl` son las reales y solo cambia el transporte.
   - ¿Ajustamos `CLAUDE.md`?
2. **Faltan datos en el contrato:**
   - no fija cuánto dura el código del correo (el falso usa 15 min);
   - `account_deleted` no está en el enum de `ActivityEvent`.
3. **El falso no implementa el límite por IP** (no hay IP), y el cambio de correo (EP-15/16) es mínimo.
4. **Con `USE_FAKE_BACKEND = true` en release** no se puede crear una cuenta, porque el código de prueba solo se ve en depuración.
5. **Desbloquear lleva a la Bóveda, no a Bóveda · cargando,** porque ya está todo en Room. La sincronización al abrir llega con OP-33.
6. **Archivos basura fuera del repo:**
   - la causa eran escrituras de archivos en paralelo;
   - desde entonces escribo de uno en uno y solo dentro del repo o del scratchpad;
   - en esta tarea no apareció ninguno.

## Vistas nuevas o con cambios visibles
- **Verificar correo** muestra «Código de prueba: …» solo en depuración.
- **La Bóveda** muestra el aviso de códigos de respaldo restantes.
- **Los botones de Crear cuenta, Iniciar sesión, 2FA, respaldo y Desbloquear** muestran un indicador mientras trabajan.
- **No hay pantallas nuevas.**
