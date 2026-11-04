# Tarea 16 · Ajuste de Crear cuenta y Ajustes (SCR-29): bloqueo automático, huella y cerrar sesión (OP-18, OP-20, OP-21, OP-35)

**Estado:** hecha
**Escrita:** 2026-11-03 · sesión de diseño

## Revisión de la tarea 15
Aprobada. Revisé el sistema de diseño (`errorText` en `LabeledTextField`, `PasswordField` y `CodeField` con semántica `error` y `liveRegion`), Crear cuenta, Login, Desbloquear y los códigos. Aprobadas tus desviaciones:
- la contraseña no se pinta en rojo por requisitos incumplidos (la lista ya lo dice); sí cuando está vacía;
- el medidor ya no repite «Usa al menos 12 caracteres»;
- `canSubmit` como «listo para enviar».

**Pendiente de la tarea 14 que no se hizo:** `epoch` en `HealthRepositoryImpl` sigue sin `@Volatile`. Hazlo en esta tarea.

El foco, el desplazamiento y el teclado con errores quedan como prueba a mano en el Redmi (M10 en `revisiones-pendientes.md`).

## Paso 0 · Ajuste de Crear cuenta (DEC-31, grafo v1.5.8)
Lo pidió el equipo después de probar la tarea 15:
- **Quita la línea «Fuerza buena o mejor»** de la lista de requisitos: la fuerza la dice solo el medidor de barras (zxcvbn, sin cambios).
- **Nuevas reglas obligatorias** en `MasterPasswordPolicy` (además de 12 caracteres y puntaje ≥ 3): al menos **una mayúscula**, **una minúscula** y **un número**. Los símbolos **no** se exigen. Se evalúan sobre el texto ya normalizado (NFKC) con las categorías Unicode (`Character.isUpperCase`/`isLowerCase`/`isDigit` por punto de código), así «Ñ» cuenta como mayúscula.
- **La lista queda así**, en este orden: «12 caracteres o más», «Una mayúscula», «Una minúscula», «Un número», «Las contraseñas coinciden».
- Si solo falla la fuerza (cumple todo lo de la lista pero el puntaje es menor que 3), al tocar el botón la contraseña se marca con «Elige una contraseña más difícil de adivinar.» y el foco va a ella; el medidor sigue diciendo «Débil» o «Regular».
- Las reglas valen **solo al fijar** una contraseña (registro y, después, el cambio de contraseña maestra). **Nunca** al entrar ni al desbloquear: las cuentas que ya existen siguen funcionando.
- Pruebas: cada regla por separado, «Ñ» y dígitos de otros alfabetos tras NFKC, el caso «solo falla la fuerza», y que Login y Desbloquear no aplican las reglas.
- Figma `210:3711` ya tiene la lista nueva.

## Objetivo
La pestaña **Ajustes** con lo que funciona solo en el teléfono: datos de la cuenta, **bloqueo automático**, **desbloqueo con huella** (DEC-09) y **cerrar sesión**. Las filas que llevan a pantallas de las tareas 17–20 se ven pero todavía no navegan.

**Figma** (página Minimalista):
- Ajustes `47:1303`;
- fila «Tarea 16»: Bloqueo automático `215:4229`, Cerrar sesión `215:3811`, Cerrar sesión con cambios sin subir `215:4020`.

## Paso 1 · Pantalla Ajustes (SCR-29)
- Título en dos pesos «**Ajustes** y seguridad»; tarjeta de la cuenta (inicial, nombre y correo de la tabla `account`).
- **Seguridad:**
  - «Verificación en 2 pasos» con «Activa» / «Inactiva» de `AccountApi.getAccount().twoFactorEnabled`. Sin red o con error, sin valor (solo la flecha). Agrega `getAccount()` a `AccountRepository` si hace falta.
  - «Desbloquear con huella» con interruptor (Paso 3). Si el teléfono no tiene sensor (`BiometricManager` dice que no hay hardware), la fila no se muestra.
  - «Bloqueo automático» con el valor actual: «Al salir», «1 min», «5 min», «15 min», «30 min» (Paso 2).
  - «Contraseña maestra».
- **Cuenta:** «Dispositivos» (con el número de `SessionApi.listSessions()`, sin valor si falla), «Actividad reciente», «Cerrar sesión» (Paso 4) y «Eliminar cuenta» en `danger`.
- **Filas de tareas futuras** (2 pasos, Contraseña maestra, Dispositivos, Actividad, Eliminar cuenta): se ven igual que en Figma; al tocarlas, `MessageBar` neutro «Disponible pronto». Deja un `TODO(T17…T20)` en cada una.
- La barra inferior con **Ajustes** activa navega a esta pantalla (quita los `TODO(DEC-56)` que quedan) y vuelve a las otras pestañas sin sumar historial.
- Dentro de `RequireUnlocked`. No necesita `SecureScreen` (no muestra secretos), pero sí se vacía el ViewModel al bloquear.

## Paso 2 · Bloqueo automático (OP-21, DEC-26)
- Hoja inferior `215:4229`: ícono de reloj, «Bloqueo automático», «Tu bóveda se bloquea cuando la app pasa este tiempo en segundo plano.» y las cinco opciones (`AutoLockSettings.ALLOWED_MINUTES`). La actual lleva la marca amarilla; «5 minutos (recomendado)».
- Tocar una opción la guarda en `SettingsStore` (agrega el `set`, pasando por `sanitize`) y cierra la hoja. `AutoLocker` ya escucha el `Flow`; comprueba que el cambio se aplica sin reiniciar la app.
- Cada opción es un `RadioButton` para TalkBack (`selectableGroup` + `Role.RadioButton`).

## Paso 3 · Desbloqueo con huella (OP-35 activar/desactivar, OP-20 desbloquear; DEC-09) — **REQUIERE REVISIÓN HUMANA**
Agrega `androidx.biometric:biometric` (la última estable) al catálogo de versiones.

**Llave del Keystore** (`core/security` o `core/crypto`, p. ej. `BiometricKeyStore`):
- AES-256-GCM, alias fijo por usuario (`end-security/bio|<userId>`);
- `setUserAuthenticationRequired(true)`, autenticación **por uso** (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` en API 30+; `setUserAuthenticationValidityDurationSeconds(-1)` antes);
- `setInvalidatedByBiometricEnrollment(true)`;
- StrongBox si está disponible, sin fallar si no;
- **solo `BIOMETRIC_STRONG`**, nunca `DEVICE_CREDENTIAL` (el PIN del teléfono no debe abrir la bóveda).

**Activar** (interruptor a encendido, con la bóveda abierta):
1. Si `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` dice que no hay huellas registradas: no se enciende y aparece debajo de la fila «Agrega una huella en los ajustes del teléfono para usar esta opción.» (estilo de error de la tarea 15, sin rojo en el borde: es un aviso).
2. Crea la llave, inicia un `Cipher` en modo cifrado y abre `BiometricPrompt` con `CryptoObject`: título «Activar desbloqueo con huella», subtítulo «Confirma que eres tú», botón negativo «Cancelar».
3. Al aceptar: cifra los bytes de la `vault_key` con ese `Cipher` (dentro de `withBytes`), guarda `bio_iv` y `bio_wrapped_vault_key` en `account` y `biometric_enabled = true`. AAD: `end-security/bio|userId|keyVersion`.
4. Cancelar o fallar: el interruptor vuelve a apagado, sin mensaje (salvo error real del sistema).

**Desactivar:** borra la llave del Keystore, pone `bio_iv` y `bio_wrapped_vault_key` en `null` y `biometric_enabled = false`. Sin confirmación.

**Desbloquear** (el botón de huella que ya existe en Desbloquear; quita el `TODO(OP-20)`):
1. Inicia el `Cipher` en modo descifrado con `bio_iv` y abre `BiometricPrompt`: «Desbloquear End Security», botón negativo «Usar contraseña».
2. Al aceptar: descifra con AAD, `session.unlock(userId, vaultKey)`. **No** cuenta ni reinicia los intentos fallidos de la contraseña.
3. Al abrir Desbloquear con la huella activa, el diálogo se muestra **solo** una vez por entrada a la pantalla (no en bucle si se cancela).
4. **Huella cambiada** (`KeyPermanentlyInvalidatedException` al iniciar el `Cipher`) o datos que no descifran: desactiva todo como en «Desactivar» y muestra en Desbloquear «Tu huella cambió. Entra con tu contraseña maestra y vuelve a activarla en Ajustes.»

**Limpieza:** `signOutLocally` también borra la llave del Keystore. Los bytes de la `vault_key` en claro se sobrescriben al terminar. Nada de esto va a logs.

## Paso 4 · Cerrar sesión (OP-18)
- Hoja `215:3811`: «¿Cerrar sesión?», «Para volver necesitarás tu correo y tu contraseña maestra. Tu bóveda sigue guardada en tu cuenta.», «Cerrar sesión» (peligro) y «Cancelar».
- **Con cambios sin subir** (elementos `pending` o `conflict`): hoja `215:4020`, «Tienes N cambios sin subir» (plural) y «Si cierras sesión ahora se perderán. Conéctate a internet y espera a que se suban, o ciérrala de todos modos.», botón «Cerrar sesión de todos modos».
- Antes de mostrar la hoja, si hay red, pide una sincronización y espera hasta 5 s: si se suben, se muestra la hoja normal.
- Al confirmar: `AuthRepository.signOut()` (ya existe) y se va a Iniciar sesión con la pila de navegación limpia.

## Terminado cuando
- **Pruebas unitarias:**
  - Ajustes: valores de 2 pasos y dispositivos con éxito, con error y sin red; filas futuras muestran «Disponible pronto».
  - Bloqueo automático: guardar cada opción; un valor inválido se sanea; `AutoLocker` aplica el nuevo tiempo.
  - Huella con un `BiometricKeyStore` falso: activar guarda `bio_iv`/`bio_wrapped_vault_key` y la bandera; desactivar los borra; desbloquear abre la sesión y no toca los intentos; huella invalidada desactiva y muestra el mensaje; AAD distinta no descifra.
  - Cerrar sesión: cuenta de pendientes y conflictos; sincronización que sube todo muestra la hoja normal.
- **Pruebas del Paso 0** (arriba) y la prueba de Compose de Crear cuenta actualizada con la lista nueva.
- **Pruebas de Compose:** Ajustes con los textos de Figma; hoja de bloqueo automático con la opción marcada y semántica de radio; las dos hojas de cerrar sesión.
- **En el emulador** (con huella simulada: Extended controls → Fingerprint): activar la huella, bloquear con el candado, desbloquear con la huella; cambiar el bloqueo a «Al salir» y comprobar que bloquea al salir; cerrar sesión. Si `BiometricPrompt` no se puede automatizar, déjalo como prueba a mano y dilo en el reporte.
- `@Volatile` en `epoch` (tarea 14).
- `security-reviewer` revisa el Paso 3 y la limpieza. **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
