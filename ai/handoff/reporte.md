# Reporte · Tarea 05 · Caracteres invisibles, 2FA al entrar (SCR-07) y código de respaldo (SCR-08)

**Estado:** hecha
**Fecha:** 2026-10-17

## Qué se hizo
- **Pendiente de la tarea 04 (emulador):**
  - Con 140 caracteres el medidor dice «Usa como máximo 128 caracteres».
  - Con la hoja de Términos abierta, la app tiene 2 ventanas con FLAG_SECURE: la actividad y la hoja (`SecureOn`).
  - El caso «ﷺ» no se puede escribir con `adb`; lo cubren las pruebas unitarias.
- **Paso 1 · Caracteres invisibles (DEC-31):**
  - `MasterPasswordPolicy.hasForbiddenCharacters` rechaza, sobre el texto normalizado:
    - controles (Cc);
    - U+200B, U+2060, U+FEFF, U+200E, U+200F, U+202A–202E y U+2066–2069;
    - puntos de código sin asignar.
  - Permite U+200D y U+FE0F.
  - En Crear cuenta: no se calcula el puntaje, no se puede crear la cuenta, `passesPolicy` lo vuelve a revisar y el medidor dice «Tu contraseña tiene caracteres invisibles que no se pueden usar» en `error`.
  - Al entrar no se usa.
- **Paso 2 · Bloqueo por intentos (DEC-32):**
  - `core/common/Countdown`: cuenta regresiva común con la `TimeSource` inyectada. Verificar correo ahora también la usa.
  - En Iniciar sesión, 2FA y código de respaldo: «Demasiados intentos. Vuelve a intentarlo en N min.» en `error` con el ícono de información, y el botón deshabilitado mientras dura. Los minutos se redondean hacia arriba.
  - Los minutos llegan por `onLockedOut(minutes)` o `onResult(LockedOut)` (por ahora solo en las vistas previas y las pruebas).
- **Paso 3 · 2FA al entrar (SCR-07):**
  - Disco de 104 con el teléfono, «Confirma / que eres tú», `CodeField`, «Continuar» y «¿Sin tu teléfono? **Usa un código de respaldo**» (componente nuevo `PromptLink`, que también usa ahora Iniciar sesión).
  - Iniciar sesión con campos válidos → `Routes.TWO_FACTOR`, con `// TODO(OP-14)`.
  - «Continuar» se habilita con 6 dígitos (`// TODO(OP-15)`, destino SCR-39).
  - Error «Código incorrecto. Revisa que la hora de tu teléfono sea automática.»: vacía las casillas y no muestra intentos.
  - Sin `SecureScreen`; el código no sale en `toString()`.
- **Paso 4 · Código de respaldo (SCR-08):**
  - Disco con la llave, campo «Código de respaldo» con ícono de llave y «Entrar».
  - `BackupCodeFormat` (Crockford): mayúsculas, quita espacios y guiones, O→0, I/L→1, descarta lo que no es del alfabeto (también U), máximo 8.
    - `InputTransformation` guarda los 8 caracteres.
    - `OutputTransformation` dibuja el guion «XXXX-XXXX» sin guardarlo (ver diferencias).
  - El código vive en `remember { TextFieldState() }` (nueva variante de `LabeledTextField` con `TextFieldState`). Lleva `SecureScreen()` y `ExcludeFromAutofill()`, y se vacía en `ON_STOP`, al salir, tras un rechazo y tras un bloqueo.
  - El ViewModel solo sabe el largo; `Submit` lleva el código de paso, con `toString` oculto, y lo vuelve a validar.
  - «Entrar» se habilita con 8 caracteres (`// TODO(OP-16)`). Error «Ese código no es válido o ya se usó.» Atrás regresa a 2FA.
- **Componentes nuevos:** `InlineErrorMessage`, `PromptLink` y `PasteOnlyTextToolbar` (`core/security`).

## Compilación y pruebas (salida real)
- `assembleDebug` y `testDebugUnitTest`: `BUILD SUCCESSFUL in 1m 41s` · **123 pruebas, 0 fallas**. Nuevas o ampliadas:
  - `ForbiddenCharactersTest` 8: U+200B, U+202E y U+0007 rechazados; el resto de la lista; 👨‍👩‍👧 y ❤️ aceptados; U+0378 sin asignar rechazado.
  - `BackupCodeFormatTest` 7: «k7qd 2m9x» → «K7QD-2M9X», O→0, I/L→1, U descartada, tope de 8, guion, `isValid`.
  - `TwoFactorViewModelTest` 6.
  - `BackupCodeViewModelTest` 5.
  - `LoginViewModelTest` 11: navegación a 2FA, bloqueo con cuenta regresiva y redondeo de minutos.
  - `SignUpViewModelTest` 29: caracteres invisibles y emoji compuesto.
- `assembleRelease`: `BUILD SUCCESSFUL in 2m 3s`.
- **Emulador (Pixel 7 Pro, API 36, solo `-s emulator-5554`):**
  - Onboarding → «Ya tengo una cuenta» → Iniciar sesión → «Entrar» → 2FA.
    - Casillas en 473 dp (Figma 472). El enlace cabe en una línea.
    - Sin FLAG_SECURE. Con 6 dígitos se habilita «Continuar».
  - «Usa un código de respaldo» → código de respaldo.
    - Con FLAG_SECURE.
    - Escribir «k7qd 2m9x» deja «K7QD-2M9X» y habilita «Entrar».
  - Atrás → 2FA → Iniciar sesión. Sin crashes.
  - Para reinstalar tuve que liberar cachés del emulador (`pm trim-caches`), porque estaba al 94 %.

## Revisión de seguridad (`security-reviewer`, Pasos 1 y 4)
Sin hallazgos críticos ni altos.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Media | El teclado podía sugerir y **aprender** el código de respaldo (`KeyboardType.Ascii`; confirmado en el emulador: `inputType=0x1001`) | **Corregido:** `KeyboardType.Password` (`inputType=0x1081`, confirmado); las mayúsculas las pone el filtro |
| 2 | Media | DEC-31 deja pasar invisibles comunes: U+00AD guion suave, U+061C, U+034F, U+180E, U+2061–2064, U+206A–206F, otros selectores de variación, U+2028/2029, rellenos hangul, U+2800 | **No implementado:** cambia DEC-31 (ver dudas) |
| 3 | Baja | «Sin asignar» depende de la versión de Unicode del teléfono: un carácter nuevo aceptado en un teléfono reciente podría normalizarse distinto en uno viejo | **No implementado:** decisión (ver dudas) |
| 4 | Baja | El campo del código ofrecía Copiar/Cortar: el código podía quedar en el portapapeles sin marca de sensible ni borrado | **Corregido:** `PasteOnlyTextToolbar` deja solo Pegar y Seleccionar todo |
| 5 | Baja | Se aceptan surrogates solitarios (Cs), que en UTF-8 se vuelven «?» y harían iguales contraseñas distintas | **No implementado:** cambia DEC-31. Para OP-11: codificar con `CodingErrorAction.REPORT` |
| 6 | Baja | `submit` del código solo revisaba el largo; falta evitar envíos dobles | **Corregido en parte:** se vuelve a validar con `BackupCodeFormat.isValid`. El «uno a la vez» queda en el `TODO(OP-16)`, porque sin backend `isLoading` nunca volvería a `false` |
| 7 | Info | **Mis pruebas tenían caracteres invisibles literales, incluido U+202E** («Trojan Source»): la herramienta de escritura convirtió los escapes | **Corregido:** todos van como `\uXXXX`, también los combinantes de las pruebas NFC/NFD, para que un editor no los altere |
| 8 | Info | Autocompletado con `ContentType` nulo | Ya probado en la tarea 02: la exclusión de la ventana raíz deja al servicio sin nodos |
| 9 | Info | Copias en las transformaciones | Límite de DEC-27 |
| 10 | Info | Crockford con «ı» o «ſ» | No junta códigos válidos distintos; sin cambios |

## Diferencias con Figma o con la tarea
- **Guion del código de respaldo:** la tarea decía que el `InputTransformation` agrega el guion. Lo dibuja un `OutputTransformation`, y el campo guarda los 8 caracteres sin guion. Así borrar y mover el cursor funcionan sobre los caracteres reales y no hay un guion que el usuario tenga que borrar.
- **Teclado del código de respaldo:** la tarea pedía `KeyboardType.Ascii`; se usa `Password` (hallazgo 1). Las mayúsculas automáticas siguen pedidas, y el filtro las garantiza.
- **Bloqueo en Iniciar sesión:** va en la línea bajo la contraseña, con prioridad sobre «Correo o contraseña incorrectos» y la ayuda.
- **«Usa un código de respaldo»** va en 14/20 (`labelMedium`), como en Figma.

## Dudas o decisiones que tomé
- **¿Ampliar la regla de invisibles (hallazgos 2, 3 y 5)?** La propuesta es:
  - rechazar la propiedad Unicode *Default_Ignorable_Code_Point*, salvo U+200D, U+FE0F, U+200C (persa) y las etiquetas U+E0020–E007F (banderas);
  - rechazar también Cs y los rellenos hangul;
  - medir «asignado» contra una versión fija de Unicode (10.0, la de minSdk 28) en vez de la del teléfono;
  - y aparte, decidir si se quita U+FE0E/FE0F al normalizar, porque hoy «❤» y «❤️» dan llaves distintas según el teclado.

  Todo esto cambia DEC-31 y la derivación, así que debería decidirse antes de la primera cuenta real.
- **Archivos sin commit que no son míos:** `docs/bd/end_security_db.drawio` y `docs/bd/.$end_security_db.drawio.bkp`. No están en mis comandos de commit; el `.bkp` parece un respaldo del editor y probablemente no debería subirse.
- **Commits:** van como comandos en el mensaje final. Son tres grupos porque `strings.xml` y la navegación mezclan varios pasos.

## Revisión
- [x] REQUIERE REVISIÓN HUMANA: no se tocó `core/crypto`, `core/session` ni SQL, pero sí la política de la contraseña maestra, el manejo del código de respaldo (un secreto) y `core/security` (`PasteOnlyTextToolbar`). Los pasos 1 y 4 pasaron por `security-reviewer`.
