# Reporte · Tarea 21 · Cambiar el correo (OP-26; EP-15, EP-16; DEC-63)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** el Paso 1 y el Paso 3 tocan autenticación e identificación local de la cuenta: `ChangeEmailImpl`, `AccountEmailSync` (y el decorador `EmailSyncingAccountApi`), `ServerAccountCheck` (comparación por correo normalizado), `EmailNormalizer`, `AccountDao.updateEmail` (SQL) y `AuthRepositoryImpl.normalizeEmail`. Pasaron por `security-reviewer` (ver abajo); los hallazgos alto y medios quedaron corregidos.

Antes de empezar se subió en un commit aparte (`docs:`) el grafo v1.6.2 (RSG-14) y `revisiones-pendientes.md`.

## Qué se hizo

### Paso 1 · Entrada y pantalla del correo nuevo (Figma `227:4476`, `227:4534`, `227:4726`, `229:4852`)
- **Ajustes:** la tarjeta de la cuenta lleva flecha y abre «Correo»; sin red (se pregunta al servidor) sale `MessageBar` «Necesitas conexión para cambiar tu correo.» y no navega.
- **Pantalla «Correo»:** aviso oscuro, «Correo actual» (solo lectura y atenuado, del Room), «Correo nuevo» y «Continuar» (siempre activo). Validaciones al salir del campo y al tocar el botón: formato (`EmailFormat`, mensaje de la tarea 15) y **igual al actual tras normalizar** → «Es tu correo actual. Escribe uno distinto.».
- **`EmailNormalizer`** (nuevo, `core/common`): trim + `lowercase(Locale.ROOT)` + NFC; **no** quita puntos ni `+alias` de Gmail. Lo usan la app, el backend falso y ahora también `AuthRepositoryImpl.normalizeEmail` (antes `trim().lowercase()`).
- **«Continuar» con todo bien** abre `ReauthSheet` («Para cambiar tu correo escribe tu contraseña maestra y el código de tu app.», «Enviar código»). Pedí un parámetro nuevo `showCode` en la hoja: **sin 2FA no hay campo de código** y el texto es «…escribe tu contraseña maestra.». Luego **EP-15** con el correo normalizado y `Reauth` (`auth_key` derivada en el teléfono con `ReauthRunner`).
- **Respuestas:** 202 → pantalla del código (sin distinguir si el correo estaba en uso); **429** → se cierra la hoja y el campo muestra, gris y con reloj, «Podrás cambiar tu correo de nuevo en N h / N min / N s», que cuenta hacia atrás (se redondea hacia arriba y el estado solo cambia al cambiar lo que se muestra); durante la espera «Continuar» no llama al servidor; `reauth_required` → «Contraseña o código incorrectos.» (con la regla de la `key_version` mayor: el mensaje del 409); `400` → el de «igual al actual»; sin red o 5xx → el genérico en la hoja.

### Paso 2 · Código (Figma `227:4802`)
- Mismo diseño que Verificar correo: «Te enviamos un código de 6 dígitos a {correo nuevo}. Tu correo cambia cuando lo confirmes.», `CodeField` y «Confirmar correo». **EP-16:** 200 → `account.email` de Room con el correo que devuelve el servidor y vuelve a Ajustes con «Tu correo ahora es {correo}»; código incorrecto («Código incorrecto. Te quedan N intentos.»), vencido o sin intentos («Este código ya no sirve. Pide uno nuevo.») y bloqueo con sus minutos.
- «¿No te llegó? **Pide otro código**» vuelve al correo nuevo **con el correo ya escrito** (otra solicitud: reautenticación y cuenta para el límite). **Atrás** en el código vuelve al correo nuevo y no cambia nada. «Modo demostración · Código: N» con el backend falso (solo debug).
- Un solo destino (`CHANGE_EMAIL`) con dos pasos y un solo ViewModel; la contraseña vive solo en el `TextFieldState` de la hoja.

### Paso 3 · El correo en el resto de la app
- **Tarjeta de Ajustes:** muestra el correo de Room (ya lo hacía).
- **Otro teléfono:** `EmailSyncingAccountApi` (decorador de `AccountApi` enlazado en Hilt) hace que **cualquier `getAccount()`** (Ajustes, 2 pasos, contraseña, eliminar) guarde el correo del servidor en Room **solo si el `userId` coincide** (`AccountEmailSync`), sin tocar el resto de esas pantallas.
- **Iniciar sesión:** con el correo nuevo entra; con el viejo responde como una cuenta inexistente (probado contra el backend falso).

### Backend falso (solo el falso)
EP-15: reautenticación; normalización y `400` si es el mismo (antes de contar el límite); 202 aunque el correo esté en uso; código de 6 dígitos que reemplaza al anterior, 15 min y 5 intentos; **límites de DEC-63** (3/h y 5/día por cuenta contando las aceptadas, 24 h desde el último `email_changed`) con `retryAfterSeconds` exacto; registra `email_change_requested`. EP-16: consume el código, cambia el correo (si en ese momento ya lo usa otra cuenta, responde como código inválido), registra `email_changed` y devuelve `Account`. El reloj es el inyectable de siempre.

## Revisión con `security-reviewer` (Pasos 1 y 3)
- **1 (alta) · la sincronización del correo debilitaba las defensas de la tarea 19.** `AccountEmailSync` guardaba cualquier correo del mismo `userId`; un servidor podía mandar uno mal formado o con mayúsculas, que no iguala al tecleado, y `ServerAccountCheck` dejaba de identificar la cuenta local (parámetros más baratos o sal distinta sin comparar). **Corregido:** solo se guarda un correo válido, **ya normalizado**, de ≤254 caracteres y solo si la cuenta **no cambió de sal** (eso es una recuperación, no un cambio de correo); además `ServerAccountCheck` compara con **ambos lados normalizados**.
- **2 (media) · oráculo de enumeración entre EP-15 y EP-16.** Con el correo ocupado el falso quitaba el cambio pendiente y EP-16 respondía «ya no sirve»; con uno libre, «incorrecto, te quedan N». **Corregido en el falso:** el ocupado crea un cambio pendiente simulado (código al azar que nadie recibe, mismos intentos y vigencia). **Para el backend real (anotar):** EP-16 debe responder igual en los dos casos, y el envío del correo debe encolarse después del COMMIT con la misma latencia visible que el caso del correo ocupado.
- **3 (media) · una respuesta atrasada de `getAccount()` podía revertir el correo recién cambiado.** **Corregido** serializando las escrituras con un `Mutex` (`AccountEmailSync` es `@Singleton`).
- **4 y 5 (bajas):** la escritura tras el 200 va en `NonCancellable`; cualquier `validation_failed` se muestra como «igual al actual» (no filtra nada; se deja así hasta que el contrato distinga otra causa).
- **6–7:** la normalización es determinista (sin NFKC: los caracteres de ancho completo cuentan como distintos, decisión del servidor); la contraseña y el código no se registran; la pantalla del código no lleva `SecureScreen` (código de un solo uso, igual que Verificar correo). El revisor lo dejó como recomendación opcional.

## Pruebas (salida real)
- **`testDebugUnitTest`: 952, 0 fallas** (antes 878). Nuevas:
  - `EmailNormalizerTest` (6): espacios, mayúsculas, NFC («é» compuesta y descompuesta), puntos y `+alias` de Gmail que no se quitan, la «I» turca (no depende del idioma) y que es idempotente.
  - `ChangeEmailViewModelTest` (37): formato, igual al actual (espacios, mayúsculas, NFC), los puntos de Gmail, reauth con y sin 2FA, 202 → código, `reauth_required`, 409, genérico, 400, 429 con horas, minutos y segundos (el texto se actualiza y desaparece), «Continuar» sin llamar durante la espera, código incorrecto, vencido y bloqueado, «Pide otro código» con el correo escrito, Atrás (no cambia nada), el 200.
  - `ChangeEmailImplTest` (26, **Argon2 real contra el backend falso** con reloj inyectable): la cuarta solicitud en una hora es 429 con los segundos exactos; la sexta del día es 429; tras un cambio hay que esperar 24 h (con 86 390 s, luego 1 s y a las 24 h se puede); mismo correo → 400 sin gastar solicitudes; correo ocupado → 202 e **indistinguible en EP-16**; un código nuevo invalida el anterior; entrar con el correo nuevo funciona y con el viejo responde como inexistente; 5 intentos; vence a los 15 min; el Paso 3 (mismo `userId` se actualiza, otro se ignora, correo malformado o sin normalizar y otra sal se ignoran, un `getAccount()` de la app guarda el correo del otro teléfono).
  - `SettingsViewModelTest` (+4): la tarjeta con y sin red y el aviso «Tu correo ahora es …».
- **Compose (`ChangeEmailScreensTest`, 17):** textos de Figma de las vistas, el mensaje de espera (horas, minutos, segundos), el de igual al actual, el formato, la hoja con y sin 2FA, el código (errores y «Pide otro código») y la tarjeta de Ajustes con su flecha.
- **`connectedDebugAndroidTest` completa (sin E3): 187 pruebas, 0 fallas**, en dos tandas (105 de `feature.account`, `feature.activity`, `feature.auth` y `navigation`, y 82 del resto), porque el emulador se cae en una sola corrida larga. Incluye las 170 de antes, que la tarea 20 había dejado sin correr juntas.
- **`lintDebug`: sin errores. `assembleRelease`: compila.**

## En el emulador
Capturas de `ChangeEmailScreensTest` comparadas con Figma: Correo nuevo, con la espera de 18 h, con «igual al actual», el código y Ajustes con la flecha coinciden (el campo «Correo actual» se atenuó tras la primera captura y no se volvió a capturar). **No recorrí por la interfaz de la app real** cambiar el correo con el código de «Modo demostración», cerrar sesión, entrar con el nuevo e intentar otro cambio para ver la espera (la prueba de Compose contra la app real se cuelga, ver tarea 13): **queda como prueba a mano**. El mismo flujo sin la interfaz está cubierto contra el backend falso con Argon2 real (`ChangeEmailImplTest`). El emulador se cayó varias veces ("System has crashed") en las corridas largas, así que la suite instrumentada se corrió en dos tandas.

## Decisiones y desviaciones a revisar
- `AccountApi` se enlaza ahora al decorador `EmailSyncingAccountApi` (que delega en `AccountApiImpl`); ninguna otra pieza cambió su constructor.
- `ReauthSheet` ganó `showCode`.
- La espera de DEC-63 usa `Countdown` y solo actualiza el estado al cambiar el texto (cada hora, cada minuto o cada segundo).
- «Correo actual» se atenúa con `alpha(0.6)`.

## Vistas / cambios (cómo llegar)
- **Correo (nuevo y código):** Ajustes → tocar la tarjeta de la cuenta (con red).
- **Hoja de reautenticación:** en «Correo», escribir un correo distinto y tocar «Continuar».
- **«Tu correo ahora es …»:** al confirmar el código, de vuelta en Ajustes.
