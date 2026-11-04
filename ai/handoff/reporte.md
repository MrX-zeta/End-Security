# Reporte · Tarea 15 · Mensajes de error en los formularios

**Estado:** hecha
**Fecha:** 2026-11-03

No toqué `AuthRepositoryImpl` ni la política de contraseña (solo se muestra mejor lo que ya existía), así que no se pasó por `security-reviewer`. No hay cambios en `core/crypto` ni `core/session`.

## Qué se hizo

### Design system (`core/designsystem/component`)
- **`errorText`** en `LabeledTextField` (ambas variantes), `PasswordField` y `CodeField`: borde `danger` de 2 dp, ícono de alerta + mensaje debajo, semántica `error` y `liveRegion Polite`. Con `errorText` se oculta la ayuda del campo.
- **`Checkbox(isError)`** (casilla sin marcar con borde de error).
- **`FieldErrors.kt`** (nuevo): `FieldErrorText`, `RequirementLine` (círculo gris / palomita `success` / tache `danger`, con descripción «cumplido / falta / pendiente» para TalkBack) y `Modifier.onFocusLost`.
- Íconos `ic_x_16` e `ic_circle_16`; textos nuevos en `strings.xml`.

### Pantallas
- **Crear cuenta (SCR-05):** botón siempre activo (salvo cargando). Correo: error al salir del campo con texto o al tocar el botón, se quita al quedar bien. Lista de requisitos («12 caracteres o más», «Fuerza buena o mejor», «Las contraseñas coinciden») en cuanto se escribe la contraseña. Contraseña vacía, confirmación distinta y términos sin marcar se marcan al tocar el botón. Con errores se desplaza y da el foco al primero (correo → contraseña → confirmación → términos) y **no se llama al servidor ni a la derivación de llaves**.
- **Login (SCR-04):** correo con formato inválido y contraseña vacía; con credenciales incorrectas se marcan los dos campos y el mensaje sale **una sola vez**, bajo la contraseña (no revela si el correo tiene cuenta). Bloqueo por intentos igual que antes (botón deshabilitado con su cuenta regresiva).
- **Desbloquear (SCR-10):** campo vacío → «Escribe tu contraseña maestra.» y foco; **no cuenta como intento**.
- **Códigos (Verificar correo, 2FA, código de respaldo):** botón activo; con menos dígitos: «Escribe los 6 dígitos del código.» / «Escribe el código completo (XXXX-XXXX).» y foco al campo, sin llamar al servidor. Los errores del servidor existentes se muestran ahora con el mismo estilo de campo marcado. En Verificar correo el botón sigue deshabilitado solo con el código vencido (se reenvía).
- **Nueva/Editar:** «Nombre» vacío usa el estilo de campo marcado y recibe el foco al tocar «Guardar». «Guardar» ya estaba activo salvo mientras Editar carga el elemento (`formReady`), sin cambio.

## Pruebas (salida real)
- **`testDebugUnitTest`: 0 fallas.** Nuevas: Login (7: no aparece al escribir, aparece al salir del campo, vacío no marca, todo vacío enfoca el correo y no llama al servidor, solo contraseña vacía enfoca la contraseña, dos toques cambian el id del foco, escribir quita el aviso), Crear cuenta (4) y Verificar correo (2).
- **`connectedDebugAndroidTest`: 77, 75 pasan; fallan los 2 de `ClipboardDeviceTest` (E3, portapapeles compartido del emulador, no lo toqué).** Nueva: `FormErrorsScreenTest` (4: sin errores no hay mensajes ni semántica `error`; con todo mal se ven los tres mensajes y hay semántica `error`; lista de requisitos; Login con credenciales incorrectas).
- **`lintDebug`: 0 errores. `assembleRelease`: compila.**

## En el emulador
Capturas de `FormErrorsScreenTest` (dibuja `SignUpScreen`/`LoginScreen` sin `SecureScreen`; no se quitó ninguna protección). Comparada Crear cuenta con Figma `210:3711`: borde rojo, ícono de alerta, mensaje del correo en dos líneas, casilla en rojo con su mensaje y botón activo coinciden. Los nodos `210:3875` y `210:3949` los cubrí por las mismas piezas (Login con credenciales incorrectas y lista de requisitos), sin abrirlos uno a uno.
- **No recorrí la app real por la interfaz** (la prueba de Compose contra la app real se cuelga, ver tarea 13); el foco/desplazamiento al campo con error y el teclado quedan **para probarlos a mano en el Redmi**.

## Decisiones y desviaciones a revisar
- En Crear cuenta el campo de contraseña **no** se marca por requisitos incumplidos (Figma lo pinta en rojo con 14 puntos); la lista de requisitos con tache/palomita ya lo dice. Sí se marca cuando está vacío al tocar el botón.
- El medidor ya no repite «Usa al menos 12 caracteres» (lo dice la lista).
- Con el botón siempre activo, `canSubmit` en los estados queda como «listo para enviar» y se usa solo en lo que se revisa al tocar.
- Si lo único que falla es la fuerza de la contraseña, se enfoca la contraseña y la lista muestra el tache.

## Vistas / cambios (cómo llegar)
- **Crear cuenta:** Onboarding → «Crear cuenta»; tocar el botón con campos vacíos.
- **Login:** pantalla de inicio → contraseña equivocada o botón vacío.
- **Desbloquear:** con la bóveda bloqueada, «Desbloquear» sin escribir.
- **Verificar correo / 2FA / Código de respaldo:** tocar el botón con menos dígitos.
- **Nueva / Editar:** Bóveda → «+» → «Guardar» con el nombre vacío.
