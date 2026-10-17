# Tarea 04 · Ajustes de Crear cuenta y Verificar correo (SCR-06)

**Estado:** hecha
**Escrita:** 2026-10-16 · sesión de diseño

## Revisión de la tarea 03
Aprobada. Fue muy buena la detección de que `Strength.wipe()` borraba la entrada del llamador; también la corrección
para que `Submit` verifique la política sobre el mismo texto. Respuestas a tus dudas (grafo v1.3.4):
- **Documentos legales:** desde Crear cuenta se abren en una **hoja inferior** (`ModalBottomSheet` a pantalla casi completa)
  sobre la misma pantalla, así que las contraseñas escritas se conservan. Desde Ajustes (más adelante) serán una pantalla.
  Reutiliza el mismo contenido en los dos casos.
- **Máximo 128 caracteres** en los campos de contraseña maestra. Queda en DEC-31.
- **Normalización:** **NFKC** (NIST SP 800-63B §5.1.1.2) antes de medir, comparar o derivar, y **UTF-8** como entrada de
  Argon2. La longitud se cuenta en **puntos de código** después de normalizar. Queda en DEC-31.
- **Prueba instrumentada de zxcvbn bajo R8:** no por ahora; la prueba manual basta.
- `LegalDocument` en `feature/settings/ui/legal`: bien.
- **Push:** ya veo tus commits en GitHub, así que la persona del equipo sí los está subiendo.

## Paso 1 · Ajustes de Crear cuenta
1. Términos y Aviso de privacidad en `ModalBottomSheet` (con `SecureScreen` si muestra algo de la pantalla de abajo; si no,
   no hace falta). Ya no navegan a `Routes.LEGAL_DOCUMENT` desde Crear cuenta; deja la ruta para Ajustes.
2. Límite de 128 en los dos campos de contraseña maestra (`InputTransformation.maxLength(128)` o equivalente) y en Iniciar sesión.
3. `MasterPasswordNormalizer` en dominio: NFKC con `java.text.Normalizer` y conteo con `codePointCount`.
   - `MasterPasswordPolicy` y el estimador trabajan sobre el texto normalizado.
   - `Normalizer` devuelve `String`; es la limitación aceptada en DEC-27. Haz la copia lo más corta posible.
   - Agrega pruebas: un texto en NFC y su equivalente en NFD dan el mismo resultado, un emoji cuenta como 1 y un texto de 11 caracteres con un carácter compuesto no pasa.

## Paso 2 · Verificar correo (SCR-06)
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=36-268 (tema claro).
De arriba hacia abajo:
- `BackTopBar`.
- `NeumorphicDisc` con el ícono de sobre, versión clara: fondo `background` y sombras del tema claro.
- `TwoWeightTitle` «Revisa» / «tu correo».
- El texto «Te enviamos un código de 6 dígitos a» seguido del correo.
- Seis casillas del código.
- La línea con ícono de reloj «Puedes pedir otro código en 0:45».
- `PrimaryButton` «Verificar», pegado abajo.

**Componente nuevo `CodeField`** en `core/designsystem/component`. Se reutiliza en 2FA (SCR-07) y en Desbloquear.
- Es un solo campo de texto oculto (no seis) dibujado como 6 casillas. La casilla activa lleva un borde `on-surface` de 1.5 y un cursor.
- Solo admite dígitos (`KeyboardType.NumberPassword`) y un máximo de 6. Al pegar, se aceptan 6 dígitos de una vez.
- Usa `ContentType.SmsOtpCode` para que el teclado sugiera el código si lo detecta.
- En estado de error, las casillas llevan borde `error`.
- Accesibilidad: un solo nodo, «Código de verificación, 3 de 6 dígitos». No anuncia cada casilla.

**Comportamiento:**
- **Navegación:**
  - En Crear cuenta, `Submit` con la política cumplida navega a `Routes.VERIFY_EMAIL` con el correo como argumento y saca Crear cuenta de la pila (`popUpTo(SIGN_UP) { inclusive = true }`).
  - Atrás regresa a donde estaba antes de Crear cuenta: Onboarding o Iniciar sesión.
  - Marca la navegación con `// TODO(OP-11): navegar solo si el registro responde bien`.
- **Reenvío:** la espera es de 60 s. Mientras dura se muestra «Puedes pedir otro código en m:ss»; al llegar a 0, se muestra `TextLink` «Reenviar código», que reinicia la espera y lleva `// TODO(OP-11 reenvío)`. La cuenta regresiva vive en el ViewModel con una fuente de tiempo inyectada, para probarla con `TestScope` sin esperas reales.
- **Verificar:** se habilita con 6 dígitos. «Listo» en el teclado también envía. No se envía solo al escribir el sexto dígito. `Submit` → `// TODO(OP-12)`.
- **Estados de error** (en Figma no existen; van bajo las casillas, en `error`, con el ícono de información):
  - código incorrecto: «Código incorrecto. Te quedan N intentos.»; el máximo es 5 (`email_verifications.attempts`). Las casillas se vacían;
  - código vencido o sin intentos: «Este código ya no sirve. Pide uno nuevo.» Además se oculta la cuenta regresiva y se muestra «Reenviar código» de inmediato.
- **Sin `SecureScreen` ni `ExcludeFromAutofill`:** el código es de un solo uso y el servidor limita los intentos. Aun así,
  el código no va en `toString()` ni en logs.

## Terminado cuando
- Hay pruebas de `MasterPasswordNormalizer`, del ViewModel de Verificar (habilitación, cuenta regresiva, reenvío y los dos errores) y del filtro de `CodeField` (solo dígitos, máximo 6, pegar), y pasan.
- Hay `@Preview` de Verificar vacía, a medias, completa, con cada error, con «Reenviar código» y en pantalla baja. También de la hoja legal sobre Crear cuenta.
- `assembleDebug`, `testDebugUnitTest` y `assembleRelease` pasan.
- `security-reviewer` revisa solo el Paso 1 (normalización y límite).

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
