# Reporte · Tarea 04 · Ajustes de Crear cuenta y Verificar correo (SCR-06)

**Estado:** hecha
**Fecha:** 2026-10-16

## Qué se hizo
### Paso 1 · Ajustes de Crear cuenta
- **Documentos legales en hoja inferior:**
  - Se usa `LegalDocumentSheet` (un `ModalBottomSheet` casi a pantalla completa) sobre Crear cuenta, así que las contraseñas escritas se conservan.
  - El contenido se comparte con la pantalla mediante `LegalDocumentContent`.
  - `Routes.LEGAL_DOCUMENT` sigue existiendo para Ajustes.
  - La hoja lleva `securePolicy = SecureOn` de forma explícita: así no depende de heredar FLAG_SECURE.
- **Normalización (DEC-31):**
  - `MasterPasswordNormalizer` en dominio: NFKC con `java.text.Normalizer` y conteo con `codePointCount`.
  - El ViewModel normaliza una sola vez por cambio y por envío. Mide la longitud, compara y puntúa sobre el texto normalizado. Las copias `String` solo viven en variables locales y en el cálculo del puntaje (DEC-27).
- **Máximo de 128:**
  - Se cuenta en puntos de código **después** de NFKC (`MasterPasswordPolicy.MAX_LENGTH`). Se revisa antes de puntuar (zxcvbn ni siquiera corre), en `passesPolicy` y en `canSubmit`.
  - Cuando se pasa del máximo, el medidor dice «Usa como máximo 128 caracteres».
  - Los campos de Crear cuenta e Iniciar sesión tienen además un tope grueso (`InputTransformation.maxLength`) de 256 unidades UTF-16. Ver diferencias.

### Paso 2 · Verificar correo (SCR-06)
- **`CodeField`** en `core/designsystem/component`:
  - Un solo `BasicTextField` dibujado como 6 casillas de 48 × 60.
  - `CodeFieldFilter`: solo dígitos 0–9 y como máximo 6; al pegar «123 456» queda «123456».
  - Teclado `NumberPassword`, `ContentType.SmsOtpCode`, borde `error` en estado de error.
  - Un solo nodo de accesibilidad: «Código de verificación, 3 de 6 dígitos».
- **`VerifyEmailViewModel`:**
  - Toma el correo del argumento de la ruta.
  - La cuenta regresiva de 60 s usa una `TimeSource` inyectada (`core/di/TimeModule`); en las pruebas corre con el reloj virtual de `TestScope`.
  - «Verificar» se habilita con 6 dígitos. «Listo» también envía. No se envía solo al escribir el sexto dígito.
  - `Submit` → `// TODO(OP-12)`. Reenviar → `// TODO(OP-11 reenvío)`.
  - Errores mediante `onVerificationResult(EmailVerificationResult)` (dominio):
    - código incorrecto: «Código incorrecto. Te quedan N intentos.» y se vacían las casillas;
    - vencido o sin intentos: «Este código ya no sirve. Pide uno nuevo.», con «Reenviar código» de inmediato.
- **Navegación:**
  - `Submit` de Crear cuenta, con la política cumplida, manda el efecto `NavigateToVerifyEmail(email)`. Se navega a `Routes.VERIFY_EMAIL` con `popUpTo(SIGN_UP) { inclusive = true }` y el `// TODO(OP-11)` pedido.
  - Atrás regresa a Onboarding o Iniciar sesión.
- **Sin `SecureScreen` ni `ExcludeFromAutofill`** en Verificar, como pide la tarea. El código y el correo no salen en `toString()`.
- `NeumorphicDisc` ahora acepta tamaño, tamaño de ícono y color.

## Compilación y pruebas (salida real)
- `assembleDebug`: `BUILD SUCCESSFUL in 21s` · sin errores ni avisos de Kotlin.
- `assembleRelease`: `BUILD SUCCESSFUL in 6m 6s`.
- `testDebugUnitTest`: `BUILD SUCCESSFUL` · **91 pruebas, 0 fallas**. Nuevas o ampliadas:
  - `MasterPasswordNormalizerTest` 8: NFC = NFD, emoji = 1, 11 caracteres con uno compuesto no pasan, ancho completo → ASCII, «ﷺ» × 128 se pasa del máximo, 128 emojis caben y 129 no.
  - `CodeFieldFilterTest` 4: solo dígitos, máximo 6, pegar con espacios o guiones, sin dígitos de otros alfabetos.
  - `VerifyEmailViewModelTest` 15: habilitación, filtro, cuenta regresiva (60 → 45 → 0), reenviar durante y después de la espera, los dos errores, sin intentos = vencido, `toString` y formato m:ss.
  - `SignUpViewModelTest` 27: además de las anteriores, NFD contra NFC, longitud normalizada, el estimador recibe el texto normalizado, el máximo tras NFKC no mide ni deja crear la cuenta, y crear cuenta navega o no según la política.
- **Emulador (Pixel 7 Pro, API 36, solo `-s emulator-5554`):**
  - La hoja de Términos se abre sobre Crear cuenta y, al cerrarla, las dos contraseñas siguen escritas.
  - «Crear cuenta» lleva a Verificar correo con el correo correcto, y Verificar no tiene FLAG_SECURE.
  - Posiciones frente a Figma: título 289 dp (288) y casillas 453 (452).
  - La cuenta regresiva corre («0:56»). Escribir «48a1» deja «481» y se anuncia como «3 de 6 dígitos». Al agregar dígitos se corta en 6 y «Verificar» se habilita.
  - «Regresar» lleva a Iniciar sesión, el origen. Sin crashes.
  - Las correcciones de la revisión de seguridad (máximo tras NFKC, una sola normalización, `SecureOn` en la hoja) se aplicaron **después** de la prueba en el emulador. Las cubren las pruebas unitarias y las compilaciones, pero no las vi en el emulador.

## Revisión de seguridad del Paso 1 (`security-reviewer`)
Sin hallazgos críticos ni altos.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Media | El máximo de 128 se aplicaba antes de NFKC, que puede expandir un carácter en muchos («ﷺ» → 18): 128 unidades podían ser unos 2 300 caracteres para zxcvbn y Argon2. El dominio no tenía máximo | **Corregido:** `MAX_LENGTH` en puntos de código tras NFKC, revisado antes de puntuar, en `passesPolicy` y en `canSubmit`; aviso en el medidor; pruebas |
| 2 | Media | NFKC no quita caracteres invisibles ni de control (ancho cero, marcas bidi, selectores de variación, Cc). Cuentan para el mínimo y la fuerza sin verse, y un teclado distinto podría impedir el acceso para siempre | **No implementado:** cambia una decisión (ver dudas) |
| 3 | Baja | NFKC unifica símbolos que se ven distintos («２０２６» → «2026», «ﬁ» → «fi») | Sin cambios: el medidor ya mide el texto normalizado. Conviene una línea en DEC-31 |
| 4 | Baja | Con 128 unidades UTF-16, una contraseña solo de emojis se cortaba en 64 | **Corregido:** el tope del campo es de 256 unidades y el máximo real lo revisa el dominio |
| 5 | Baja | Se normalizaba 4 veces por envío | **Corregido:** una vez por envío |
| 6 | Info | Puntos de código sin asignar pueden cambiar con versiones futuras de Unicode | Entra en la decisión del punto 2 |
| 7 | Info | La longitud cuenta puntos de código, no grafemas (un emoji de familia son 7) | Solo documentar |
| 8 | Info | La hoja inferior no expone nada | **Reforzado:** `SecureFlagPolicy.SecureOn` explícito |
| 9 | Info | La comparación no es de tiempo constante | Sin riesgo (local, mismo usuario) |

## Diferencias con Figma o con la tarea
- **Disco de Verificar:** 104 dp con el sobre de 38 en `on-surface` (Figma, nodo 36:304), no 112 con ícono amarillo como en el onboarding.
- **Casilla activa de `CodeField`:** borde de **2** como en Figma (nodo 36:321); la tarea decía 1.5.
- **Tope del campo:** la tarea pedía `InputTransformation.maxLength(128)`. Lo dejé en 256 unidades UTF-16 y el máximo de 128 lo revisa el dominio en puntos de código tras NFKC, que es como DEC-31 cuenta la longitud. Con 128 unidades, 128 emojis no cabrían y un texto que NFKC expande sí pasaría.
- **Errores de Verificar:** no están en Figma. Van bajo las casillas, en `error`, con el ícono de información. Con 1 intento dice «Te queda 1 intento.» (plural correcto).
- **Cursor de `CodeField`:** es fijo, sin parpadeo; así funciona también con «Quitar animaciones».

## Dudas o decisiones que tomé
- **Caracteres invisibles (hallazgo 2):** ¿se adopta una regla tipo PRECIS OpaqueString (RFC 8265) para la contraseña maestra? Sería:
  - rechazar controles (Cc);
  - rechazar o quitar los ignorables por defecto (Cf, selectores de variación);
  - opcionalmente, rechazar puntos de código sin asignar;
  - la misma función en registro, inicio de sesión, desbloqueo y cambio de contraseña.

  No la implementé porque no está en el grafo (INV-19). Si se aprueba, va en DEC-31 y en `MasterPasswordNormalizer`.
- **Iniciar sesión** solo tiene el tope del campo. La normalización se aplicará al derivar en OP-13/OP-14 (quedó anotado en su `TODO`).
- **Commits:** como acordamos, no hice commits. Al final de esta sesión van los comandos. Son menos y más grandes que antes, porque varios archivos (por ejemplo `strings.xml` y `SignUpScreen.kt`) mezclan cambios de los dos pasos y no se pueden separar sin `git add -p`. Solo compilé el estado final, no cada commit intermedio.

## Revisión
- [x] REQUIERE REVISIÓN HUMANA: no se tocó `core/crypto`, `core/session` ni SQL, pero sí el manejo de la contraseña maestra (normalización y límite) en registro e inicio de sesión. El Paso 1 ya pasó por `security-reviewer`.
