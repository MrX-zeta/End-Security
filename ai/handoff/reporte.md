# Reporte · Tarea 03 · Crear cuenta (SCR-05)

**Estado:** hecha
**Fecha:** 2026-10-16

## Qué se hizo
- **Grano de las manchas:** 12 % en tema claro y 7 % en oscuro (grafo v1.3.3).
- **Estimador de fuerza (DEC-31):**
  - `PasswordStrengthEstimator` vive en dominio (Kotlin puro y recibe `CharSequence`) y `ZxcvbnPasswordStrengthEstimator` en datos.
  - Se agregó zxcvbn4j 1.9.0 (`com.nulab-inc:zxcvbn`, licencia MIT, sin dependencias).
  - `MasterPasswordPolicy` fija el mínimo: 12 caracteres y puntaje ≥ 3.
  - Calificador `@DefaultDispatcher` para el trabajo de CPU.
- **Componentes nuevos:**
  - `BackTopBar` y `BackButton`: barra superior con el botón de atrás («Regresar»).
  - `Checkbox`: 24 × 24, radio 7, área táctil de 48 y rol de casilla.
  - `StrengthMeter`: 4 barras con su etiqueta.
  - `NoticeCard`: aviso importante.
  - `PasswordField` ahora puede ir sin el ojo; en ese caso siempre va oculto.
- **Crear cuenta:**
  - `SignUpRoute`, `SignUpScreen`, `SignUpViewModel`, `SignUpUiState` y `PasswordStrengthLevel` (mapeo puntaje → barras, etiqueta y color).
  - Textos y medidas de Figma (nodo 36:119).
  - `Submit` → `// TODO(OP-11)`; no se creó `AuthRepository`.
- **Documentos legales:** `Routes.LEGAL_DOCUMENT` con un marcador «Documento en redacción» para Términos y Aviso de privacidad (`feature/settings/ui/legal`).
- **Navegación (DEC-56):**
  - «Comenzar» deja el onboarding en la pila, así que atrás desde Crear cuenta regresa a él. Lo confirmó la persona del equipo.
  - Desde Iniciar sesión, atrás regresa a Iniciar sesión. Todas las rutas usan `launchSingleTop`.
  - El onboarding se puede volver a terminar al regresar: la protección contra el doble toque ahora usa un `Job`.
- **Seguridad:**
  - Los dos campos usan `remember { TextFieldState() }`, `SecureScreen()` y `ExcludeFromAutofill()`.
  - Se vacían en `ON_STOP` y al salir.
  - Ningún texto de contraseña entra al estado: el ViewModel recibe `PasswordsChanged` y `Submit`, que no son `data class` y tienen `toString()` oculto.
  - La fuerza se calcula fuera del hilo principal con debounce de 150 ms, y la coincidencia con `contentEquals`.

## Commits
| Hash | Mensaje |
|---|---|
| `cd2c472` | docs: grafo v1.3.3 y tarea 03 del relevo (cambios de la sesión de diseño) |
| `a58573a` | fix: grano de las manchas al 12 % en el tema claro (DEC-50) |
| `5c9a3d8` | feat: estimador de fuerza de contraseña con zxcvbn4j (tarea 03, DEC-31) |
| `c34aac4` | feat: componentes para Crear cuenta en el sistema de diseño (tarea 03) |
| `6641549` | fix: el onboarding se puede terminar otra vez al volver de Crear cuenta |
| `fcddf85` | feat: pantalla Crear cuenta SCR-05 (tarea 03) |
| `1b76b3a` | test: pruebas del SignUpViewModel y del medidor de fuerza |
| `bfb1806` | fix: el estimador de fuerza mide sobre una copia y no toca la entrada |
| `99eb7bb` | fix: Crear cuenta verifica la política sobre el texto que se envía |

## Compilación y pruebas (salida real)
- `assembleDebug`: `BUILD SUCCESSFUL in 10s` (41 actionable tasks: 3 executed, 38 up-to-date). Sin errores ni avisos de Kotlin.
- `testDebugUnitTest`: `BUILD SUCCESSFUL in 1m 18s` · **58 pruebas, 0 fallas**:
  - `SignUpViewModelTest` 21/21: habilitación del botón con cada requisito, cálculo pendiente, momento de «no coinciden», envío con el medidor atrasado, reinicio al vaciar, falla del estimador y `toString`.
  - `PasswordStrengthLevelTest` 4/4: puntaje → barras, etiqueta y color.
  - `ZxcvbnPasswordStrengthEstimatorTest` 6/6: incluye que no modifica la entrada.
  - `MasterPasswordPolicyTest` 2/2.
  - `OnboardingViewModelTest` 10/10: doble toque con escritura en curso y regreso desde Crear cuenta.
  - Las 15 pruebas restantes son de tareas anteriores y siguen pasando.
- `assembleRelease`: `BUILD SUCCESSFUL in 2m 7s`. Los diccionarios de zxcvbn4j quedan en el APK con sus rutas originales. El APK sin firmar crece de 3.0 a 3.9 MB.
- **Emulador (Pixel 7 Pro, API 36).** Había un teléfono físico conectado; todos los comandos fueron solo al emulador.
  - **Debug:**
    - Posiciones en dp frente a Figma: título 113 (112), correo 299 (298), contraseña 397 (396), confirmación 523 (522), casilla 697 (696).
    - FLAG_SECURE activo en Crear cuenta e inactivo en el onboarding y en los documentos.
    - Atrás → onboarding (página 3), y «Comenzar» funciona otra vez.
    - Medidor: con 7 caracteres «Usa al menos 12 caracteres»; con una frase larga «Muy fuerte».
    - «No coinciden» no aparece mientras se escribe, aparece al salir de la confirmación y se quita al coincidir.
    - El botón se habilita solo con la casilla marcada.
    - Términos y Aviso de privacidad abren su marcador. Al volver, la casilla sigue como estaba y las contraseñas están vacías.
  - **Release con R8:** lo firmé solo para probar, con la llave de depuración estándar de Android pasada por línea de comandos; no se cambió nada del proyecto.
    - El medidor funciona («Muy fuerte»).
    - Con el formulario completo, «Crear cuenta» verifica y no se cierra la app.
    - Al volver de Términos, los campos están vacíos y el botón deshabilitado.
    - Sin crashes ni clases faltantes en logcat.

## Revisión de seguridad (`security-reviewer`)
Sin hallazgos críticos ni altos; los seis requisitos de seguridad de la tarea se cumplen.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Media | «Crear cuenta» nunca pasaba la verificación (el cálculo pendiente lo rechazaba). Al conectar OP-11 era fácil «arreglarlo» abriendo una carrera contra la política | **Corregido** (`99eb7bb`): `Submit` lleva las contraseñas y `passesPolicy` revisa largo, coincidencia y puntaje sobre ese mismo texto |
| 2 | Media/baja | zxcvbn4j deja copias internas, y **comprobé en el bytecode** que `Strength.wipe()` borra la entrada del llamador si es `StringBuilder` o `WipeableString`. En OP-11 podría dejar en ceros el búfer del que se deriva la llave | **Corregido** (`bfb1806`): se mide sobre un `WipeableString` propio. Prueba de que la entrada queda intacta. Las copias internas (subcadenas, expresiones regulares) son una limitación de DEC-27 |
| 3 | Baja | Al salir, el ViewModel conservaba el puntaje y `canSubmit` de un texto ya borrado | **Corregido:** acción `PasswordsCleared` al salir y en `ON_STOP` |
| 4 | Baja | Cálculos cancelados seguían corriendo; sin límite de longitud | **Corregido en parte:** un `Mutex` deja un cálculo a la vez y los cancelados en espera no corren. **El límite de longitud no se puso** (ver dudas) |
| 5 | Baja | Si el estimador fallaba, la app se cerraba | **Corregido:** el puntaje queda vacío y el botón deshabilitado. La prueba en release fue manual; no hay prueba instrumentada |
| 6 | Info | `toString()` del estado imprimía correo, puntaje y longitud | **Corregido:** `toString()` sin datos en `SignUpUiState` y `EmailChanged` |
| 7 | Info | Accesibilidad: el medidor anuncia el cambio de etiqueta (canal de audio mínimo) | Aceptado; sin cambios |
| 8 | Info | La longitud se cuenta en unidades UTF-16 y no hay normalización Unicode definida | Pendiente para OP-11 (ver dudas) |
| 9 | Info | Dos actualizaciones de estado no atómicas | **Corregido:** una sola actualización con `canSubmit` |

## Diferencias con Figma o con el grafo
- **Confirmación sin ojo:** así está en Figma; sin el botón, el campo va siempre oculto.
- **«Usa al menos 12 caracteres»** va en `on-surface-variant` (información neutra) mientras las barras siguen mostrando el puntaje. La tarea no dice de qué color va.
- **«No coinciden» al tocar «Crear cuenta»:** el botón está deshabilitado mientras no coinciden, así que en la práctica ese caso entra por «Listo» del teclado de la confirmación (que también hace `Submit`) o al salir del campo.
- **Enlaces dentro del texto** («Términos», «Aviso de privacidad»): su área táctil es la del texto (menos de 48 dp), lo usual para enlaces en línea. La casilla sí tiene 48.

## Dudas o decisiones que tomé
- **Push:** no lo hice; dijiste que el push lo haces tú. Vas 9 commits adelante (10 con el de este reporte).
- **Las contraseñas se vacían al abrir Términos o el Aviso:** la pantalla sale de composición y se aplica la regla «se vacían al salir». Desde seguridad es lo correcto (lo confirmó el revisor), pero obliga a escribirlas de nuevo. La alternativa sería mostrar el documento en un diálogo sobre la misma pantalla, a costa de mantener la contraseña viva en memoria mientras se lee.
- **Límite de longitud (hallazgo 4):** el revisor propone `maxLength` (por ejemplo 128) en los campos de contraseña maestra, para acotar zxcvbn y Argon2. No está en el grafo (INV-19), así que no lo puse. ¿Lo registramos en DEC-31?
- **Normalización Unicode (hallazgo 8):** antes de OP-11 hay que decidir NFC o NFKC y la codificación de entrada a Argon2 (DEC-05). Si no, la misma frase escrita con otro teclado podría derivar otra llave.
- **Prueba de release:** zxcvbn4j bajo R8 lo comprobé a mano en el emulador. Una prueba instrumentada (`score("password") <= 1`) lo dejaría automatizado.
- **`LegalDocument`** quedó en `feature/settings/ui/legal`, porque Ajustes tendrá el grupo «Legal y ayuda» (PEND-12).

## Revisión
- [x] REQUIERE REVISIÓN HUMANA: no se tocó `core/crypto`, `core/session` ni SQL, pero sí la pantalla de registro (autenticación), el manejo de la contraseña maestra en memoria y una librería nueva (zxcvbn4j). Ya pasó por `security-reviewer`; ver la tabla de arriba.
