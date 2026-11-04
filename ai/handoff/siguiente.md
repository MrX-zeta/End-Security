# Tarea 17 · Verificación en dos pasos (SCR-30…32, SCR-43; OP-22…25)

**Estado:** hecha
**Escrita:** 2026-11-03 · sesión de diseño

## Revisión de la tarea 16
Aprobada. Revisé `BiometricKeyStore` (AES-256-GCM, por uso, solo `BIOMETRIC_STRONG`, invalidación por huella nueva, StrongBox opcional), `BiometricWrap` (AAD y sobrescritura), la limpieza en `signOutLocally` con `try/finally` y la lista nueva de Crear cuenta.
- Tenías razón con `@Volatile`: ya estaba desde la tarea 14. Error mío.
- Aprobadas tus desviaciones: `biometric_enabled` en DataStore y `bio_*` en `account` sin cambio de esquema; «Disponible pronto» de 2,5 s.
- La prueba con huella real queda como M11 en el Redmi.

## Paso 0 · Lo que quedó abierto en la tarea 16 (grafo v1.5.9, DEC-09)
1. **La huella sí puede abrir durante la espera por intentos de contraseña.** DEC-08 cuenta solo la contraseña; la huella tiene el bloqueo propio del sistema. Sin cambio.
2. **Diálogo automático:** debe salir **una vez por cada bloqueo**, cuando Desbloquear está en primer plano (`Lifecycle.State.RESUMED`), no al crear la pantalla. Si el autobloqueo ocurre con la app en segundo plano, el diálogo sale al volver. Si se cancela, no se repite hasta el siguiente bloqueo.
3. **«¿No eres …? → Cerrar sesión» en Desbloquear:** usa las mismas hojas que Ajustes. El conteo de `pending`/`conflict` se lee de la columna `sync_state` (no hace falta descifrar). Con cambios, el texto cambia a «Si cierras sesión ahora se perderán. Desbloquea tu bóveda y espera a que se suban, o ciérrala de todos modos.» Sin intento de sincronizar (está bloqueada).
4. **Ventana entre comprobar y `session.unlock`:** usa un `Mutex` compartido en `AuthRepositoryImpl` para `signOutLocally` y la apertura con huella o contraseña.

## Objetivo
Activar, desactivar y regenerar códigos de la verificación en dos pasos desde Ajustes. Todo pasa por el servidor (el falso por ahora): la app **no** genera códigos TOTP (DEC-11).

**Figma** (página Minimalista):
- Paso 1 · clave `47:1509`, Paso 2 · código `47:1616`, Paso 3 · códigos `47:1714`, cargando `69:3626`;
- nuevas, fila «T17»: Dos pasos · activa `218:4304` y Confirmar identidad `218:4421`.

## Paso 1 · Entrada desde Ajustes
- «Verificación en 2 pasos» deja de mostrar «Disponible pronto».
- Si está **inactiva**: va al flujo de activación (Paso 2).
- Si está **activa**: va a la pantalla `218:4304` (Paso 4).
- Sin red: `MessageBar` «Necesitas conexión para cambiar la verificación en dos pasos.» y no navega.
- Al volver, Ajustes recarga «Activa» / «Inactiva».

## Paso 2 · Activar (OP-22, OP-23; EP-19, EP-20)
- **Cargando (SCR-43)** mientras responde EP-19.
- **Paso 1 de 3 (SCR-30):**
  - la clave Base32 en grupos de 4, como en Figma;
  - el botón de copiar usa `SecretClipboard` (marcado como sensible y con borrado automático);
  - si hay una app que abra `otpauth://totp/...` (`resolveActivity` / `queryIntentActivities` con la etiqueta `<queries>` en el manifiesto), muestra además un botón secundario «Abrir app de autenticación» con el URI completo (`issuer`, `algorithm`, `digits`, `period` de `TwoFactorSetup`). Si no hay, no se muestra.
- **Paso 2 de 3 (SCR-31):**
  - `CodeField` de 6 dígitos y «El código cambia en N s», calculado con el reloj del teléfono y el `periodSeconds`;
  - errores con el estilo de la tarea 15: incompleto → «Escribe los 6 dígitos del código.»; incorrecto → «Código incorrecto. Revisa que la hora de tu teléfono sea automática.»;
  - el límite de intentos → el mensaje de bloqueo que ya existe.
- **Paso 3 de 3 (SCR-32):**
  - los 10 códigos que devuelve EP-20, en dos columnas;
  - «Copiar códigos» con `SecretClipboard` (un código por línea);
  - «Ya los guardé» vuelve a Ajustes, quitando todo el flujo de la pila;
  - el botón Atrás del sistema en este paso hace lo mismo que «Ya los guardé», nunca regresa al Paso 2.
- **Reglas del flujo:**
  - pantallas con secretos → `SecureScreen()` y `ExcludeFromAutofill`;
  - la clave y los códigos viven solo en el ViewModel y se borran al salir del flujo y al bloquear (`RequireUnlocked`). Nada en `SavedStateHandle`, logs, `toString` ni argumentos de navegación;
  - salir antes de confirmar deja el 2FA pendiente en el servidor. Si se vuelve a entrar, EP-19 entrega una clave nueva: muéstrala, no recuperes la anterior.

## Paso 3 · Confirmar identidad (reautenticación)
- Hoja `218:4421`: «Confirma que eres tú», campo «Contraseña maestra» (`PasswordField`) y «Código de tu app» (6 dígitos), botón de acción y «Cancelar».
- La `authKey` se deriva **en el teléfono** igual que al entrar (Argon2id con los parámetros de `account` + HKDF), dentro de `withBytes`, y va en `Reauth`. La contraseña nunca sale del teléfono. Derivar tarda: muestra el botón cargando.
- **Errores:**
  - vacíos o incompletos → los mensajes de la tarea 15;
  - `reauth_required` → «Contraseña o código incorrectos.» marcando los dos campos;
  - sin red → el genérico.
- Hazla reutilizable (`ReauthSheet`): la usarán Contraseña maestra (T18) y Eliminar cuenta (T19).

## Paso 4 · 2FA activa (OP-24, OP-25; EP-21, EP-22)
- Pantalla `218:4304`: «**Verificación en dos pasos** está activa», la tarjeta oscura «Protegida» y el texto de los códigos de respaldo.
- **«Generar códigos de respaldo nuevos»:**
  - abre `ReauthSheet` con el texto «Para generar códigos nuevos escribe tu contraseña maestra y el código de tu app.» y el botón «Generar»;
  - al terminar, muestra los códigos con el diseño de SCR-32, sin «Paso 3 de 3» y con el aviso «Los códigos anteriores ya no funcionan.».
- **«Desactivar»:** `ReauthSheet` con el botón «Desactivar» en `danger`. Al terminar vuelve a Ajustes con `MessageBar` «Verificación en dos pasos desactivada».

## Backend falso
Comprueba que `FakeServer` cumple EP-19…22 como dice el contrato:
- secreto pendiente reemplazable;
- confirmación con TOTP real y anti-replay;
- `reauth_required` con `authKey` o TOTP incorrectos;
- desactivar borra los códigos;
- regenerar invalida los anteriores.

Si falta algo, complétalo **solo en el backend falso** y dilo en el reporte.

## Terminado cuando
- **Pruebas de ViewModel:**
  - cada paso con éxito, error y sin red;
  - Atrás en el Paso 3 no regresa al Paso 2;
  - la clave y los códigos se vacían al bloquear y al salir;
  - `toString` sin secretos;
  - la reautenticación deriva la `authKey` con los parámetros guardados;
  - `reauth_required` marca los dos campos.
- **Pruebas del backend falso:** el flujo completo con TOTP calculado en la prueba (RFC 6238); el mismo código dos veces → error (anti-replay); regenerar invalida; desactivar borra.
- **Pruebas de Compose:** textos de Figma de los 3 pasos, la pantalla activa y la hoja; semántica del `CodeField`; el URI `otpauth` bien armado.
- **Paso 0 de arriba:**
  - diálogo de huella una vez por bloqueo al estar `RESUMED`;
  - hojas de cerrar sesión desde Desbloquear con el conteo;
  - `Mutex` compartido.
- **En el emulador:** activa con una app autenticadora o con el TOTP de prueba, cierra sesión, entra con 2FA, regenera códigos, entra con uno de respaldo y desactiva. Lo que no se pueda automatizar, al reporte como prueba a mano.
- `security-reviewer` revisa los Pasos 2–4 y el `Mutex`. **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
