# Tarea 21 · Cambiar el correo (OP-26; EP-15, EP-16; DEC-63)

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 20
Aprobada. Buen trabajo con los dos hallazgos de `security-reviewer`:
- **`AccountDeletionGate` con el aviso a `LockGuard`:** se abre dentro del `NonCancellable` antes de limpiar. Aprobado como cambio de diseño: era la única forma con `RequireUnlocked`.
- **La marca de limpieza con `user_id`.**

Aprobadas tus desviaciones: `signOutLocally` que devuelve `Boolean`, N con los elementos que esperan subir y las manchas grises de «Cuenta eliminada».

**Lo abierto:**
- **Hallazgo 3:** se acepta como **RSG-14** (grafo v1.6.2).
- **Hallazgo 8:** se completa con el backend real.
- **Falta una cosa:** no volviste a correr las 170 pruebas del emulador juntas después de arreglar `LockGuardAccountDeletedTest`. Córrelas completas en esta tarea.

## Objetivo
Cambiar el correo de la cuenta. Lo decidió el equipo (DEC-63, cierra PEND-13; contrato 1.3.0):
- reautenticación (contraseña maestra + TOTP si hay 2FA);
- código de 6 dígitos al correo nuevo;
- aviso al correo actual (eso es del servidor);
- **límites:** 3 solicitudes por hora, 5 por día y **24 h** después de un cambio completado. Al pasarse responde `429 rate_limited` con `retryAfterSeconds`;
- el correo nuevo debe ser **distinto del actual tras normalizar** (recortar espacios, minúsculas, NFC). Si no, el servidor responde `400 validation_failed`.

**Figma** (página Minimalista, fila «Tarea 21 · Cambiar el correo»):
- Ajustes con la tarjeta de la cuenta con flecha `227:4476`;
- Correo · nuevo `227:4534`;
- Correo · espera de 24 h `227:4726`;
- Correo · código `227:4802`;
- Correo · igual al actual `229:4852`;
- la hoja de reautenticación es `ReauthSheet` (`218:4421`).

## Paso 1 · Entrada y pantalla del correo nuevo
- **Ajustes:** la tarjeta de la cuenta lleva una flecha y al tocarla abre «Correo». Sin red: `MessageBar` «Necesitas conexión para cambiar tu correo.» y no navega.
- **Pantalla `227:4534`:**
  - aviso oscuro «Te enviaremos un código al correo nuevo para confirmarlo. También avisaremos a tu correo actual.»;
  - «Correo actual», de solo lectura y atenuado;
  - «Correo nuevo»;
  - botón «Continuar», siempre activo (tarea 15).
- **Validaciones antes de pedir nada**, al salir del campo o al tocar el botón:
  - formato con `EmailFormat` → el mensaje de la tarea 15;
  - **igual al actual tras normalizar** → «Es tu correo actual. Escribe uno distinto.» (`229:4852`). Usa una función compartida `EmailNormalizer` (trim + `lowercase(Locale.ROOT)` + NFC), la misma que use el backend falso.
- Si todo está bien: `ReauthSheet` con «Para cambiar tu correo escribe tu contraseña maestra y el código de tu app.» y el botón «Enviar código». Luego **EP-15** con `newEmail` normalizado y `Reauth`.
- **Respuestas:**
  - **202** → pantalla del código. Responde 202 aunque el correo esté en uso (anti-enumeración): no lo distingas;
  - **`429 rate_limited`** → se cierra la hoja y el campo muestra, con ícono de reloj y en gris (no es error del usuario, `227:4726`), «Podrás cambiar tu correo de nuevo en N h» (o «en N min» con menos de una hora, «en N s» con menos de un minuto), calculado con `retryAfterSeconds` y actualizado cada minuto. Mientras dure, «Continuar» no llama al servidor y repite el mensaje;
  - `reauth_required` → como en las tareas 17–20 (incluida la regla de la `key_version` mayor);
  - `400 validation_failed` → el mensaje de «igual al actual» (por si la normalización difiere);
  - sin red o 5xx → el genérico.

## Paso 2 · Código (`227:4802`)
- Reutiliza el diseño de Verificar correo:
  - «Te enviamos un código de 6 dígitos a {correo nuevo}. Tu correo cambia cuando lo confirmes.»;
  - `CodeField`;
  - «Confirmar correo».
- **EP-16** con el código:
  - **200** → actualiza `account.email` en Room con el correo que devuelve el servidor y vuelve a Ajustes con `MessageBar` «Tu correo ahora es {correo}»;
  - código incorrecto → «Código incorrecto. Te quedan N intentos.» (los textos de Verificar correo);
  - vencido o sin intentos → «Este código ya no sirve. Pide uno nuevo.»;
  - `429` → el bloqueo con su cuenta regresiva.
- «¿No te llegó? **Pide otro código**» vuelve a la pantalla del correo nuevo **con el correo ya escrito**: pedir otro código es una solicitud nueva, con reautenticación y contando para el límite. Sustituye el «Puedes pedir otro código en 0:45» de Figma.
- **Atrás** en el código vuelve a la pantalla del correo nuevo. El correo **no** cambia hasta confirmar.
- **«Modo demostración»:** con el backend falso, muestra el código como en el registro (tarea 10), solo en debug (E1).

## Paso 3 · El correo en el resto de la app
- **Tarjeta de Ajustes:** muestra el correo de Room.
- **Otro teléfono:** cuando cualquier pantalla reciba `getAccount()` con un correo distinto del local (por ejemplo, Ajustes en otro teléfono), actualiza `account.email` en Room **solo si el `userId` coincide**. Así la recuperación de `login()` (tarea 19), que identifica la cuenta por el correo tecleado, sigue funcionando con el correo nuevo.
- **Iniciar sesión** con el correo nuevo funciona en este teléfono; con el viejo responde como una cuenta inexistente.

## Backend falso (solo el falso)
**EP-15:**
- reautenticación;
- normalización y `400 validation_failed` si es el mismo;
- 202 aunque el correo esté en uso;
- código de 6 dígitos que reemplaza al anterior, 15 min y 5 intentos;
- **límites de DEC-63** (3/h y 5/día por cuenta, contando las aceptadas; 24 h desde el último `email_changed`) con `retryAfterSeconds` exacto;
- registra `email_change_requested`.

**EP-16:**
- consume el código, cambia el correo (si en ese momento ya lo usa otra cuenta, responde como código inválido);
- registra `email_changed`;
- devuelve `Account`.

Usa un reloj inyectable para probar los límites sin esperar.

## Terminado cuando
- **Pruebas de `EmailNormalizer`:** espacios, mayúsculas, NFC (correo con «é» compuesta y descompuesta) y que los puntos de Gmail **no** se quitan.
- **Pruebas de ViewModel:**
  - formato;
  - igual al actual;
  - reauth;
  - 202 → código;
  - 429 con horas, minutos y segundos, con el texto que se actualiza;
  - código incorrecto, vencido y bloqueado;
  - «Pide otro código» con el correo escrito;
  - Atrás no cambia nada;
  - el 200 actualiza Room.
- **Backend falso:**
  - la cuarta solicitud en una hora → 429;
  - la sexta en un día → 429;
  - otra solicitud antes de 24 h tras un cambio → 429 con los segundos que faltan;
  - a las 24 h ya se puede;
  - mismo correo → 400;
  - correo en uso → 202 sin código efectivo;
  - un código nuevo invalida el anterior;
  - entrar con el correo nuevo funciona y con el viejo no.
- **Pruebas de Compose:** textos de Figma de las vistas, el mensaje de espera y el de igual al actual.
- **Paso 3:** la actualización del correo con `userId` igual y que se ignora con otro `userId`.
- **En el emulador:** cambia el correo de una cuenta de prueba con el código de «Modo demostración», cierra sesión y entra con el nuevo; intenta otro cambio y ve la espera.
- La corrida **completa** de `connectedDebugAndroidTest` (salvo E3), además de `testDebugUnitTest`, lint y `assembleRelease`. Commits hechos y subidos (el grafo v1.6.2 y `revisiones-pendientes.md` van en un commit `docs:` aparte).
- `security-reviewer` revisa los Pasos 1 y 3 (normalización, anti-enumeración y la actualización del correo local). **REQUIERE REVISIÓN HUMANA.**

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
