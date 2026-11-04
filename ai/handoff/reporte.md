# Reporte · Tarea 18 · Cambiar la contraseña maestra (SCR-35; OP-27; EP-17)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** el cambio completo (`ChangeMasterPasswordImpl`: reautenticación, sal y llaves nuevas, re-envoltura de la misma `vault_key`, EP-17 y guardado en Room), `ReauthRunnerImpl`, `AccountDao.updateKeys` (SQL) y el re-envoltorio de la huella. Pasó por el subagente `security-reviewer` (ver abajo); **A1 y M1 quedaron corregidos pero conviene que una persona los lea**.

## Qué se hizo

### Paso 0 · Ajustes de la prueba en el teléfono y de la tarea 17
1. **Sin diálogo automático de huella (DEC-09):** Desbloquear se muestra completa (título, contraseña y botón de huella) y `BiometricPrompt` se abre **solo al tocar el botón**, en todos los casos. Se quitó la lógica de «una vez por bloqueo» y sus pruebas; hay una prueba nueva de que entrar a Desbloquear no abre el diálogo.
2. **«Abrir app de autenticación»** usa `Intent.createChooser`.
3. **Salir del Paso 3 por una pestaña:** hoja «¿Ya guardaste tus códigos?» / «No podrás volver a verlos…» con «Salir» y «Volver a los códigos». «Ya los guardé» y Atrás siguen igual.
4. **Portapapeles al bloquear (DEC-25):** `SecretClipboard.clearOnLock` (registrado en `EndSecurityApp`) borra lo copiado si sigue ahí, con la misma regla que el borrado por tiempo.
5. **Bloqueo por intentos:** `lockoutCountdown.start` sale de `_state.update` en el ViewModel de administrar 2FA; el `Retry-After` de la tarea 17 ya no se redondea a minutos y, con menos de 60 s, el mensaje dice «…en N s.».
6. **`establish`:** si `accountDao.replace` falla tras guardar los tokens, los borra antes de propagar el error.
7. **Ajustes y Desbloquear no parpadean al cerrar sesión:** en cuanto se confirma, el ViewModel pasa a `isSigningOut` y **congela** lo que se ve (huella, 2FA, dispositivos, cuenta, autobloqueo); la hoja sigue abierta con su botón cargando hasta navegar y no se puede cerrar.

### Pasos 1 y 2 · Contraseña maestra (SCR-35, OP-27)
- **Pantalla** (`ChangePasswordScreen`, Figma `47:2098`): aviso oscuro, «Contraseña actual», «Código de tu app» **solo con 2FA activa**, «Nueva contraseña» con el medidor, «Confírmala» y la lista de requisitos (`RequirementLine`): «12 caracteres o más», «Una mayúscula», «Una minúscula», «Un número», «Las contraseñas coinciden» y **«No aparece en filtraciones»**. Botón siempre activo, errores y foco como en la tarea 15, `SecureScreen` y `ExcludeFromAutofill`; las contraseñas viven solo en `TextFieldState`.
- **HIBP (DEC-21):** solo al tocar el botón (antes la línea va en gris). Con filtración: tache y «Esta contraseña apareció en filtraciones. Elige otra.» y **bloquea**; sin red o sin respuesta: «No se pudo revisar (sin conexión)» y **no** bloquea. Solo sale el prefijo del SHA-1 (con relleno).
- **«Debe ser distinta de la actual.»** (texto normalizado).
- **Cambio (`ChangeMasterPasswordImpl`):** (1) reautenticación con la `auth_key` actual (`ReauthRunner`) y el TOTP si hay 2FA; (2) sal nueva de 16 B (`SecureRandom`) y parámetros KDF (por defecto, nunca menos que los de la cuenta) → `master_key` → `enc_key` y `auth_key` nuevas; (3) **la misma `vault_key`** de `VaultSession` envuelta con la `enc_key` nueva, nonce nuevo y AAD `end-security/vault-key|userId|keyVersion+1` (los elementos no se vuelven a cifrar); (4) EP-17 con `expectedKeyVersion`; (5) con 200, **una sola sentencia de Room** (`AccountDao.updateKeys`, solo la cuenta de la sesión y exigiendo una fila) reemplaza sal, parámetros, pvk y `key_version`, con reintento y, si falla otra vez, `signOut()` + «Tu contraseña cambió. Entra con la nueva.» en Iniciar sesión (`AuthNotices`); (6) con la huella activa se desactiva todo y se abre `BiometricPrompt` («Confirma tu huella para seguir usándola») para volver a envolver; si se cancela o falla queda desactivada con «Desbloqueo con huella desactivado. Actívalo de nuevo en Ajustes.»; (7) vuelve a Ajustes con «Contraseña cambiada. Cerramos la sesión en tus otros dispositivos.».
- **Errores:** `reauth_required` → «Contraseña o código incorrectos.» (marca contraseña y código); 409 → «Tu contraseña maestra cambió en otro dispositivo…» sin tocar nada local; sin red o 5xx → el genérico; límite de intentos → bloqueo con su cuenta regresiva.
- **Memoria:** las llaves nuevas y la `auth_key` actual se borran en `finally`, también al cancelar (patrón M1 de la tarea 17). El botón queda cargando y Atrás está deshabilitado mientras dura.
- **Backend falso:** `FakeServer` ya cumplía EP-17 (reautenticación, 409, reemplazo de parámetros, `auth_hash` y pvk, `key_version` +1, otras sesiones revocadas); lo comprobé con pruebas y **no cambié nada**.

## Revisión con `security-reviewer`
No encontró fallos criptográficos (sal, nonce, AAD y la misma `vault_key` están bien). Lo que señaló, **corregido**:
- **A1 (alta) · cambio a medias por cancelación:** si se cancelaba (Atrás, bloqueo) entre el 200 de EP-17 y el guardado en Room, el servidor tenía llaves nuevas y el teléfono las viejas. **Corrección:** desde que se manda EP-17 hasta que se guarda en Room todo corre en un `NonCancellable`. Prueba nueva: cancelar a mitad deja servidor y teléfono con la misma `key_version`.
- **M1 · versión rara del servidor:** si devuelve una versión menor o igual (retroceder) o la bóveda se bloquea al volver a envolver, ahora se trata como «cambio que no se pudo guardar aquí» (cierre local con aviso) en vez de un error genérico, y se captura `IllegalArgumentException`.
- **M2 · huella huérfana:** tras guardar, con la huella activa se desactivan llave del Keystore, columnas y bandera **antes** del re-envoltorio, para que el estado guardado sea coherente aunque el proceso muera.
- **M3:** `updateKeys` lleva `WHERE user_id`, devuelve las filas afectadas (se exige 1) y toma `SessionLifecycleLock`.
- **M4:** `signOut()` en Ajustes y Desbloquear atrapa el fallo del borrado local y navega igual (antes se podía quedar congelado o cerrar la app).
- **Bajos corregidos:** B1 (si `authKey` falla tras `encKey`, esta se borra), B2 (no se bajan los parámetros de Argon2 de la cuenta), B3 (sin cuenta local ya no dice «contraseña débil»), B4 (tras el borrado por tiempo, bloquear no borra algo ajeno copiado después).

**Queda abierto, a decidir con la sesión de diseño:**
- **Recuperación si el proceso muere justo entre el 200 y `updateKeys`:** el servidor queda con llaves nuevas y el teléfono con las viejas; entrar con la contraseña nueva falla porque `login()` rechaza una sal distinta de la local (`downgradesKnownAccount`, hallazgo 1 de la tarea 07). La revisión propone permitir el cambio de sal cuando la pvk del servidor abra con la `enc_key` derivada. **No lo cambié** porque modifica una decisión de seguridad ya aprobada; hoy se recupera cerrando sesión (con riesgo de perder cambios sin subir) y entrando de nuevo. La ventana se cerró para cancelación y bloqueo, no para la muerte del proceso.
- Tras una reautenticación que falla con credenciales que el usuario cree correctas (fila local desactualizada por un cambio desde otro dispositivo) sale «incorrectos» y no el 409.
- Las contraseñas normalizadas viven como `String` durante la operación (limitación aceptada en DEC-27).

## Pruebas (salida real)
- **`testDebugUnitTest`: 749, 0 fallas.** Nuevas: `ChangeMasterPasswordImplTest` (**Argon2 real**, contra el backend falso: la contraseña **nueva** desbloquea y la vieja no contra la `account` local; los elementos no se re-cifran y la sesión sigue con la misma `vault_key`; la AAD usa la nueva `key_version`; servidor sube la versión y acepta la `auth_key` nueva; otras sesiones revocadas; `reauth_required`, 409 y sin red no cambian nada; falla de Room → reintento y luego cierre de sesión; con 2FA pide el TOTP; la huella se desactiva; cancelar no llama al servidor; política y «igual a la actual»), `ChangePasswordViewModelTest` (34: cada requisito, HIBP con filtración/sin filtración/sin red, igual a la actual, código solo con 2FA, errores y foco, `reauth_required`, 409, límite, huella, `toString` sin secretos), `SecretClipboardControllerTest` (+7), `UnlockViewModelTest`, `SettingsViewModelTest` y `LoginViewModelTest` (Paso 0).
- **`connectedDebugAndroidTest`: 119 pruebas.** Nuevas: `ChangePasswordScreenTest` (11: textos de Figma, los 6 requisitos, código solo con 2FA, filtración, sin conexión, igual a la actual, credenciales incorrectas una sola vez). Esta vez fallaron 0 de `ClipboardDeviceTest` (E3) y **1 intermitente**, `TwoFactorScreensTest.codigosNuevosUsanElDisenoDelPaso3…`, que **pasa sola** (las 15 de esa clase) y no tiene relación con esta tarea.
- **`lintDebug`: 0 errores. `assembleRelease`: compila.**

## En el emulador
Captura de `ChangePasswordScreenTest` (dibuja `ChangePasswordScreen` sin `SecureScreen`) comparada con Figma `47:2098`: aviso, campos, medidor, las seis líneas de requisitos y el botón coinciden; con filtración se marca el campo y la línea. **No recorrí por la interfaz de la app real** cambiar la contraseña de una cuenta de prueba, bloquear y desbloquear con la nueva y cerrar sesión y entrar con la nueva (la prueba de Compose contra la app real se cuelga, ver tarea 13); ese flujo, con Argon2 real y el backend falso, está cubierto por `ChangeMasterPasswordImplTest`. **Queda para probarlo a mano**, junto con la huella (M11).

## Decisiones y desviaciones a revisar
- El resultado del cambio vuelve a Ajustes por el `SavedStateHandle` de su entrada (1 = cambiada, 2 = cambiada y huella desactivada); ningún secreto viaja por ahí. Si la huella quedó desactivada, el segundo aviso sale 4,2 s después del primero.
- `ProtectedVaultKey.wrap` ahora acepta un `SecretReader` (la `vault_key` de la sesión es de solo lectura).
- `DangerButton` y `LabeledTextField` siguen con `loading` y `placeholder` de la tarea 17; `FlowScaffold` ganó `title`.
- `AuthNotices` es un avisador en memoria de un solo uso, solo para «Tu contraseña cambió…».

## Vistas / cambios (cómo llegar)
- **Contraseña maestra (SCR-35):** Ajustes → «Contraseña maestra».
- **Hoja al salir del Paso 3:** Ajustes → «Verificación en 2 pasos» (inactiva) → completar hasta los códigos → tocar una pestaña de la barra inferior.
- **Desbloquear sin diálogo automático:** bloquear con el candado de la Bóveda (con la huella activa).
