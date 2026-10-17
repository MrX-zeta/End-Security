# Tarea 06 · Regla de invisibles ampliada, Desbloquear (SCR-09) y Bóveda · cargando (SCR-39)

**Estado:** hecha
**Escrita:** 2026-10-17 · sesión de diseño

## Revisión de la tarea 05
Aprobada según el reporte. **No pude ver el código:** los commits de esta tarea todavía no están en GitHub (lo último es
`fb83f50`). Pídele a la persona que los suba. Aprobado:
- el `OutputTransformation` para el guion;
- `KeyboardType.Password` para el código de respaldo;
- `PasteOnlyTextToolbar`;
- el cambio de los caracteres invisibles de las pruebas por escapes `\uXXXX`. Buen hallazgo.

**Decisión sobre los hallazgos 2, 3 y 5** (grafo v1.3.6, DEC-31), antes de que exista la primera cuenta real:
1. **Normalización (siempre, también al entrar y al desbloquear):** NFKC y después **se quitan U+FE0E y U+FE0F**. Así «❤» y «❤️» dan la misma llave. Ya no hace falta la excepción de U+FE0F.
2. **Al fijar una contraseña nueva se rechazan:**
   - controles (Cc);
   - **surrogates solitarios (Cs)**;
   - **todo `Default_Ignorable_Code_Point`** salvo U+200C, U+200D y las etiquetas U+E0020–E007F. Esto ya incluye el guion suave, los rellenos hangul, los selectores de variación restantes, etc.;
   - los **puntos de código que no estaban asignados en Unicode 10.0**. Mídelo con `android.icu.lang.UCharacter.getAge(cp)` (disponible desde API 24) y no con la versión del teléfono.

   Como `UCharacter` es de Android, pon esa consulta detrás de una interfaz de dominio (por ejemplo `UnicodeCharacterData`), con implementación en `data` y una falsa en las pruebas. Lo mismo para `Default_Ignorable_Code_Point` (`UCharacter.hasBinaryProperty(cp, UProperty.DEFAULT_IGNORABLE_CODE_POINT)`).
3. **UTF-8 con `CodingErrorAction.REPORT`.** Si falla al entrar, es un fallo genérico. Esto se aplica en OP-11/OP-13; por ahora déjalo anotado en esos `TODO`.
4. **U+2028/2029 y U+2800:** no son ignorables por defecto. Recházalos también, en una lista explícita aparte.

**Archivos de draw.io:** agrega a `.gitignore` las líneas `*.bkp` y `.$*`. El `.drawio` sí se sube; es el diagrama de la base de datos del equipo.

## Paso 1 · Regla ampliada
- Actualiza `MasterPasswordNormalizer` (quitar U+FE0E/FE0F) y `hasForbiddenCharacters` según lo de arriba.
- Pruebas:
  - «❤» y «❤️» normalizan igual;
  - U+00AD, U+115F, U+3164, U+FE00, U+2028, U+2800 y un surrogate solitario se rechazan;
  - un carácter asignado después de Unicode 10.0 (por ejemplo U+1FA70 🩰, Unicode 12) se rechaza con la implementación falsa que reporta su edad;
  - 🇲🇽, que no usa etiquetas, y 🏴󠁧󠁢󠁳󠁣󠁴󠁿 (bandera con etiquetas) se aceptan;
  - 👨‍👩‍👧 se sigue aceptando.

## Paso 2 · Desbloquear (SCR-09)
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=36-491 (tema claro, sin flecha de atrás).
- De arriba hacia abajo:
  - barra con «End Security»;
  - manchas como en Iniciar sesión;
  - «Tu bóveda» / «está bloqueada», con el texto «Escribe tu contraseña maestra para abrirla.»;
  - `PasswordField` «Contraseña maestra»;
  - una fila con el **botón de huella** (cuadrado de 56, contorno, ícono de huella) y `PrimaryButton` «Desbloquear»;
  - abajo, `PromptLink` «¿No eres {nombre}? **Cerrar sesión**».
- El botón de huella solo aparece si `biometric_enabled` es verdadero en DataStore (KV-DATASTORE). Si no aparece, «Desbloquear» ocupa todo el ancho. Al tocarlo: `// TODO(OP-20)` (BiometricPrompt + CryptoObject, DEC-09). No implementes biometría todavía.
- «Desbloquear» se habilita con la contraseña no vacía y lleva `// TODO(OP-19)`. El error es «Contraseña incorrecta.» Aquí sí se dice «contraseña» porque el usuario ya está identificado. Se aplica el mismo bloqueo por intentos que en Iniciar sesión.
- **«Cerrar sesión»** abre una confirmación en hoja inferior: «¿Cerrar sesión?» / «Para volver a entrar necesitarás tu correo, tu contraseña maestra y tu código de 2 pasos.» / «Cerrar sesión» (`error`) y «Cancelar». Al confirmar: `// TODO(OP-18)` y navega a Iniciar sesión vaciando la pila.
- El nombre viene de un `TODO` (cuenta local, TBL-L-ACCOUNT); en las vistas previas, «Ana».
- Seguridad: lo mismo que en Iniciar sesión (`SecureScreen`, `ExcludeFromAutofill`, `TextFieldState`, vaciar en `ON_STOP`, normalización sin rechazo, máximo 256 en el campo).
- **Atrás del sistema en Desbloquear** sale de la app (`moveTaskToBack`). Nunca debe regresar a una pantalla con datos de la bóveda.
- Ruta `Routes.UNLOCK`. Por ahora se llega solo desde las vistas previas y una ruta de depuración; `LockGuard` llega con la tarea de `VaultSession`.

## Paso 3 · Bóveda · cargando (SCR-39) y barra inferior
**Figma:** https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=69-3207
- **Componente nuevo `BottomNavBar`** (DEC-56): Bóveda, Salud, Generador y Ajustes. La pestaña activa lleva el ícono en un cuadro `on-surface` con el ícono claro.
  - Íconos: candado, escudo, **dado** (no destellos, DEC-59) y engrane, de la fila `35:14`.
  - Etiquetas siempre visibles y área de 48 dp.
- **Componente nuevo `SkeletonBlock`** con pulso suave de opacidad. Con «Quitar animaciones» queda fijo.
- Pantalla `VaultLoadingScreen`:
  - título «Tu bóveda»;
  - punto `primary` con «Descargando tu bóveda…»;
  - el buscador y 5 filas como esqueletos;
  - `BottomNavBar` con Bóveda activa.
  - El candado de arriba a la derecha queda deshabilitado mientras carga.
- 2FA «Continuar» y Código de respaldo «Entrar» navegan aquí (temporal, con sus `TODO`). Al terminar (`// TODO(OP-32)`), irá a la Bóveda (SCR-10). Por ahora crea un marcador `Routes.VAULT` y pasa a él tras 2 s, **solo en compilaciones de depuración**.
- Accesibilidad: los esqueletos se ocultan a TalkBack, y la pantalla anuncia «Descargando tu bóveda» una sola vez.

## Terminado cuando
- Hay pruebas del Paso 1, del ViewModel de Desbloquear (habilitación, error, bloqueo y visibilidad de la huella) y de que la pestaña activa de `BottomNavBar` se reporta como seleccionada, y pasan.
- Hay `@Preview` de Desbloquear (con y sin huella, error, bloqueo y hoja de cerrar sesión), de Bóveda · cargando y de `BottomNavBar` con cada pestaña activa.
- `assembleDebug`, `testDebugUnitTest` y `assembleRelease` pasan.
- `security-reviewer` revisa el Paso 1 y Desbloquear.
- **Commits hechos y subidos**, para que pueda revisar el código de las tareas 05 y 06.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
