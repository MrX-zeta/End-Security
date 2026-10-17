# Tarea 02 · Ajuste del onboarding e Iniciar sesión (SCR-04)

**Estado:** pendiente
**Escrita:** 2026-10-16 · sesión de diseño

## Revisión de la tarea 01
Aprobada. El código del onboarding y del sistema de diseño está bien hecho: UDF, accesibilidad, «Quitar animaciones»,
sin hex fuera de `Color.kt`, respaldos y tráfico en claro cerrados. Tus pendientes del grafo ya quedaron registrados
(grafo v1.3.2): `danger`/`success`/`blob-grain` en DEC-50, el nombre «End Security», `onboarding_completed` en KV-DATASTORE.
Mapear `danger` a `colorScheme.error` y `success` a los colores extendidos es correcto; `on-danger` no hace falta todavía.
Revisé GitHub hasta `1e2eda7`. Los commits `ca82968`, `a4347c0` y `df91dd6` no están en GitHub, así que no los vi.

## Paso 0 · Orden
1. Haz un commit con `CLAUDE.md` y `ai/handoff/README.md`: `docs: protocolo de relevo entre sesiones`. El equipo ya lo aprobó.
2. Al terminar esta tarea, haz `git push` de la rama para que pueda revisar todo desde GitHub.

## Paso 1 · Cambio de navegación en el onboarding (DEC-56, actualizado)
- «Comenzar» (página 3) → **Crear cuenta (SCR-05)**. Quien empieza de cero quiere registrarse, no entrar.
- «Ya tengo una cuenta» y «Omitir» → Iniciar sesión (SCR-04), como ahora.
- Crea `Routes.SIGN_UP` con un marcador como el de `LoginScreen` (tema claro y título «Crear cuenta»). Se implementa en la tarea 03.
- El efecto del ViewModel necesita indicar el destino (por ejemplo `NavigateToSignUp` / `NavigateToLogin`). Las dos rutas
  sacan el onboarding de la pila. Actualiza `OnboardingViewModelTest`.

## Paso 2 · Iniciar sesión (SCR-04)
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=19-144 (tema claro, DEC-51).
Si no tienes el MCP de Figma, este es el contenido de arriba hacia abajo:
- La barra superior con «End Security». Arriba a la derecha, una mancha oscura grande con grano y una mancha `primary` pequeña encima (reutiliza `OrganicBlob`).
- `TwoWeightTitle` «Hola» / «de nuevo». Debajo, el texto «Entra con tu correo y tu contraseña maestra.»
- Campo «Correo» con ícono de sobre.
- Campo «Contraseña maestra» con ícono de candado y, a la derecha, un botón cuadrado `primary` con el ojo para mostrar u ocultar la contraseña.
- La línea de ayuda «Si la olvidas, no podremos recuperarla.» con ícono de información. No hay «¿Olvidaste tu contraseña?» y no se agrega, porque el diseño es de conocimiento cero.
- `PrimaryButton` «Entrar».
- La línea «¿Primera vez? **Crea tu cuenta**», donde el enlace lleva a `SIGN_UP`.
- Los íconos que falten (sobre, ojo, ojo tachado, información) salen de la fila de íconos de Figma, nodo `35:14`.

**Componentes nuevos** en `core/designsystem/component`: `LabeledTextField` (etiqueta arriba, ícono al inicio, superficie
`surface`, radio 14, sombra neumórfica igual que en Figma) y `PasswordField` (usa el anterior y agrega el botón del ojo).
Servirán también para Crear cuenta y Desbloquear.

**Seguridad (reglas no negociables 5, 6 y 7):**
- La contraseña vive en un `TextFieldState` creado con `remember { TextFieldState() }`. **No uses
  `rememberTextFieldState()` ni `rememberSaveable`**, porque guardan el texto en el Bundle. Usa `SecureTextField` o
  `BasicSecureTextField` con `TextObfuscationMode` para el ojo.
- La contraseña nunca pasa por `UiState`, `StateFlow`, `SavedStateHandle` ni logs. El ViewModel solo recibe
  `passwordIsEmpty: Boolean` para habilitar el botón. Cuando exista el envío (OP-13/OP-14), se copiará a `CharArray` al tocar «Entrar».
- Crea `core/security/SecureScreen.kt`, un composable que activa `FLAG_SECURE` en la ventana mientras la pantalla está
  visible y lo quita al salir. Úsalo en esta pantalla.
- La contraseña maestra no usa autocompletado (`ContentType` vacío, o `importantForAutofill = no`): no queremos que otro
  gestor la guarde. El correo sí puede usar autocompletado (`ContentType.EmailAddress`).
- Teclado: el correo usa `KeyboardType.Email` e `ImeAction.Next`; la contraseña usa `KeyboardType.Password`, sin autocorrección, e `ImeAction.Done`.

**Comportamiento:**
- `LoginViewModel` con estado `email`, `isEmailValid`, `canSubmit`, `isLoading` y `error`. «Entrar» se habilita con un
  correo de forma válida y una contraseña no vacía.
- **Todavía no hay backend.** `Submit` no llama nada: deja un `// TODO(OP-13, OP-14)` y no crees `AuthRepository` aún,
  porque depende de PEND-01 (Argon2).
- El estado de error no está en Figma. Muéstralo bajo el campo de contraseña, en color `error`, con el ícono de
  información: «Correo o contraseña incorrectos.» Es el mismo texto para los dos casos, para no revelar si el correo existe.
- Con el teclado abierto, el contenido se desplaza (`imePadding` + `verticalScroll`) y «Entrar» queda visible.
- Accesibilidad: el botón del ojo dice «Mostrar contraseña» u «Ocultar contraseña», con área de 48 dp; las etiquetas se asocian a sus campos.

## Terminado cuando
- `assembleDebug` y `testDebugUnitTest` pasan, con pruebas del `LoginViewModel` (validación y `canSubmit`).
- Hay `@Preview` vacía, llena, con contraseña visible, con error y en pantalla baja (360 × 740).
- Todos los textos están en `strings.xml` y los commits son atómicos.
- No tocaste `core/crypto`, `core/session` ni SQL. Si `SecureScreen` te hace dudar, márcalo para revisión humana.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha`, haz `git push` y detente.
