# Reporte · Tarea 02 · Ajuste del onboarding e Iniciar sesión (SCR-04)

**Estado:** hecha
**Fecha:** 2026-10-16

## Qué se hizo
- **Paso 0.1:** ya estaba hecho antes de esta tarea en `b8739a2` («docs: protocolo de relevo de tareas en ai/handoff»), con un mensaje un poco distinto al pedido.
- **Paso 1 · navegación del onboarding (DEC-56):** «Comenzar» → Crear cuenta (SCR-05); «Omitir» y «Ya tengo una cuenta» → Iniciar sesión.
  Acciones `Skip`, `Start` y `AlreadyHaveAccount`; efectos `NavigateToLogin` y `NavigateToSignUp`. Las dos rutas sacan el
  onboarding de la pila. `Routes.SIGN_UP` con un marcador claro «Crear cuenta». `OnboardingViewModelTest` actualizado.
- **Paso 2 · componentes:** `LabeledTextField` y `PasswordField` en `core/designsystem/component` (comparten el marco
  interno `FieldLayout`/`FieldContainer`), íconos de Figma (sobre, candado, ojo, ojo tachado, información) y tipografía
  `displaySmall` 34/42 (título de acceso) y `bodySmall` 13/18 (ayuda y error).
- **Paso 2 · Iniciar sesión:** `LoginRoute`/`LoginScreen` + `LoginViewModel` + `LoginUiState`, `EmailFormat` (dominio, Kotlin puro).
  «Crea tu cuenta» → `SIGN_UP`. `Submit` no llama nada: `// TODO(OP-13, OP-14)`; no se creó `AuthRepository`.
- **Seguridad:**
  - Contraseña en `remember { TextFieldState() }`, `BasicSecureTextField` con `TextObfuscationMode` (Hidden/Visible); nunca en `UiState`, `StateFlow`, `SavedStateHandle` ni logs. El ViewModel solo recibe `PasswordEmptinessChanged(isEmpty)`.
  - Al salir de la pantalla se vacía el campo (`clearText()`).
  - `core/security/SecureScreen.kt`: FLAG_SECURE con conteo por ventana (para no quitarlo en las transiciones).
  - `core/security/ExcludeFromAutofill.kt`: la pantalla queda fuera del autocompletado del sistema y se cancela la sesión de autocompletado al entrar y al salir.
  - Teclado: correo `Email` + `Next` (sin autocorrección); contraseña `Password` sin autocorrección + `Done`.
- **Comportamiento:** «Entrar» se habilita con un correo de forma válida y una contraseña no vacía; error único
  «Correo o contraseña incorrectos.» bajo la contraseña, en `error`, con ícono de información y anuncio para TalkBack;
  `imePadding` + `verticalScroll` + `bringIntoView` para que «Entrar» quede visible con el teclado abierto.
- **Ajuste global:** el interlineado de la tipografía ahora es igual que en Figma (ver diferencias).

## Commits
| Hash | Mensaje |
|---|---|
| `2dfe72f` | feat: «Comenzar» del onboarding lleva a Crear cuenta (tarea 02, DEC-56) |
| `1f8bbde` | feat: campos de texto del sistema de diseño (tarea 02) |
| `0192d5c` | feat: SecureScreen y exclusión del autocompletado en core/security (tarea 02) |
| `f1080e6` | fix: interlineado de los textos igual que en Figma |
| `1807eaf` | feat: estado deshabilitado del botón primario |
| `fbf0d7f` | feat: pantalla Iniciar sesión SCR-04 (tarea 02) |
| `1d1c21c` | test: pruebas del LoginViewModel y de EmailFormat |

## Compilación y pruebas (salida real)
- `assembleDebug`: `BUILD SUCCESSFUL in 2s` · 41 actionable tasks: 41 up-to-date (la compilación anterior, con todos los cambios, terminó sin errores ni avisos de Kotlin).
- `testDebugUnitTest`: `BUILD SUCCESSFUL in 8s` · 19 pruebas, 0 fallas:
  `LoginViewModelTest` 7/7, `EmailFormatTest` 2/2, `OnboardingViewModelTest` 9/9, `ExampleUnitTest` 1/1.
- **Revisión en el emulador (Pixel 7 Pro, API 36):**
  - Capturas de Iniciar sesión salen negras y la ventana tiene `fl=… SECURE …` (FLAG_SECURE activo); en Crear cuenta el flag ya no está.
  - Posiciones frente a Figma (dp desde arriba): correo 475 (474), contraseña 579 (578), ayuda 643 (642), «Entrar» 693 (692), enlace 757 (756).
  - Ojo 48 × 48 táctil, enlace «Crea tu cuenta» 48 de alto, etiquetas asociadas a los campos («Correo», «Contraseña maestra»), ojo anuncia «Mostrar/Ocultar contraseña».
  - «Entrar» deshabilitado con datos incompletos o correo inválido y habilitado con ambos válidos; con el teclado abierto queda completo a la vista.
  - Navegación: «Crea tu cuenta» → Crear cuenta → atrás vuelve a Iniciar sesión; onboarding «Comenzar» → Crear cuenta y atrás sale de la app (onboarding fuera de la pila). Sin crashes en logcat.

## Diferencias con Figma o con el grafo
- **Campo: radio 16 y una sola sombra, no radio 14 ni sombra neumórfica.** La tarea pide «radio 14, sombra neumórfica
  igual que en Figma», pero Figma (nodo 19:178) y DEC-50 tienen radio 16 y una sola sombra `0 8 20 shadow-dark`. Seguí Figma y DEC-50.
- **Título 34/42**, no 32/40 como el onboarding: así está en Figma; se usa `displaySmall`.
- **Grano de la mancha:** Figma usa blanco al 12 % en esta pantalla; el grafo (DEC-50 v1.3.2) dice blanco al 7 % en ambos
  temas. Usé el token del grafo. Si debe ser 12 % en claro, hay que actualizar DEC-50.
- **Mancha pequeña amarilla:** está en Figma y en la tarea, aunque DEC-50 reserva el amarillo para acciones. Usa `primary`.
- **Estado deshabilitado de «Entrar»:** Figma no lo define; usé opacidad al 40 % (convención de Material).
- **Error:** reemplaza a la línea «Si la olvidas…» mientras se muestra (las dos van en el mismo lugar bajo la contraseña).
- **Interlineado:** Compose recortaba el interlineado de la primera y última línea (≈6 dp menos por texto que Figma).
  Lo corregí en `Type.kt`; también mueve un poco el onboarding hacia sus medidas de Figma.

## Dudas o decisiones que tomé
- **Paso 0.2 (`git push`):** no lo hice. En esta sesión me pediste que el push lo hagas tú. Vas 7 commits adelante
  (8 con el de este reporte).
- **Autocompletado:** `BasicSecureTextField` se marca solo como `ContentType.Password` y Compose no deja quitarlo campo por
  campo, así que excluí **toda la pantalla** del autocompletado. Consecuencia: el correo tampoco se autocompleta.
- **Accesibilidad expone la contraseña:** en el árbol de accesibilidad el `EditText` de la contraseña trae el texto real
  (marcado `password=true`). TalkBack no lo lee, pero un servicio de accesibilidad malicioso podría leerlo; los `EditText`
  de vistas exponen puntos. No lo parcheé (sobrescribir la semántica de Compose es frágil). Propuesta para revisar.
- **Borrado de la contraseña:** `TextFieldState` no permite sobrescribir su memoria; se vacía al salir. La copia a
  `CharArray` con borrado llegará con OP-13/OP-14.
- **`LabeledTextField`/`PasswordField`:** `PasswordField` no llama a `LabeledTextField` (uno usa `BasicTextField` con `String`
  y el otro `BasicSecureTextField` con `TextFieldState`); comparten el marco interno, que es lo que da el mismo aspecto.
- **«Crea tu cuenta»** quedó como composable privado de la pantalla; si Crear cuenta necesita «¿Ya tienes cuenta? Inicia
  sesión», conviene moverlo a `core/designsystem`.

## Revisión
- [x] REQUIERE REVISIÓN HUMANA — no se tocó `core/crypto`, `core/session` ni SQL, pero sí la **pantalla de autenticación** y
  `core/security` (`SecureScreen`, `ExcludeFromAutofill`). Sugiero pasar `LoginScreen.kt`, `PasswordField.kt`,
  `SecureScreen.kt` y `ExcludeFromAutofill.kt` por el subagente `security-reviewer`, en especial el punto de accesibilidad.
