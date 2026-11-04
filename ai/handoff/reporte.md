# Reporte · Tarea 19 · Dispositivos y actividad (SCR-33, 34, 41, 42, 45; OP-29, OP-30; EP-23…25)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** el Paso 0 toca autenticación: `ServerAccountCheck` (nuevo), `AuthRepositoryImpl.login/completeWithEncKey/establish`, `AccountDao`/`VaultItemDao.anySealed` (SQL) y `ChangeMasterPasswordImpl`. Pasó por el subagente `security-reviewer` (ver abajo); **M1, M2, B1, B2 y B4 quedaron corregidos pero conviene que una persona los lea**.

Antes de empezar se subió en un commit aparte lo que estaba sin commit de la sesión de diseño (estado, grafo, revisiones pendientes y el diagrama de la BD).

## Qué se hizo

### Paso 0 · Lo que quedó abierto en la tarea 18

1. **Recuperación si el proceso muere entre el 200 de EP-17 y `updateKeys`** (`ServerAccountCheck`, `AuthRepositoryImpl`). En `login()`, una sal del servidor distinta de la local ya no se rechaza de entrada. Con la cuenta de ese correo en el teléfono:
   - **Antes de derivar:** los parámetros KDF que manda el servidor no pueden costar menos que los locales (memoria, pasadas **ni paralelismo**) → si no, `ServerError` sin mandar nada.
   - **Se acepta la cuenta nueva solo si se cumplen las tres:** (a) parámetros no menores que los locales ni que el piso de DEC-05 (`KdfParams`); (b) `key_version` del servidor **mayor** que la local; (c) la pvk del servidor abre con la `enc_key` derivada y la `vault_key` resultante abre un elemento local (`VaultItemDao.anySealed()` + etiqueta GCM con la AAD `id|user_id|format_version`; sin elementos locales no hay nada que comprobar). Además debe ser **la misma cuenta** (mismo `userId` y mismo correo que dice el servidor).
   - Si se cumplen, `establish` reemplaza `account` **conservando elementos, pendientes y el cursor de sincronización** (la `vault_key` no cambió). Si falla cualquiera, igual que una pvk que no abre: se cierra la sesión que acaba de crearse en el servidor y sale el error genérico.
2. **Reautenticación «incorrecta» con la fila local vieja** (`ChangeMasterPasswordImpl`): cuando la reautenticación responde `reauth_required`, se consulta `getAccount()`; si la `key_version` del servidor es **mayor** que la local, el resultado es el del 409 («Tu contraseña maestra cambió en otro dispositivo…») y no «incorrectos». Sin red o con la misma versión se queda «incorrectos».
3. Las contraseñas como `String` (DEC-27): se mantiene.

### Paso 1 · Dispositivos (SCR-33; OP-29, OP-30; EP-23, EP-24)
- **Pantalla** (`DevicesScreen`, Figma `47:1817`): título en dos pesos «**N dispositivos** / con tu sesión abierta» (singular «1 dispositivo»); filas con ícono de teléfono, `deviceName` y «Activo ahora» (la actual o usada hace menos de 5 min) o «Hace N min/h días/semanas» (con plurales); **la actual primero, con «Este», sin flecha ni acción**; no se inventa «Android 14». Pie con «cambia tu contraseña maestra» como **enlace a SCR-35**.
- **Cerrar una:** tocar otra fila abre la hoja con el diseño de cerrar sesión (`215:3811`): «¿Cerrar sesión en {nombre}?», «Tendrá que volver a entrar con tu correo y tu contraseña maestra.», «Cerrar sesión» (peligro) y «Cancelar». Al confirmar, EP-24, la quita de la lista y sale `MessageBar` «Cerraste la sesión en {nombre}». Mientras corre, el botón carga y la hoja no se cierra.
- **«Cerrar sesión en los demás»** (solo si hay más de uno; `RevokeSessions`): EP-24 **en serie** por cada una (nunca la propia); si alguna falla las demás se intentan, quedan en la lista las que no se cerraron y avisa «No pudimos cerrar la sesión en N dispositivos. Intenta de nuevo.». Un `404` (ya no existía) cuenta como cerrada; un `session_revoked` de la sesión propia deja de llamar y el flujo de siempre cierra la sesión local.
- **Cargando** con esqueleto (`69:3411`) y «Cargando tus dispositivos…»; **sin conexión** (`69:3850`) con «Reintentar».
- **Ajustes:** «Dispositivos» abre la pantalla y al volver (`Resumed`) el número se recarga; sin red conserva el último.

### Paso 2 · Actividad reciente (SCR-34; EP-25)
- **Pantalla** (`ActivityScreen`, Figma `47:1938`): agrupada por **día local** («Hoy», «Ayer» y «lunes 2 de noviembre»; con otro año, también el año); la hora a la derecha en el formato del teléfono (12 o 24 h); «Hoy» pasa a «Ayer» **a la medianoche local** (temporizador en la ruta y al volver a la pantalla).
- **Título e ícono por `eventType`:** los 18 del contrato con la tabla de la tarea; uno desconocido dice «Actividad en tu cuenta». **Fallidos:** ícono de alerta y título en `danger`; `login` fallido es «Acceso fallido». Subtítulo: `deviceName` o «Dispositivo desconocido».
- **Filas sin acción** (sin flechas); títulos largos en una línea con «…» y la hora no se mueve. TalkBack lee «título, dispositivo, a las HH:MM» y «fallido».
- **Paginación:** `LazyColumn`; al llegar al final con `hasMore` pide la siguiente con `before` = id del último (50 por página), sin duplicados (`ActivityRules.appendPage`) y con indicador pequeño; si una página falla no se repite sola y ofrece «Reintentar».
- Pie «¿No reconoces algo? **Cambia tu contraseña**» → SCR-35. Cargando (`69:3513`) con esqueleto; sin conexión con el mismo patrón que Dispositivos.
- **Ajustes:** «Actividad reciente» abre la pantalla (ya no dice «Disponible pronto»).

### Otros cambios
- Nuevos componentes compartidos: `ListCard`/`ListRowDivider`/`ListRowIcon` (salieron de Ajustes, que ahora los usa) y `AccountListPage`, `OfflinePanel`, esqueletos y estado de carga en `feature/activity/ui`. Ícono `ic_wifi_off_40`.
- `TimeZoneProvider` (zona del teléfono inyectada) junto a `DeviceClock`.
- **`FakeServer`** (solo el backend falso): registra con su `deviceName` también `email_change_requested` (antes `null`). Ya registraban con dispositivo: `account_registered`, `email_verified`, `login`, `totp_verification`, `recovery_code_login`, `account_locked`, `token_reuse_detected`, `logout`, `session_revoked`, `master_password_changed`, `two_factor_enabled/disabled`, `recovery_codes_regenerated` y los fallidos. `email_changed`, `account_deleted` y `account_anonymized` no los genera todavía (EP-16/EP-18 están fuera del falso hasta la tarea 20).

## Revisión con `security-reviewer` (Paso 0)
No vio fugas de secretos ni uso de `Random`; la lógica de (a), (b) y (c) está bien pensada. Hallazgos y qué se hizo:
- **M1 (media) · el correo de la respuesta:** `isRecovery` identificaba la cuenta local por el `email` que dice el servidor; uno que lo alterara esquivaba (a), (b) y (c). **Corregido:** la cuenta local se identifica por el correo **que tecleó la persona** y la recuperación exige que el correo de la respuesta sea el de la cuenta local. Pruebas nuevas.
- **M2 (media) · `auth_key` con una sal que elige el servidor:** se manda antes de poder comprobar (b) y (c). **Mitigación 1 aplicada:** con otra sal que la local, **antes de mandar nada** se deriva con la sal **local** y se prueba la pvk local; si abre, la contraseña tecleada es la vigente en este teléfono y esa sal no es legítima → `ServerError` sin mandar nada (un Argon2 extra solo en ese caso raro). Queda como riesgo residual la ventana real de recuperación (la contraseña tecleada es la nueva y el servidor recibe su `auth_key` con la sal que quiera). Las mitigaciones 2 (límite de intentos de recuperación) y 3 (mezclar el `user_id` en la sal, cambio de contrato y de DEC-05) **quedan como propuestas** para la sesión de diseño.
- **B1 (baja):** la `vault_key` no se cerraba si `accountDao.get()` lanzaba. **Corregido:** `try/finally` con indicador de entrega desde que existe la llave.
- **B2 (baja) · carrera con el candado:** la decisión se tomaba fuera de `SessionLifecycleLock`. **Corregido:** `establish` vuelve a leer la fila **dentro** del candado y, en una recuperación, rechaza si ya no es la misma (usuario, `key_version`, sal); entonces se cierra la sesión del servidor y se borra la llave.
- **B3 (baja):** rechazar `key_version` menor que la local también en el acceso normal (misma sal). **No se aplicó:** tras restaurar el servidor desde una copia, esa regla dejaría fuera a quien tenga la cuenta en el teléfono sin forma de volver a entrar. A decidir con la sesión de diseño.
- **B4 (baja):** el paralelismo menor no se rechazaba. **Corregido** (se compara junto con memoria y pasadas).
- **B5 (informativa):** (c) es suficiente; no filtra información (los dos rechazos dan el mismo error).
- **Nota de alcance del revisor, para la sesión de diseño:** EP-17 revoca las **demás** sesiones, no la actual; tras la muerte del proceso el teléfono sigue con sus tokens y la pvk vieja y desbloquea con la contraseña **vieja**. La recuperación por `login()` ocurre cuando se llega al acceso con la fila de `account` todavía en Room (p. ej. `session_revoked` desde otro dispositivo… que hoy hace `clearAllTables` y **no** conserva los elementos). Conviene confirmar por qué camino llega la persona al login con la fila presente; si no hay ninguno, la recuperación es poco alcanzable. El desbloqueo local con la contraseña nueva falla y suma intentos a `UnlockAttemptStore`.

## Pruebas (salida real)
- **`testDebugUnitTest`: 830, 0 fallas** (antes 749). **`connectedDebugAndroidTest` (sin E3, `ClipboardDeviceTest`): 146, 0 fallas.** **`lintDebug`: sin errores. `assembleRelease`: compila.** Nuevas: `ServerAccountCheckTest` (21: cada condición a, b, c por separado, la combinación válida con elementos y pendientes, M1, B4), `ChangeMasterPasswordImplTest` (+3: key_version mayor, igual y menor), `ActivityRulesTest` (tiempo relativo, 18 tipos y el desconocido, agrupación por día local con 23:59/00:01, medianoche, páginas sin duplicados, `RevokeSessions`), `DevicesViewModelTest` (lista ordenada, «Este», tiempos relativos, cerrar uno, cerrar los demás con éxito total y parcial, sin red, cargando, hoja bloqueada mientras cierra), `ActivityViewModelTest` (agrupación, cambio de día, paginación sin duplicados, fallo de página y reintento, sin red), `ActivityPresentationTest` (la tabla de los 18 tipos), `TwoSessionsAgainstFakeServerTest` (dos sesiones contra el backend falso: ver las dos, cerrar la otra, la cerrada ya no vale, actividad con su `deviceName`, paginación) y `SettingsViewModelTest` (+2).
- **Compose (`DevicesAndActivityScreensTest`, 25):** textos de Figma, singular/plural de «dispositivo(s)», la actual sin acción, las hojas de cerrar uno y los demás, cargando, sin conexión, avisos, filas de actividad sin clic, título largo, tipo desconocido, paginación, reintento.
- **Integración (`LoginRecoveryIntegrationTest`, 6, en el emulador):** `login()` real contra el backend falso con Argon2 real, Room en memoria y el Keystore: la recuperación válida conserva elementos, pendientes y cursor y deja la misma `vault_key`; la contraseña vigente con sal ajena no manda nada; cada condición (b, c, otra cuenta, parámetros más bajos) rechaza, cierra la sesión del servidor y no toca lo local.

## En el emulador
Capturas de `DevicesAndActivityScreensTest` comparadas con Figma: Dispositivos (`47:1817`), cargando (`69:3411`), sin conexión (`69:3850`), Actividad (`47:1938`) y cargando (`69:3513`) coinciden (la «Este» sin flecha y la actividad sin flechas son lo pedido). La prueba de «dos sesiones» se hizo contra el backend falso en JVM (`TwoSessionsAgainstFakeServerTest`) en lugar de abrir dos emuladores. **No recorrí por la interfaz de la app real** Ajustes → Dispositivos → cerrar otra sesión → Actividad (la prueba de Compose contra la app real se cuelga, ver tarea 13): **queda como prueba a mano**, junto con la huella (M11) y el cambio de contraseña (tarea 18).

## Decisiones y desviaciones a revisar
- «Cerrar sesión en los demás»: éxito total avisa «Cerraste la sesión en los demás dispositivos» (el texto no estaba en la tarea). Un fallo de una sola sesión avisa «No pudimos cerrar la sesión en {nombre}. Intenta de nuevo.».
- Errores de carga de cualquier tipo (sin red, `session_revoked`, 5xx) muestran la misma pantalla de «sin conexión» (la de Figma no distingue).
- Una página de actividad sin eventos nuevos apaga `hasMore` para no pedirla sin fin.
- El texto de los dos enlaces del pie va como `LinkAnnotation` (TalkBack los anuncia como enlaces); su área táctil es la del texto.

## Vistas / cambios (cómo llegar)
- **Dispositivos (SCR-33):** Ajustes → «Dispositivos». Con más de uno: «Cerrar sesión en los demás» o tocar una fila.
- **Actividad reciente (SCR-34):** Ajustes → «Actividad reciente».
- **Contraseña maestra (SCR-35):** también desde el pie de Dispositivos y de Actividad.
