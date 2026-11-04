# Tarea 19 · Dispositivos y actividad (SCR-33, 34, 41, 42, 45; OP-29, OP-30; EP-23…25)

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 18
Aprobada. El cambio de contraseña está bien hecho:
- la misma `vault_key` se vuelve a envolver con sal, nonce y AAD nuevos, sin volver a cifrar los elementos;
- `NonCancellable` desde que se manda EP-17 hasta que se guarda en Room (A1);
- `updateKeys` con `WHERE user_id` y exigiendo una fila;
- la huella se desactiva antes de volver a envolverla;
- las llaves se borran en `finally`.

También queda aprobado el Paso 0: sin diálogo automático de huella, Ajustes congelado al cerrar sesión, portapapeles al bloquear y lo demás. Aprobadas tus desviaciones (resultado por `SavedStateHandle` con 1 o 2, `ProtectedVaultKey.wrap` con `SecretReader`, `AuthNotices`).

## Paso 0 · Lo que quedó abierto en la tarea 18 (decidido)
1. **Recuperación si el proceso muere entre el 200 y `updateKeys`.** Se acepta la propuesta de `security-reviewer`, con estas condiciones. En `login()`, si la sal del servidor es distinta de la local, **no** se rechaza de entrada (`downgradesKnownAccount`). Se acepta la cuenta nueva **solo si se cumplen las tres**:
   - (a) los parámetros KDF del servidor no son menores que los locales ni que el piso de DEC-05;
   - (b) la `key_version` del servidor es **mayor** que la local;
   - (c) la pvk del servidor **abre** con la `enc_key` derivada y la AAD de esa `key_version`, y la `vault_key` resultante es **la misma** que abre los elementos locales (comprueba descifrando un elemento local, si hay alguno).

   Si se cumplen, se reemplaza `account` y **se conservan** los elementos y los cambios sin subir (la `vault_key` no cambió). Si falla cualquiera, se mantiene el rechazo actual. Pruebas para cada condición. **REQUIERE REVISIÓN HUMANA.**
2. **Reautenticación «incorrecta» con la fila local vieja.** Cuando una reautenticación responde `reauth_required`, consulta `getAccount()` (o el `keyVersion` que ya venga en la respuesta). Si la `key_version` del servidor es mayor que la local, muestra el mensaje del 409 («Tu contraseña maestra cambió en otro dispositivo…») en lugar de «incorrectos».
3. Las contraseñas como `String`: se mantiene (DEC-27).

## Objetivo
Ver los dispositivos con sesión abierta, cerrar sesión en uno o en todos los demás, y ver la actividad reciente de la cuenta. Todo viene del servidor (el falso por ahora).

**Figma:**
- Dispositivos `47:1817`, cargando `69:3411`, sin conexión `69:3850`;
- Actividad `47:1938`, cargando `69:3513`;
- hoja de confirmación: usa el diseño de cerrar sesión (`215:3811`) con los textos de abajo.

## Paso 1 · Dispositivos (SCR-33, OP-30; EP-23, EP-24)
- Título en dos pesos «**N dispositivos** con tu sesión abierta» (singular «1 dispositivo»).
- **Filas:**
  - ícono de teléfono, `deviceName` y, debajo, el tiempo: «Activo ahora» (la actual o usada hace menos de 5 min), «Hace N min/h/días/semanas» desde `lastUsedAt`;
  - la actual primero, con «Este» y **sin flecha ni acción**;
  - el «Android 14» de Figma solo aparece si viene dentro de `deviceName`: no lo inventes.
- **Tocar otra fila:** hoja «¿Cerrar sesión en {nombre}?», «Tendrá que volver a entrar con tu correo y tu contraseña maestra.», «Cerrar sesión» (peligro) y «Cancelar». Al confirmar, EP-24, se quita de la lista y sale `MessageBar` «Cerraste la sesión en {nombre}».
- **«Cerrar sesión en los demás»** (solo si hay más de uno): hoja «¿Cerrar sesión en los demás dispositivos?», «Solo este teléfono seguirá con tu sesión abierta.» El contrato no tiene un endpoint para todos: llama EP-24 por cada uno, en serie. Si alguno falla, deja en la lista los que no se cerraron y avisa «No pudimos cerrar la sesión en N dispositivos. Intenta de nuevo.».
- Texto de pie de Figma: «Toca un dispositivo para cerrar su sesión. Si no reconoces alguno, cambia tu contraseña maestra.» Haz «cambia tu contraseña maestra» un enlace a SCR-35.
- **Cargando** (`69:3411`) con esqueleto. **Sin conexión** (`69:3850`) con «Reintentar».
- En Ajustes, «Dispositivos» deja de decir «Disponible pronto» y al volver se recarga el número.

## Paso 2 · Actividad reciente (SCR-34; EP-25)
- **Agrupada por día local:** «Hoy», «Ayer» y luego la fecha («lunes 2 de noviembre»). La hora va a la derecha en formato del teléfono.
- **Título e ícono por `eventType`** (los 18 del contrato). Usa esta tabla y para un tipo desconocido muestra «Actividad en tu cuenta»:

| `eventType` | Título | Ícono |
|---|---|---|
| `account_registered` | Cuenta creada | user |
| `email_verification_sent` | Código de verificación enviado | mail |
| `email_verified` | Correo verificado | mail |
| `login` | Inicio de sesión | log-out (girado) / el de Figma |
| `totp_verification` | Código de verificación usado | shield-check |
| `recovery_code_login` | Entrada con código de respaldo | key |
| `account_locked` | Cuenta bloqueada por intentos | alert |
| `token_reuse_detected` | Sesión cerrada por seguridad | shield-alert |
| `logout` | Cierre de sesión | log-out |
| `session_revoked` | Sesión cerrada en un dispositivo | smartphone |
| `master_password_changed` | Contraseña maestra cambiada | key |
| `email_change_requested` | Cambio de correo solicitado | mail |
| `email_changed` | Correo cambiado | mail |
| `two_factor_enabled` | Verificación activada | shield-check |
| `two_factor_disabled` | Verificación desactivada | shield-alert |
| `recovery_codes_regenerated` | Códigos de respaldo nuevos | key |
| `account_deleted` | Eliminación de cuenta solicitada | trash |
| `account_anonymized` | Cuenta anonimizada | trash |

- **Fallidos** (`success = false`): ícono de alerta y título en `danger`. Para `login` fallido el título es «Acceso fallido».
- Subtítulo: `deviceName` o «Dispositivo desconocido».
- **Las filas no se tocan:** quita las flechas de Figma, porque no hay pantalla de detalle. Los títulos largos se cortan en una línea con «…» y la hora no se mueve.
- **Paginación:** al llegar al final, si `hasMore`, pide la siguiente página con `before` = id del último (50 por página). Muestra un indicador pequeño mientras carga.
- Pie: «¿No reconoces algo? **Cambia tu contraseña**» → SCR-35.
- Cargando (`69:3513`) con esqueleto. Sin conexión: el mismo patrón de Dispositivos.
- En Ajustes, «Actividad reciente» deja de decir «Disponible pronto».

## Reglas
- Dentro de `RequireUnlocked`; los ViewModel se vacían al bloquear. No muestran secretos, así que no necesitan `SecureScreen`.
- Si al cerrar una sesión el servidor responde que la sesión **propia** ya no es válida (`session_revoked`), el flujo de siempre cierra sesión local.
- Comprueba que `FakeServer` registra en la actividad los eventos de las tareas 10–18 (login, 2FA, cambio de contraseña, cierre remoto) con su `deviceName`. Si falta alguno, complétalo **solo en el backend falso**.

## Terminado cuando
- **Paso 0:** pruebas de las tres condiciones de la recuperación (cada una falla por separado y la combinación válida conserva los elementos y los pendientes), y del mensaje con `key_version` mayor.
- **Pruebas de ViewModel:**
  - Dispositivos: lista ordenada, «Este», tiempos relativos, cerrar uno, cerrar los demás con éxito parcial, sin red y cargando;
  - Actividad: agrupación por día (incluido el cambio de día en medianoche local), los 18 tipos y el desconocido, fallidos, paginación sin duplicados, sin red.
- **Pruebas de Compose:** textos de Figma; la hoja de cerrar uno y la de cerrar los demás; plural de «dispositivo(s)»; filas de actividad sin acción de clic.
- **En el emulador:** con dos sesiones del backend falso (`adb` o una cuenta abierta en dos emuladores, lo que sea más simple), cierra la otra y comprueba la actividad. Lo que no se pueda, al reporte como prueba a mano.
- `security-reviewer` revisa el Paso 0. **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
