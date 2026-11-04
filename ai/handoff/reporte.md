# Reporte · Tarea 16 · Ajuste de Crear cuenta y Ajustes (SCR-29): bloqueo automático, huella y cerrar sesión (OP-18, OP-20, OP-21, OP-35)

**Estado:** hecha
**Fecha:** 2026-11-03

**REQUIERE REVISIÓN HUMANA:** llave del Keystore y envoltorio de la `vault_key` (`BiometricKeyStore`, `BiometricWrap`, `BiometricUnlockImpl`), `signOutLocally`/`establish` en `AuthRepositoryImpl` y consultas SQL nuevas de Room. Pasó por el subagente `security-reviewer` (ver abajo).

## Qué se hizo

### Paso 0 · Crear cuenta (DEC-31)
- `MasterPasswordPolicy`: además de 12 caracteres y puntaje ≥ 3, exige **una mayúscula, una minúscula y un número** (por punto de código sobre el texto ya normalizado con NFKC; «Ñ» cuenta; símbolos no se exigen). Solo se aplica al fijar la contraseña: Login y Desbloquear no la usan.
- La lista queda: «12 caracteres o más», «Una mayúscula», «Una minúscula», «Un número», «Las contraseñas coinciden». Se quitó «Fuerza buena o mejor» (la fuerza la dice solo el medidor).
- Si solo falla la fuerza, al tocar «Crear cuenta» la contraseña se marca con «Elige una contraseña más difícil de adivinar.» y recibe el foco; el aviso se quita al cambiar la contraseña.
- `epoch` de `HealthRepositoryImpl` **ya era `@Volatile`** (la tarea decía que no): no hubo nada que cambiar.

### Pasos 1, 2 y 4 · Ajustes (SCR-29)
- Pestaña **Ajustes** (`feature/settings`): tarjeta de la cuenta (inicial, nombre o correo, correo), «Verificación en 2 pasos» con Activa/Inactiva (`AccountApi.getAccount()`; sin red o con error solo la flecha), «Desbloquear con huella» con interruptor (oculto si no hay sensor), «Bloqueo automático» con su valor, «Contraseña maestra», «Dispositivos» (número de `SessionApi.listSessions()`), «Actividad reciente», «Cerrar sesión» y «Eliminar cuenta» en `danger`. Las filas de tareas 17–20 (2 pasos, Contraseña maestra, Dispositivos, Actividad, Eliminar cuenta) muestran `MessageBar` «Disponible pronto» (con `TODO(T17…T20)`).
- La barra inferior con **Ajustes** navega a la pantalla sin sumar historial; se quitaron los `TODO(DEC-56)`.
- **Bloqueo automático (OP-21):** hoja de Figma `215:4229` con las 5 opciones (cada una un `RadioButton` con `selectableGroup`), «5 minutos (recomendado)» y marca amarilla. Guarda en `SettingsStore.setAutolockMinutes` (pasa por `sanitize`); `AutoLocker` ya escuchaba el `Flow` (prueba nueva de que cambiarlo con la app abierta aplica sin reiniciar).
- **Cerrar sesión (OP-18):** hoja `215:3811` y, con `pending`/`conflict`, la `215:4020` («Tienes N cambios sin subir», plural). Antes de la hoja se intenta sincronizar hasta 5 s (sin red vuelve al instante). Al confirmar: `AuthRepository.signOut()` y a Iniciar sesión con la pila limpia.

### Paso 3 · Huella (OP-35, OP-20, DEC-09) — dependencia `androidx.biometric:biometric:1.1.0`
- **`BiometricKeyStore`** (`core/crypto/keystore`): AES-256-GCM en Android Keystore, alias `end-security/bio|<userId>`, `setUserAuthenticationRequired(true)` **por uso** (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` en API 30+; `-1` antes), `setInvalidatedByBiometricEnrollment(true)`, StrongBox si hay (sin fallar si no). Solo `BIOMETRIC_STRONG`: nunca `DEVICE_CREDENTIAL`.
- **`BiometricWrap`** (`core/crypto`): único código que toca los bytes de la `vault_key` para esto. AAD `end-security/bio|userId|keyVersion`; IV del Keystore guardado en `account.bio_iv`; el resultado se copia a `SecureBytes` y el arreglo intermedio se sobrescribe.
- **Activar:** sin huellas registradas no se enciende y debajo de la fila sale «Agrega una huella en los ajustes del teléfono…»; si hay, se crea la llave, se abre `BiometricPrompt` con `CryptoObject` («Activar desbloqueo con huella», «Confirma que eres tú», «Cancelar») y al aceptar se guarda `bio_iv`, `bio_wrapped_vault_key` y `biometric_enabled`. Cancelar o fallar: el interruptor vuelve a apagado, sin mensaje, y se borra la llave huérfana. **Desactivar:** borra llave, `bio_*` y bandera, sin confirmación.
- **Desbloquear:** el botón de huella (que ya existía) abre `BiometricPrompt` («Desbloquear End Security», «Usar contraseña»); el diálogo sale **una sola vez** al entrar a la pantalla. **No** cuenta ni reinicia los intentos de la contraseña. Con huella cambiada (`KeyPermanentlyInvalidated`/llave ausente) o datos que no descifran se desactiva todo y Desbloquear muestra «Tu huella cambió. Entra con tu contraseña maestra y vuelve a activarla en Ajustes.»
- `MainActivity` pasó de `ComponentActivity` a `FragmentActivity` (lo exige `BiometricPrompt`).
- **Limpieza:** `signOutLocally` borra todas las llaves biométricas y la bandera y, ahora, **siempre** termina con `session.lock()` (`try/finally`); `establish` también limpia llave y bandera de una cuenta anterior.

## Pruebas (salida real)
- **`testDebugUnitTest`: 0 fallas.** Nuevas: `MasterPasswordPolicyTest` (5), `SignUpViewModelTest` (+3), `BiometricWrapTest` (9: ida y vuelta, 1 bit del cifrado o del IV, otra AAD/versión/usuario, largo distinto, 10 000 IV distintos, llave de otro usuario), `BiometricUnlockImplTest` (10, con Keystore falso en software), `UnlockViewModelTest` (+7: una sola vez, sin bucle, no toca intentos, invalidada), `SettingsViewModelTest` (22: 2 pasos y dispositivos con éxito/error/sin red, filas futuras, bloqueo automático, activar/cancelar/sin huellas/desactivar, cerrar sesión con 0/N pendientes y sincronización que sube todo), `SettingsStoreAutolockTest` (3) y un caso nuevo en `AutoLockerTest`.
- **`connectedDebugAndroidTest`: 87 pruebas.** Nuevas: `SettingsScreenTest` (10: textos de Figma, interruptor con rol Switch, radios de la hoja con la opción marcada, las dos hojas de cerrar sesión con plural) y `BiometricKeyStoreDeviceTest` (6, 5 se **omiten** si el dispositivo no tiene huella registrada; ver abajo). En la corrida completa fallaron `HealthScreensTest.ranking_muestra_mas_fuertes_y_mas_debiles` (**intermitente**: pasó sola y las 10 de esa clase pasan) y no fallaron esta vez las de `ClipboardDeviceTest` (E3, intermitentes).
- **`lintDebug`: 0 errores. `assembleRelease`: compila.** (Corridos por separado: juntos con `compileDebugAndroidTestKotlin` el lint de pruebas chocó por tareas simultáneas de Gradle.)

## En el emulador
- Capturas de `SettingsScreenTest` (dibuja `SettingsScreen` y las hojas sin `SecureScreen`) comparadas con Figma `47:1303`, `215:4229`, `215:3811` y `215:4020`: filas, tarjeta, interruptor amarillo, hoja con marca amarilla y las dos hojas de cerrar sesión coinciden.
- **El emulador no tiene huella registrada**: `canAuthenticate(BIOMETRIC_STRONG)` no da éxito y Android rechaza crear llaves por uso (`At least one biometric must be enrolled…`), así que **no se pudo activar la huella ni desbloquear con ella** aquí. `BiometricPrompt` con la huella simulada no se puede automatizar sin registrarla por la interfaz de Ajustes del teléfono. **Queda como prueba a mano en el Redmi** (con huella registrada): activar, bloquear con el candado y desbloquear; registrar otra huella y ver «Tu huella cambió…»; cerrar sesión. Tampoco recorrí por la interfaz real «Al salir» ni «Cerrar sesión» (la prueba de Compose contra la app real se cuelga, ver tarea 13); quedan cubiertos por las pruebas de VM/`AutoLocker` y para probar a mano.

## Revisión con `security-reviewer`
Sin hallazgos críticos ni altos; la llave, el `BIOMETRIC_STRONG`/`CryptoObject`, el IV y la AAD están bien. **Corregí:**
- (Media) `signOutLocally` bloqueaba la sesión al final: si fallaba un paso intermedio (DataStore, Room), la `vault_key` seguía en memoria. Ahora `try/finally` con `session.lock()` y cada paso protegido.
- (Media) Carrera huella ↔ cerrar sesión: `completeUnlock` vuelve a comprobar que la cuenta y el envoltorio siguen en Room antes de abrir, y el ViewModel ignora la huella con un desbloqueo o cierre en curso.
- (Media) Excepciones no capturadas dejaban `biometricBusy` pegado o cerraban la app: `try/finally` en Desbloquear y Ajustes y `RuntimeException` → `Failed` en el repositorio.
- (Baja/Media) Un error pasajero del Keystore ya no borra la huella ni dice «Tu huella cambió»: solo `KeyPermanentlyInvalidated`/llave ausente.
- (Baja) Llave huérfana al cancelar la activación: se borra. (Baja) `establish` limpia llave y bandera de otra cuenta.

**Quedan abiertos, a decidir con el equipo:**
- El diálogo automático sale una sola vez: si el autobloqueo manda a Desbloquear con la app en segundo plano, el sistema lo cancela y hay que tocar el botón.
- «¿No eres Ana? → Cerrar sesión» en Desbloquear borra los datos locales **sin** avisar de cambios sin subir (con la bóveda bloqueada no se puede sincronizar); la hoja con el conteo solo existe en Ajustes.
- La huella puede abrir durante un bloqueo por intentos de contraseña (no usa ese contador); confirmar que DEC-08 lo permite.
- Sigue una ventana mínima entre la comprobación y `session.unlock` si el cierre de sesión llega justo en medio (no hay un mutex compartido con `signOutLocally`).

## Decisiones y desviaciones a revisar
- `biometric_enabled` está en DataStore (ya existía, lo lee Desbloquear) y `bio_iv`/`bio_wrapped_vault_key` en `account` (columnas ya existentes): no hubo cambio de esquema de Room.
- Cambiar la contraseña maestra (tarea 18) cambia `key_version` y por tanto la AAD: la huella deberá volver a envolverse o activarse de nuevo.
- «Disponible pronto» se muestra 2,5 s y no se anuncia con acción.

## Vistas / cambios (cómo llegar)
- **Ajustes (SCR-29):** barra inferior → «Ajustes».
- **Bloqueo automático:** Ajustes → «Bloqueo automático».
- **Cerrar sesión (dos hojas):** Ajustes → «Cerrar sesión».
- **Huella:** Ajustes → «Desbloquear con huella»; en Desbloquear, el botón de huella.
- **Crear cuenta con la lista nueva:** Onboarding → «Crear cuenta», escribir una contraseña.
