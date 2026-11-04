# Tarea 15 · Mensajes de error en los formularios

**Estado:** hecha
**Escrita:** 2026-11-03 · sesión de diseño

## Revisión de la tarea 14
Aprobada. Revisé `HibpClient`, `HealthStores`, `HealthRepositoryImpl`, el manifiesto y `network_security_config.xml`:
- HIBP solo recibe el prefijo de 5 caracteres; sin redirecciones; cuerpo con tope de 2 MB; los bytes del hash se sobrescriben;
- la caché y las fotos van cifradas con su propia AAD (`breach|…`, `health|…`);
- el bloqueo corta el análisis y la revisión (época por sesión);
- sin tráfico en claro y nada a logs.

Aprobadas tus desviaciones (eje de 50 a 100, «Revisado hoy» desde la caché, análisis en segundo plano para la Bóveda, «Débiles» abre el ranking). Los cuatro puntos abiertos se aceptan como están.

**Un ajuste para esta tarea:** `epoch` en `HealthRepositoryImpl` se lee y se escribe desde varios hilos (el colector del estado de sesión y el trabajo en `Dispatchers.Default`). Márcalo `@Volatile` (o usa `AtomicInteger`).

Las pruebas a mano que no pudiste hacer quedaron como M8 y M9 en `revisiones-pendientes.md`.

## Por qué esta tarea
En la demostración con el profesor, **«Crear cuenta» e «Iniciar sesión» se quedaban con el botón apagado sin decir por qué**. El profesor pensó que solo aceptaba @gmail.com: en realidad la contraseña no llegaba a la fuerza mínima o faltaba aceptar los términos, y nada lo indicaba. Ningún formulario debe dejar a la persona sin saber qué corregir.

## Regla general (todos los formularios)
1. **El botón principal está siempre activo** (salvo mientras carga o durante el bloqueo por intentos). Al tocarlo con algo mal:
   - cada campo con problema se marca: borde `danger` de 2 dp e ícono de alerta + mensaje en `danger` debajo;
   - la pantalla se desplaza al **primer** campo con error y le da el foco;
   - no se manda nada al servidor.
2. **Cuándo aparece cada error:** al **salir del campo** (si tiene texto) o al **tocar el botón**. Nunca mientras se escribe por primera vez. En cuanto el campo queda bien, el error se quita en ese momento (mientras escribe).
3. **Accesibilidad:** el campo usa `isError` y `Modifier.semantics { error(mensaje) }`, y el mensaje se anuncia con `liveRegion = Polite`. Con TalkBack se debe oír el error al tocar el botón. No uses solo el color: siempre ícono + texto.
4. Los mensajes **no** repiten lo que se escribió (ni el correo ni nada de la contraseña) y nada termina en logs.
5. Un componente común para no repetir: el campo de texto del sistema de diseño acepta `errorText: String?` y pinta borde, ícono y mensaje; `InlineErrorMessage` se reutiliza para los errores que no son de un campo.

**Figma** (página Minimalista, fila «Tarea 15 · Mensajes de error en formularios»):
- `210:3711` Crear cuenta · errores al enviar;
- `210:3875` Login · correo inválido;
- `210:3949` Login · credenciales incorrectas.

## Paso 1 · Crear cuenta (SCR-05)
- **Correo:** «Escribe un correo válido, por ejemplo nombre@correo.com». Usa el mismo `EmailFormat` (no se limita a ningún dominio).
- **Lista de requisitos** debajo del medidor de fuerza, visible en cuanto se empieza a escribir la contraseña. Cada línea con palomita `success` o tache `danger` (ícono + texto, nunca solo color):
  - «12 caracteres o más»;
  - «Fuerza buena o mejor» (puntaje 3 o más);
  - «Las contraseñas coinciden» (se evalúa en cuanto la confirmación tiene el mismo largo o más; antes, la línea va en gris neutro).
  - Si hay caracteres prohibidos, se mantiene el mensaje que ya existe en lugar de la lista.
- **Confirmación:** «Las contraseñas no coinciden.» con las reglas de arriba.
- **Términos:** la casilla sin marcar se pinta con borde `danger` y debajo: «Acepta los Términos y el Aviso de privacidad para continuar.»
- El botón ya no depende de `canSubmit` para estar activo; `canSubmit` se queda como la validación que se revisa al tocarlo (renómbralo si conviene, p. ej. `isFormValid`).

## Paso 2 · Iniciar sesión y Desbloquear
- **Correo** con el mismo mensaje que el registro.
- **Contraseña vacía:** «Escribe tu contraseña maestra.»
- **Credenciales incorrectas:** se marcan **los dos campos** y el mensaje va debajo de la contraseña: «Correo o contraseña incorrectos.» (un solo mensaje para no revelar si el correo tiene cuenta, como ya está). El texto de ayuda «Si la olvidas…» se oculta mientras se ve el error.
- **Desbloquear:** vacía → «Escribe tu contraseña maestra.»; incorrecta → se mantiene «Contraseña incorrecta.» con el campo marcado.
- El bloqueo por intentos se queda como está (botón apagado con su cuenta regresiva, que **sí** explica por qué).

## Paso 3 · Códigos (Verificar correo SCR-06, 2FA, código de respaldo)
- Botón activo; con menos de los dígitos necesarios: «Escribe los 6 dígitos del código.» / «Escribe el código completo (XXXX-XXXX).»
- Los errores del servidor que ya existen (`verify_error_wrong_code`, `verify_error_expired`, `two_factor_error_wrong_code`, `backup_code_error_invalid`) se muestran con el mismo estilo de campo marcado.

## Paso 4 · Nueva / Editar contraseña
- Ya existe `nameError`: comprueba que use el mismo estilo (borde, ícono y mensaje «Escribe un nombre.» o el que ya tenga), que el botón «Guardar» esté activo desde el principio en Editar (hoy `formReady` lo apaga hasta cargar; mientras carga sí puede ir apagado) y que salte al campo.

## Terminado cuando
- **Pruebas de ViewModel** por pantalla: el error no aparece mientras se escribe, aparece al salir del campo y al tocar el botón; se quita al corregir; con errores no se llama al repositorio.
- **Pruebas de Compose:** en Crear cuenta, tocar el botón con todo vacío o mal muestra los mensajes de correo, requisitos, confirmación y términos, y el foco queda en el correo; los requisitos cambian de tache a palomita; en Iniciar sesión, credenciales incorrectas marca los dos campos. Comprueba la semántica `error` en cada campo.
- **En el emulador:** recorre a mano Crear cuenta con `x@mail.com` y una contraseña corta (debe quedar claro qué falta) y un inicio de sesión con contraseña equivocada. Toma capturas y compáralas con los tres marcos de Figma.
- `@Volatile` en `epoch` (tarea 14).
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos.
- No hace falta `security-reviewer` salvo que toques `AuthRepositoryImpl` o la política de contraseña (no deberías).

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.

## Después (tarea 16, ya acordada)
Ajustes (SCR-29): bloqueo automático, desbloqueo con huella y cerrar sesión, como entrada a 2FA, dispositivos y cuenta.
