# Reporte · Tarea 17 · Verificación en dos pasos (SCR-30…32, SCR-43; OP-22…25)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** reautenticación (`ReauthRunnerImpl`: deriva la `auth_key` con Argon2 y la presta a `Reauth`), manejo de la clave TOTP y de los códigos de respaldo (`TwoFactorSetupViewModel`, `TwoFactorManageViewModel`), el nuevo `SessionLifecycleLock` en `AuthRepositoryImpl` y `BiometricUnlockImpl`, y la consulta SQL nueva `unsyncedCount`. Pasó por el subagente `security-reviewer` (ver abajo).

## Qué se hizo

### Paso 0 · Lo que quedó abierto en la tarea 16
- **Huella durante la espera por intentos:** sin cambio (DEC-08 cuenta solo la contraseña).
- **Diálogo automático:** sale **una vez por cada bloqueo** y solo con Desbloquear a la vista (`ScreenResumed`/`ScreenPaused` desde `ON_RESUME`/`ON_PAUSE`). Si el autobloqueo ocurre con la app en segundo plano, sale al volver; si se cancela, no se repite hasta el siguiente bloqueo.
- **«¿No eres …? → Cerrar sesión» en Desbloquear:** usa las mismas hojas que Ajustes (ahora en `core/designsystem/component/SignOutSheet.kt`, compartidas). El conteo de `pending`/`conflict` sale de la columna `sync_state` (`UnlockSettingsRepository.unsyncedCount`, sin descifrar) y, con cambios, el texto dice «Desbloquea tu bóveda y espera a que se suban…». Sin intento de sincronizar.
- **Ventana comprobar ↔ `session.unlock`:** `SessionLifecycleLock` (un `Mutex`) compartido por `signOutLocally`, `establish`, `unlock` con contraseña (que además comprueba que la cuenta siga en Room) y `completeUnlock` de la huella. Argon2 y el diálogo de la huella corren **fuera** del candado.

### Pasos 1–4 · Verificación en dos pasos
- **Entrada desde Ajustes:** «Verificación en 2 pasos» consulta el estado: inactiva → activación; activa → su pantalla; sin red → `MessageBar` «Necesitas conexión para cambiar la verificación en dos pasos.» y no navega. Al volver se recarga «Activa/Inactiva» (con una falla de red se conserva el último valor) y, tras desactivar, sale «Verificación en dos pasos desactivada».
- **Activar (OP-22, OP-23):** un solo destino (`TwoFactorSetupRoute`) con los tres pasos, así que al salir se vacía el ViewModel y con él la clave y los códigos.
  - *Cargando (SCR-43)* con esqueleto y «Generando tu clave…». Sin red: mensaje y «Reintentar».
  - *Paso 1:* clave Base32 en grupos de 4 (dos por línea), copiar con `SecretClipboard` y, si hay una app que abra `otpauth://` (`<queries>` en el manifiesto), «Abrir app de autenticación» con el URI completo armado por `OtpAuthUri` (`issuer`, `algorithm`, `digits`, `period` del servidor).
  - *Paso 2:* `CodeField` de 6 dígitos y «El código cambia en N s» con el reloj del teléfono; errores como en la tarea 15 (incompleto, incorrecto, bloqueo por intentos).
  - *Paso 3:* los 10 códigos en dos columnas, «Copiar códigos» (uno por línea) y «Ya los guardé». Atrás en el Paso 3 hace lo mismo que «Ya los guardé» (nunca regresa al Paso 2); en el Paso 2 vuelve al Paso 1.
  - Cada entrada pide una clave nueva (EP-19); la anterior no se recupera.
- **Confirmar identidad (`ReauthSheet`, reutilizable para T18/T19):** contraseña maestra (`PasswordField`) y «Código de tu app» (con `placeholder` nuevo en `LabeledTextField`). La `auth_key` se deriva **en el teléfono** con los parámetros de `account` (`ReauthRunner`) y se borra al terminar; la contraseña vive en un `TextFieldState` de la hoja y el ViewModel solo sabe si está vacía. `reauth_required` marca los dos campos con «Contraseña o código incorrectos.» (una sola vez).
- **2FA activa (OP-24, OP-25):** pantalla de Figma `218:4304`; «Generar códigos de respaldo nuevos» (hoja con «Generar») muestra los códigos con el diseño del Paso 3, sin «Paso 3 de 3» y con «Los códigos anteriores ya no funcionan.»; «Desactivar» (hoja con botón `danger`) vuelve a Ajustes con el aviso.
- **Backend falso:** ya cumplía EP-19…22 como dice el contrato; lo comprobé con pruebas nuevas y **no cambié nada en `FakeServer`**.

## Revisión con `security-reviewer`
Sin hallazgos críticos ni altos. Tres medios, **corregidos**:
- **M1 · `auth_key` sin borrar si se cancela justo al volver de Argon2** (`ReauthRunnerImpl` y `AuthRepositoryImpl.derive`). La derivación corría en `withContext(… + NonCancellable)` y la llave solo se cerraba después; si el que llamaba se cancelaba mientras corría Argon2 (Atrás, autobloqueo), `withContext` lanzaba `CancellationException`, el valor se descartaba y la `SecureBytes` quedaba viva en el heap. **Corrección:** el resultado se deja en una variable declarada fuera del `withContext` y se borra en un `finally` que cubre también la cancelación (en `AuthRepositoryImpl.derive` solo se entrega si todo salió bien). Prueba nueva: cancelar durante Argon2 no llama al servidor.
- **M2 · una excepción en `signOutLocally` mataba el colector de `session_revoked`** (`AuthRepositoryImpl`, `init`). Si `clearAllTables` o `sync.stop` lanzaban, la excepción terminaba el `collect` y las siguientes revocaciones del servidor ya no cerraban la sesión local. **Corrección:** el colector atrapa la excepción (sin tragarse `CancellationException`, y sin registrarla) y sigue escuchando; el `finally` de `signOutLocally` ya bloqueaba la sesión.
- **M3 · rutas de 2FA con secretos vivas al cambiar a Generador** (`AppNavHost`). Bóveda, Salud y Ajustes hacen `popUpTo`, pero Generador no, así que la clave o los códigos seguían en la pila y Atrás volvía a mostrarlos. **Corrección:** al tocar la pestaña Generador desde una pantalla de 2FA se hace pop hasta Ajustes antes de navegar, lo que vacía los ViewModel.

**Quedan abiertos (bajos), a decidir con el equipo:**
- El URI `otpauth://…?secret=` es un `Intent` implícito: cualquier app con un filtro `otpauth` lo recibe (la clave ya está visible y es copiable). Se podría usar `createChooser`.
- Salir del Paso 3 por una pestaña descarta los códigos sin avisar (la 2FA ya está activa en el servidor; habría que regenerarlos).
- Los secretos copiados no se borran del portapapeles al bloquear la bóveda (hasta 120 s; DEC-25 no lo exige).
- `lockoutCountdown.start` dentro de `_state.update` (inofensivo pero frágil) y un `Retry-After` corto se redondea a minutos.
- Parámetros KDF locales desactualizados (contraseña cambiada desde otro teléfono) se muestran como «Contraseña o código incorrectos»: a tener en cuenta en la tarea 18.
- Si `accountDao.replace` lanza en `establish` tras guardar los tokens, estos quedan sin cuenta.

## Pruebas (salida real)
- **`testDebugUnitTest`: 678, 0 fallas. `connectedDebugAndroidTest`: 108, 105 pasan; fallan las 3 de `ClipboardDeviceTest` (E3, portapapeles compartido del emulador; no lo toqué). Una primera corrida abortó porque el sistema del emulador se cayó (`System has crashed`); lo reinicié en frío y se repitió completa. `lintDebug`: 0 errores. `assembleRelease`: compila.** Nuevas: `FakeBackendTwoFactorTest` (11: clave pendiente reemplazable, confirmación con TOTP real RFC 6238, anti-replay, `reauth_required`, regenerar invalida, desactivar borra, código de respaldo de un solo uso), `TwoFactorSetupViewModelTest` (19) y `TwoFactorManageViewModelTest` (11: cada paso con éxito, error y sin red, Atrás en el Paso 3, clave y códigos vacíos al salir, `toString` sin secretos, `reauth_required` marca los dos campos), `ReauthRunnerImplTest` (8, con Argon2 real: deriva con los parámetros guardados, normaliza, borra, cancela), `OtpAuthUriTest` (4), `SettingsViewModelTest` (+6) y `UnlockViewModelTest` (+5: diálogo una vez por bloqueo y a la vista; cerrar sesión con conteo).
- **Compose (emulador):** `TwoFactorScreensTest` (15: textos de Figma de los 3 pasos, cargando, error, pantalla activa, códigos nuevos y la hoja; semántica `error` del `CodeField`).

## En el emulador
Capturas de `TwoFactorScreensTest` (dibuja las pantallas sin `SecureScreen`) comparadas con Figma `47:1509`, `47:1616`, `47:1714`, `69:3626`, `218:4304` y `218:4421`: coinciden el paso 1 (tarjeta, instrucciones, botones), cargando, el paso 3, la pantalla activa y la hoja. Corregí el título de la pantalla activa (`TitleStyle` compartido).
- **No recorrí por la interfaz de la app real** activar con una app autenticadora, cerrar sesión, entrar con 2FA, regenerar, entrar con un código de respaldo y desactivar (la prueba de Compose contra la app real se cuelga, ver tarea 13). El flujo completo contra el backend falso está cubierto con TOTP calculado en pruebas; **queda para probarlo a mano** (con el TOTP de prueba o una app autenticadora) y lo mismo el botón «Abrir app de autenticación».

## Decisiones y desviaciones a revisar
- `SignOutSheet`/`SignOutSheetContent` pasaron de `feature/settings/ui` a `core/designsystem/component` para compartirlas con Desbloquear; se quitaron las cadenas `unlock_sign_out_*` que ya no se usan.
- `DangerButton` ganó `loading` y `LabeledTextField` ganó `placeholder`.
- El campo de contraseña de la hoja se marca (borde) con credenciales incorrectas pero el mensaje y la semántica `error` van solo en el campo del código (como en Login).
- El resultado de «Desactivar» viaja a Ajustes como un booleano en el `SavedStateHandle` de su entrada; ningún secreto va en argumentos de ruta.

## Vistas / cambios (cómo llegar)
- **Activar:** Ajustes → «Verificación en 2 pasos» (inactiva).
- **2FA activa, códigos nuevos y desactivar:** Ajustes → «Verificación en 2 pasos» (activa).
- **Cerrar sesión con el conteo desde Desbloquear:** pantalla Desbloquear → «Cerrar sesión».
