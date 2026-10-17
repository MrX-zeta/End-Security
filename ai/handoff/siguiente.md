# Tarea 05 · Caracteres invisibles, 2FA al entrar (SCR-07) y código de respaldo (SCR-08)

**Estado:** hecha
**Escrita:** 2026-10-16 · sesión de diseño

## Revisión de la tarea 04
Aprobada. Contar el máximo después de NFKC (por el caso «ﷺ») y el tope de 256 en el campo fue mejor que lo que pedí. También
aprobado: el disco de 104, el borde de 2 y el cursor fijo. Respuestas (grafo v1.3.5):
- **Caracteres invisibles (hallazgo 2): sí, con una variante de PRECIS** (DEC-31):
  - **Al fijar una contraseña nueva** (registro y, más adelante, cambio de contraseña) se **rechazan**:
    - controles (Cc);
    - espacios de ancho cero y marcas de dirección: U+200B, U+2060, U+FEFF, U+200E, U+200F, U+202A–202E y U+2066–2069;
    - puntos de código **sin asignar** en el dispositivo.
  - **Se permiten U+200D (ZWJ) y U+FE0F**, porque los emojis compuestos los necesitan. Por eso no se rechaza toda la categoría Cf ni todos los selectores de variación.
  - No se quitan en silencio: se rechazan con el mensaje «Tu contraseña tiene caracteres invisibles que no se pueden usar», en el lugar de la etiqueta del medidor.
  - **Al entrar y al desbloquear solo se normaliza**, sin rechazar, para no dar pistas.
- Hallazgos 3 y 7 (NFKC unifica caracteres parecidos; puntos de código, no grafemas): documentados en DEC-31.
- **Pendiente de la tarea 04:** prueba en el emulador las correcciones de seguridad que quedaron sin ver (máximo tras NFKC y `SecureOn` en la hoja).

## Paso 1 · Rechazo de caracteres invisibles
- `MasterPasswordPolicy` (o una función nueva en dominio, `findForbiddenCodePoints`) aplica la regla de arriba sobre el texto normalizado.
- Crear cuenta: no se puede crear la cuenta mientras haya caracteres prohibidos, y aparece el mensaje.
- Pruebas: contraseña con U+200B rechazada; con U+202E rechazada; emoji 👨‍👩‍👧 (lleva ZWJ) aceptado; ❤️ (lleva FE0F) aceptado; con U+0007 rechazada.

## Paso 2 · Bloqueo por intentos (DEC-32), solo en la interfaz
- En Iniciar sesión, en 2FA y en el código de respaldo hay un estado de error nuevo: «Demasiados intentos. Vuelve a intentarlo en N min.», en `error` y con el ícono de información.
- Mientras dura, el botón está deshabilitado.
- Los minutos vienen del servidor (por ahora del estado de la vista previa) y la cuenta regresiva reutiliza la `TimeSource` de Verificar correo.

## Paso 3 · Verificación en dos pasos al entrar (SCR-07)
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=36-341
- De arriba hacia abajo:
  - `BackTopBar`;
  - disco de 104 con el ícono de teléfono;
  - «Confirma» / «que eres tú», con el texto «Abre tu app de códigos (como Google Authenticator) y escribe el número de 6 dígitos que aparece para End Security.»;
  - `CodeField`;
  - `PrimaryButton` «Continuar»;
  - abajo, «¿Sin tu teléfono? **Usa un código de respaldo**». Corregí ese texto en Figma porque se cortaba.
- Navegación temporal: en Iniciar sesión, `Submit` con los campos válidos va a `Routes.TWO_FACTOR` con `// TODO(OP-14): solo si la cuenta tiene 2FA; si no, a la bóveda`. Atrás regresa a Iniciar sesión.
- `Continuar` se habilita con 6 dígitos y lleva `// TODO(OP-15)`. Su destino real será «Bóveda · cargando» (SCR-39).
- El error de código es «Código incorrecto. Revisa que la hora de tu teléfono sea automática.» No muestra intentos restantes (DEC-32 bloquea al quinto). Las casillas se vacían.
- Sin `SecureScreen`: el código dura 30 s y no se vuelve a aceptar (anti-replay). Pero el código no va en logs ni en `toString()`.

## Paso 4 · Código de respaldo (SCR-08)
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=36-413
- De arriba hacia abajo:
  - `BackTopBar`;
  - disco con la llave;
  - «Usa un código» / «de respaldo», con el texto «Escribe uno de los códigos que guardaste cuando activaste la verificación en dos pasos. Cada código sirve una sola vez.»;
  - campo «Código de respaldo» con ícono de llave;
  - `PrimaryButton` «Entrar».
- **Formato (DEC-12):** 8 caracteres en Base32 de Crockford, mostrados como `XXXX-XXXX`.
  - Un `InputTransformation` pasa a mayúsculas, quita espacios y guiones, convierte O→0 e I/L→1, descarta lo que no sea del alfabeto (`0-9 A-Z` sin I, L, O ni U) y agrega el guion después del cuarto carácter.
  - Teclado `KeyboardType.Ascii` sin autocorrección ni mayúsculas automáticas (`KeyboardCapitalization.Characters`).
- **Es un secreto de larga vida hasta que se usa.** Va en `remember { TextFieldState() }` (no en `UiState`), con `SecureScreen()` y `ExcludeFromAutofill()`, y se vacía en `ON_STOP` y al salir, igual que la contraseña.
- `Entrar` se habilita con 8 caracteres válidos y lleva `// TODO(OP-16)`. El error es «Ese código no es válido o ya se usó.»
- Atrás regresa a 2FA.

## Terminado cuando
- Hay pruebas del Paso 1, del filtro del código de respaldo (incluye «k7qd 2m9x» → «K7QD-2M9X» y «O»→«0»), de los dos ViewModels nuevos y del estado de bloqueo, y pasan.
- Hay `@Preview` de 2FA (vacía, completa, error y bloqueo), del código de respaldo (vacío, completo, error y bloqueo) y de Iniciar sesión con bloqueo.
- `assembleDebug`, `testDebugUnitTest` y `assembleRelease` pasan.
- `security-reviewer` revisa el Paso 1 y el Paso 4.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
