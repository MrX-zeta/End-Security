# Tarea 18 · Cambiar la contraseña maestra (SCR-35; OP-27; EP-17)

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 17
Aprobada. Revisé el flujo de 3 pasos, `ReauthSheet`/`ReauthRunnerImpl` (la `auth_key` se deriva en el teléfono y se borra, también al cancelar), `SessionLifecycleLock` y las correcciones M1–M3 de `security-reviewer`. Aprobadas tus desviaciones:
- `SignOutSheet` en `core/designsystem`;
- `loading` en `DangerButton` y `placeholder` en `LabeledTextField`;
- en la hoja, borde en los dos campos y mensaje solo en el código;
- el resultado de «Desactivar» como booleano en el `SavedStateHandle`.

El recorrido con app autenticadora y código de respaldo queda como prueba a mano (M4 ya existía en `revisiones-pendientes.md`).

## Paso 0 · Ajustes que salieron de la prueba en el teléfono y de la tarea 17 (grafo v1.5.10)
1. **Sin diálogo automático de huella (DEC-09).** Lo probó el equipo en el Redmi: al tocar el candado de la Bóveda aparece de inmediato la hoja del sistema con la huella, y se siente forzado. Desbloquear debe mostrarse **completa**: título, campo de contraseña y el **botón de huella**. `BiometricPrompt` se abre **solo al tocar ese botón**, en todos los casos (candado, autobloqueo, abrir la app). Quita la lógica de «una vez por bloqueo» y sus pruebas, y agrega una que compruebe que entrar a Desbloquear **no** abre el diálogo.
2. **«Abrir app de autenticación»:** usa `Intent.createChooser` para que el sistema no entregue la clave a una app por su cuenta.
3. **Salir del Paso 3 de la activación por una pestaña:** antes de salir, una hoja «¿Ya guardaste tus códigos?» / «No podrás volver a verlos. Si los pierdes, genera unos nuevos en Ajustes.» con «Salir» y «Volver a los códigos». «Ya los guardé» y Atrás siguen como están.
4. **Portapapeles al bloquear (DEC-25):** al bloquear la bóveda, `SecretClipboard` borra lo que copió si sigue ahí (misma regla que el borrado por tiempo).
5. **`lockoutCountdown.start`** fuera de `_state.update`. El `Retry-After` se redondea hacia arriba en minutos (ya es así), pero con menos de 60 s muestra segundos: «Vuelve a intentarlo en N s».
6. **`establish`:** si `accountDao.replace` falla después de guardar los tokens, borra los tokens antes de propagar el error.
7. **Ajustes «parpadea» al cerrar sesión.** Lo vio el equipo: con la huella activa, al confirmar «Cerrar sesión» el interruptor pasa de encendido a apagado antes de cambiar de pantalla, porque `signOutLocally` borra la bandera y Ajustes sigue escuchando. Arreglo:
   - en cuanto se confirma, el ViewModel pasa a un estado `signingOut` que **congela** lo que se ve (deja de aplicar cambios de la huella, la 2FA, los dispositivos y la cuenta);
   - el botón de la hoja queda cargando hasta navegar;
   - lo mismo aplica a «¿No eres …? → Cerrar sesión» en Desbloquear.
   - Prueba: durante el cierre, el estado que se muestra no cambia aunque cambien la bandera o Room.

## Objetivo
Cambiar la contraseña maestra desde Ajustes. Por DEC-05, la **misma `vault_key`** se vuelve a envolver con una sal nueva: los elementos **no** se vuelven a cifrar. El servidor sube `key_version` (DEC-41) y cierra las demás sesiones (TX-10).

**Figma:** Contraseña maestra `47:2098`, con la lista de requisitos ya actualizada.

## Paso 1 · Pantalla (SCR-35)
- Aviso oscuro «Te la pediremos en todos tus dispositivos. Si la olvidas, nadie podrá recuperar tu bóveda.»
- **Campos:**
  - «Contraseña actual»;
  - **«Código de tu app» (6 dígitos), solo si la 2FA está activa** (no está en Figma: mismo estilo que la hoja de reautenticación, debajo de la contraseña actual);
  - «Nueva contraseña» con el medidor;
  - «Confírmala».
- **Requisitos** con el mismo componente de Crear cuenta (`RequirementLine`) y la misma `MasterPasswordPolicy`: «12 caracteres o más», «Una mayúscula», «Una minúscula», «Un número», «Las contraseñas coinciden» y **«No aparece en filtraciones»**.
- **«No aparece en filtraciones»:**
  - se revisa con `HibpClient`, que ya existe: solo el prefijo y con relleno;
  - se consulta **al tocar el botón**, no mientras se escribe;
  - antes de revisar, la línea va en gris;
  - si la contraseña aparece en filtraciones, tache y el campo se marca con «Esta contraseña apareció en filtraciones. Elige otra.»: **bloquea** el cambio;
  - sin red, la línea dice «No se pudo revisar (sin conexión)» en gris y **no** bloquea. De todos modos, el cambio necesita red.
- **Más reglas:**
  - la nueva no puede ser igual a la actual (comparando el texto normalizado): «Debe ser distinta de la actual.»;
  - errores y botón siempre activo como en la tarea 15;
  - `SecureScreen()` y `ExcludeFromAutofill`;
  - las contraseñas viven solo en los `TextFieldState` de la pantalla, como en Crear cuenta.
- En Ajustes, «Contraseña maestra» deja de decir «Disponible pronto».

## Paso 2 · El cambio (OP-27) — **REQUIERE REVISIÓN HUMANA**
En un caso de uso del dominio (`ChangeMasterPasswordUseCase` o dentro de `AccountRepository`), en este orden:
1. **Reautenticación:** deriva la `auth_key` **actual** con los parámetros de `account` (el mismo código que `ReauthRunner`), junto con el TOTP si hay 2FA.
2. **Nuevas llaves:** sal nueva de 16 bytes con `SecureRandom` y los parámetros KDF por defecto (DEC-05). De ahí `master_key` nueva → `enc_key` y `auth_key` nuevas (HKDF).
3. **Volver a envolver la `vault_key`** (la que está en `VaultSession`) con la `enc_key` nueva, un nonce nuevo y la AAD `end-security/vault-key|userId|keyVersion+1`.
4. **EP-17** con `expectedKeyVersion` = la local, `newAuthKey`, `newKdf` y `newProtectedVaultKey`.
5. **Si responde 200:** en **una sola transacción de Room**, reemplaza en `account` la sal, los parámetros, `pvk_nonce`, `pvk_ciphertext` y `key_version` (el que devolvió el servidor). La sesión sigue abierta con la misma `vault_key`.
6. **Huella:** si estaba activa, al terminar abre `BiometricPrompt` («Confirma tu huella para seguir usándola») y vuelve a envolver con la AAD de la nueva `key_version`. Si se cancela o falla, desactívala y muestra «Desbloqueo con huella desactivado. Actívalo de nuevo en Ajustes.»
7. Vuelve a Ajustes con `MessageBar` «Contraseña cambiada. Cerramos la sesión en tus otros dispositivos.»

**Errores:**
- `reauth_required` → «Contraseña o código incorrectos.» en «Contraseña actual» (y en el código, si se ve);
- `key_version_conflict` (409) → «Tu contraseña maestra cambió en otro dispositivo. Cierra sesión y entra con la nueva.» No se toca nada local;
- sin red o 5xx → el genérico, sin cambios locales.
- Si el servidor respondió 200 pero falla guardar en Room, reintenta una vez. Si vuelve a fallar, `signOutLocally()` y a Iniciar sesión con «Tu contraseña cambió. Entra con la nueva.»: es preferible a dejar parámetros viejos.

**Memoria:** `master_key`, `enc_key`, las dos `auth_key` y la sal se borran en `finally` (también al cancelar, como en M1 de la tarea 17). Derivar dos veces tarda: el botón queda cargando y Atrás está deshabilitado mientras dura el paso 4.

## Backend falso
Comprueba EP-17 en `FakeServer`:
- valida la reautenticación;
- con `expectedKeyVersion` distinto → 409;
- reemplaza parámetros, `auth_hash` y pvk;
- sube `key_version`;
- revoca las demás sesiones.

Completa **solo el backend falso** si falta algo.

## Terminado cuando
- **Pruebas unitarias del cambio** (Argon2 real, con parámetros bajos solo en la prueba):
  - tras cambiar, la contraseña **nueva** desbloquea y la vieja no, contra la `account` local;
  - los elementos ya guardados se siguen descifrando sin volver a cifrarse;
  - la AAD de la pvk usa la nueva `key_version`;
  - 409, `reauth_required` y sin red no cambian nada local;
  - falla de Room tras el 200 → reintento y luego cierre de sesión;
  - las llaves se borran también al cancelar.
- **Pruebas de la pantalla:** cada requisito; HIBP con filtración, sin filtración y sin red; igual a la actual; campo de código solo con 2FA.
- **Backend falso:** el flujo con 2FA y sin 2FA; 409; las otras sesiones quedan revocadas.
- **Paso 0:** sin diálogo automático; `createChooser`; la hoja al salir del Paso 3; portapapeles al bloquear; segundos del bloqueo; `establish`; Ajustes congelado al cerrar sesión.
- **En el emulador:** cambia la contraseña de una cuenta de prueba, bloquea y desbloquea con la nueva, cierra sesión y entra con la nueva. La parte de la huella va al reporte como prueba a mano.
- `security-reviewer` revisa el Paso 2 completo. **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
