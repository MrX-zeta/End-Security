# Tarea 03 · Crear cuenta (SCR-05)

**Estado:** pendiente
**Escrita:** 2026-10-16 · sesión de diseño

## Revisión de la tarea 02
Aprobada. La revisión de seguridad y sus correcciones (`SharedRequest`, exclusión sobre la raíz, datos sensibles para
accesibilidad y vaciado en `ON_STOP`) son correctas, y la prueba en el emulador con el autocompletado de Google
convence. Tus decisiones quedan así (grafo v1.3.3):
- Radio 16 y una sola sombra en los campos: tenías razón, mi dato estaba mal.
- `displaySmall` 34/42 y la corrección del interlineado: bien.
- **Grano:** en tema claro va al **12 %** (Figma manda); en oscuro sigue al 7 %. Actualiza el token.
- Mancha amarilla pequeña: es una excepción decorativa, ya registrada en DEC-50.
- Botón deshabilitado al 40 %: queda como regla en DEC-50.
- Sin autocompletado en toda la pantalla, también para el correo: se acepta (DEC-26).
- Riesgo de accesibilidad en Android 9 a 13 y memoria de `TextFieldState`: aceptados y registrados en DEC-26 y DEC-27.
- El mensaje del commit `b8739a2` está bien así.

## Objetivo
Pantalla Crear cuenta, solo la interfaz. El envío real (OP-11) espera a PEND-01 (Argon2), igual que el inicio de sesión.

**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=36-119 (tema claro).
De arriba hacia abajo:
- Botón de atrás (barra superior, nodo `36:144`).
- `TwoWeightTitle` «Crea tu cuenta» / «en un minuto», con el texto «Solo necesitas tu correo y una contraseña maestra.»
- Campos «Correo», «Contraseña maestra» (con ojo) y «Confirma tu contraseña maestra». Reutiliza `LabeledTextField` y `PasswordField`.
- Medidor de fuerza entre la contraseña y la confirmación (nodo `36:221`).
- «Aviso importante»: tarjeta `primary` con ícono de información y el texto «Es la única llave de tu bóveda. Si la olvidas, no podremos recuperarla.»
- **Nuevo (PEND-12):** la casilla «Acepto los **Términos** y el **Aviso de privacidad**» (nodo «Casilla · Términos» dentro de `36:158`). Mide 24 × 24, tiene radio 7 y relleno `primary` con la palomita `on-primary` cuando está marcada; sin marcar, solo el borde `outline`. El área táctil es de 48 dp.
- `PrimaryButton` «Crear cuenta», pegado abajo.

## Política de contraseña maestra (DEC-31, PEND-03 resuelto)
- Mínimo **12 caracteres** y puntaje **zxcvbn ≥ 3**.
- Fuerza con **zxcvbn4j** (TEC-ZXCVBN), en el dispositivo y sin red. Antes de agregarla, verifica que sea compatible con
  AGP 9 y R8, y agrégala a `libs.versions.toml`. Si no lo es, detente y repórtalo; no la sustituyas por una heurística propia.
- Medidor de 4 barras:
  - puntaje 0–1: «Débil», 1 barra `error`;
  - puntaje 2: «Regular», 2 barras `on-surface-variant`;
  - puntaje 3: «Fuerte», 3 barras `success`;
  - puntaje 4: «Muy fuerte», 4 barras `success`.
  Las barras vacías van en `outline` al 30 %. Con el campo vacío no se muestra la etiqueta.
- Si la contraseña tiene menos de 12 caracteres, en el lugar de la etiqueta va «Usa al menos 12 caracteres».
- La confirmación, si no coincide, muestra bajo su campo en `error`: «Las contraseñas no coinciden.» Solo se muestra cuando el campo pierde el foco o al tocar «Crear cuenta», no mientras se escribe.
- «Crear cuenta» se habilita solo con un correo válido, la política cumplida, la confirmación igual y la casilla marcada.

## Seguridad (las mismas reglas que en Iniciar sesión)
- Los dos campos de contraseña usan `remember { TextFieldState() }`, `SecureScreen()` y `ExcludeFromAutofill()`, y se vacían en `ON_STOP` y al salir.
- **Ningún texto de contraseña entra al `UiState` ni a un `StateFlow`.**
  - El estimador es una interfaz de dominio (`PasswordStrengthEstimator`, Kotlin puro) que recibe `CharSequence` y devuelve el puntaje. La implementación con zxcvbn4j va en `data`.
  - El ViewModel solo guarda `strengthScore`, `meetsLength` y `confirmationMatches`, y los recalcula en `snapshotFlow` con `debounce` de unos 150 ms.
  - La comparación de las dos contraseñas se hace con `contentEquals` sobre los `CharSequence`, sin crear `String`.
- Nunca registres en logs el puntaje junto con la longitud. El puntaje solo vive en memoria.

## Comportamiento
- **Términos** y **Aviso de privacidad** abren una pantalla `Routes.LEGAL_DOCUMENT` con un marcador («Documento en redacción»), porque los textos de PEND-12 aún no existen. La casilla no se marca sola al volver.
- `Submit` → `// TODO(OP-11)`. No crees `AuthRepository`.
- Atrás regresa a la pantalla de origen: Onboarding o Iniciar sesión.
- Con el teclado abierto: `imePadding` + `verticalScroll`; el botón se queda abajo cuando cabe y se desplaza con el contenido cuando no.

## Terminado cuando
- Hay pruebas del ViewModel (habilitación del botón y momento de mostrar «no coinciden») y del mapeo puntaje → etiqueta y color, y pasan.
- Hay `@Preview` vacía, débil, regular, fuerte con todo válido, con «no coinciden» y en pantalla baja (360 × 740).
- `assembleDebug`, `testDebugUnitTest` y `assembleRelease` pasan (este último porque se agrega una librería y debe sobrevivir a R8).
- Pasa por `security-reviewer` antes de cerrar.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha`, haz `git push` (o déjalo listo si el push lo hace la persona) y detente.
