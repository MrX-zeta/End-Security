# Reporte · Tarea 06 · Regla de invisibles ampliada, Desbloquear (SCR-09) y Bóveda · cargando (SCR-39)

**Estado:** hecha
**Fecha:** 2026-10-17

## Qué se hizo
- **Commits de la tarea 05:** ya estaban en GitHub cuando empecé (`d678c22` y `4d5cecb` en `origin/development`).
- **`.gitignore`:** agregué `*.bkp` y `.$*`. El `.drawio` se sigue subiendo.

### Paso 1 · Regla ampliada (DEC-31 v1.3.6)
- **`MasterPasswordNormalizer`:** aplica NFKC y después quita U+FE0E y U+FE0F. Así «❤» y «❤️» dan lo mismo.
- **`MasterPasswordPolicy.hasForbiddenCharacters(raw, normalized, unicode)`:** sobre el texto normalizado rechaza:
  - Cc y Cs;
  - todo `Default_Ignorable_Code_Point`, salvo U+200C, U+200D y U+E0020–E007F;
  - U+2028, U+2029 y U+2800, en una lista aparte;
  - lo que no estaba asignado en Unicode 10.0.
- **Edad también en el texto crudo** (hallazgo 1 de la revisión): la edad Unicode se revisa además en lo que escribió el usuario, antes de NFKC.
- **Interfaz de dominio `UnicodeCharacterData`** (`isDefaultIgnorable` y `age`), con dos implementaciones:
  - `IcuUnicodeCharacterData` en `data`, con `UCharacter.hasBinaryProperty` y `UCharacter.getAge`;
  - `FakeUnicodeCharacterData` en las pruebas, con la tabla de DerivedCoreProperties.
- **Crear cuenta:** `SignUpViewModel` la recibe por Hilt.
- **UTF-8 con `CodingErrorAction.REPORT`:** queda anotado en los `TODO` de OP-11 (registro), OP-13 (entrar) y OP-19 (desbloquear).
- **Escapes en las pruebas:** todos los caracteres invisibles se arman con `cp(0x…)`; no hay ningún `\u` en el código nuevo. Revisé con un script que no quedaran caracteres invisibles literales en los archivos tocados: 0.

### Paso 2 · Desbloquear (SCR-09, `Routes.UNLOCK`)
- **De arriba abajo:** «End Security», manchas como en Iniciar sesión, «Tu bóveda / está bloqueada», texto, `PasswordField` «Contraseña maestra», fila con huella (56, contorno) y «Desbloquear», y abajo «¿No eres Ana? **Cerrar sesión**».
- **Huella:** solo se muestra con `biometric_enabled`. Es una clave nueva en `SettingsStore` y se lee con `UnlockSettingsRepository`. Sin huella, «Desbloquear» ocupa todo el ancho. Al tocarla: `TODO(OP-20)`.
- **«Desbloquear»:**
  - se habilita con la contraseña escrita (`TODO(OP-19)`);
  - con contraseña incorrecta muestra «Contraseña incorrecta.» y vacía el campo; el error se quita al volver a escribir;
  - tiene el mismo bloqueo por intentos que Iniciar sesión.
- **«Cerrar sesión»:** abre una hoja inferior con «¿Cerrar sesión?», el texto, «Cerrar sesión» (`DangerButton`) y «Cancelar» (`SecondaryButton`). Al confirmar: `TODO(OP-18)` y va a Iniciar sesión con la pila vacía.
- **Seguridad:**
  - `SecureScreen`, `ExcludeFromAutofill` y `TextFieldState` en memoria;
  - se vacía en `ON_STOP` y al salir, máximo de 256 en el campo, la hoja con `SecureOn` y nada en `toString()`.
- **Atrás del sistema:** `moveTaskToBack(true)`.
- **Ruta de depuración:** solo en compilaciones de depuración, con `adb shell am start -n com.luis.end_security/.MainActivity --es debug_route unlock`. Solo acepta `unlock`.
- **Nombre:** sale de un `TODO(TBL-L-ACCOUNT)`. En las vistas previas es «Ana».

### Paso 3 · Bóveda · cargando (SCR-39) y barra inferior
- **`BottomNavBar` (DEC-56):**
  - Bóveda, Salud, Generador y Ajustes, con los íconos vault, shield-check, dado y settings de Figma, en 22 px;
  - la pestaña activa lleva un cuadro `on-surface` de 44×36 con el ícono claro y la etiqueta en Bold;
  - cada pestaña mide 76×54, tiene `Role.Tab` y `selectableGroup`;
  - el margen inferior es la barra de gestos o 14 dp, lo que sea mayor.
- **`SkeletonBlock` y `SkeletonPulse`:**
  - `outline` al 20 % con un pulso de opacidad del 100 al 50 % (900 ms), compartido por todos los bloques de la pantalla;
  - con «Quitar animaciones» queda fijo;
  - oculto a TalkBack (`clearAndSetSemantics`).
- **`VaultLoadingScreen`:**
  - esqueleto de la inicial, candado «Bloquear ahora» deshabilitado, «Tu bóveda», punto `primary` con «Descargando tu bóveda…», buscador y 5 filas en esqueleto, y la barra con Bóveda activa;
  - TalkBack anuncia «Descargando tu bóveda» una vez, como `paneTitle`.
- **Navegación:**
  - «Continuar» de 2FA y «Entrar» del código de respaldo navegan aquí con la pila vacía, de forma temporal y con sus `TODO(OP-15/16)`;
  - en depuración pasa a `Routes.VAULT` (marcador) tras 2 s (`TODO(OP-32)`).
- **Componentes nuevos:**
  - `OutlinedIconButton` («Botón de ícono / Contorno»);
  - `SecondaryButton` y `DangerButton` («Botón/Secundario» y «Botón/Peligro», en `BorderedButtons.kt`);
  - estilos `headlineMedium` (30 Bold) y `labelSmall` (11/14 Medium).

## Compilación y pruebas (salida real)
- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` y `assembleRelease`: `BUILD SUCCESSFUL in 1m 48s`.
- **Pruebas unitarias: 143, 0 fallas.** Nuevas o ampliadas:
  - **`ForbiddenCharactersTest` (17):**
    - se rechazan U+00AD, U+115F, U+3164 y U+FE00; U+2028, U+2029 y U+2800; surrogates D800 y DC00; U+1FA70 (12.0, con el falso); U+1FBF0 (13.0, que NFKC vuelve «0»); U+E0001; U+034F y U+061C;
    - se aceptan 🇲🇽, 🏴󠁧󠁢󠁳󠁣󠁴󠁿, 👨‍👩‍👧, ❤️ y U+200C.
  - **`MasterPasswordNormalizerTest` (9):** «❤», «❤» + FE0F y «❤» + FE0E normalizan igual.
  - **`UnlockViewModelTest` (8):** habilitación, error y su limpieza, bloqueo con cuenta regresiva, huella según `biometric_enabled`, hoja, confirmación hacia Iniciar sesión y `toString` sin nombre.
  - **`TwoFactorViewModelTest` (7) y `BackupCodeViewModelTest` (6):** navegan a la bóveda.
- **Pruebas instrumentadas** (emulador, `am instrument`): **6, OK.**
  - `BottomNavBarTest`: con cada pestaña activa, esa es `Role.Tab` seleccionada y las demás no; además revisa el toque.
  - `IcuUnicodeCharacterDataTest`: con el ICU real, 🧡 = 10.0, U+0378 sin edad, e ignorables (U+180E, U+FFA0, U+E0001, U+E0100…).
- **Release:** `debug_route` no aparece en el dex de release y `mapping.txt` existe, así que R8 quitó la ruta de depuración.
- **Emulador** (Pixel 7 Pro, API 36, solo `emulator-5554`):
  - Desbloquear por la ruta de depuración: ventana con FLAG_SECURE, título a 317 dp (Figma 316), «Desbloquear» a todo el ancho sin huella, y se habilita al escribir.
  - «Cerrar sesión» abre la hoja, «Cancelar» la cierra y confirmar lleva a Iniciar sesión.
  - Atrás en Desbloquear manda la app al fondo.
  - Onboarding → Omitir → Iniciar sesión → 2FA → 6 dígitos → «Continuar» → Bóveda · cargando, igual a Figma (captura) → a los 2 s, el marcador. Atrás ahí sale de la app: la pila quedó vacía.

## Revisión de seguridad (`security-reviewer`, Paso 1 y Desbloquear)
Sin hallazgos críticos ni altos.

| # | Gravedad | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | Media | Un carácter posterior a Unicode 10.0 que NFKC vuelve antiguo (U+1FBF0 → «0», U+1CCF0…, U+10780…) pasaba, porque la edad se medía después de normalizar; en un teléfono viejo la llave saldría distinta | **Corregido:** la edad también se revisa en el texto crudo, con prueba |
| 2 | Media | Compose pone el texto real del campo de contraseña en el nodo de accesibilidad (`password=true`). En API 28–33 un servicio de accesibilidad activo podría leerlo; en 34+ lo frena `ACCESSIBILITY_DATA_SENSITIVE_YES`. Lo vi con `uiautomator dump` (que es una herramienta privilegiada) | **No implementado:** afecta a `PasswordField` desde la tarea 02 y cambia DEC-26 (ver dudas) |
| 3 | Baja | U+200C, U+200D y las etiquetas se aceptan en cualquier posición (sueltas, repetidas, al final) | **No implementado:** pide reglas de contexto (RFC 5892) que DEC-31 no tiene |
| 4 | Baja | Pasan Cf no ignorables (U+FFF9–FFFB), los no-caracteres (U+FDD0–FDEF, U+FFFE…, que sí tienen edad) y el uso privado | **No implementado:** amplía DEC-31 |
| 5 | Baja | Quitar FE0E/FE0F después de NFKC no es idempotente: «A»+FE0F+U+030A da «A»+U+030A y no «Å» | **No implementado:** DEC-31 dice «NFKC y después se quitan»; propongo quitarlos antes (da lo mismo en todo lo demás) |
| 6 | Baja | El bloqueo de Desbloquear vive en la memoria del ViewModel: cerrar la app lo reinicia | **No implementado:** OP-19 es local y el grafo no dice dónde se cuentan los intentos |
| 7 | Baja | La ruta de depuración aceptaba `vault` y `vault_loading` (la actividad es exported) | **Corregido:** solo `unlock`. En release no existe (R8, comprobado) |
| 8 | Baja | Los emojis nuevos (🥰 de Unicode 11, 🫶 de 14) se rechazan con «caracteres invisibles» | **No implementado:** el texto lo fija DEC-31 |
| 9 | Info | Las pruebas no tocaban el ICU real | **Corregido:** `IcuUnicodeCharacterDataTest` instrumentada, más casos de U+E0001 y U+1FBF0 |
| 10 | Info | Cuando llegue `LockGuard`, debe navegar con la pila limpia y bloquear `VaultSession`, no apilar Desbloquear | Anotado para la tarea de `VaultSession` |
| 11 | Info | NFKC no unifica ي/ی ni ك/ک (persa con teclado árabe) | Anotado como riesgo conocido de DEC-31 |

## Diferencias con Figma o con la tarea
- **Candado de Bóveda · cargando:** en Figma es un esqueleto. Como pide la tarea, es el botón real deshabilitado (contorno al 40 %).
- **Contraseña incorrecta en Desbloquear vacía el campo**, como el código de respaldo. Así el error se quita al volver a escribir y la contraseña rechazada no se queda en pantalla. Iniciar sesión no lo hace.
- **Sin nombre de la cuenta**, el enlace dice «¿No eres tú?». Es un texto nuevo: hasta que exista TBL-L-ACCOUNT, la app real no tiene el nombre.
- **Hoja «¿Cerrar sesión?»:** no está en Figma. Usa los botones de peligro y secundario de la biblioteca, sobre `background`, como la hoja de documentos legales.
- **Marcador de `Routes.VAULT`:** «Tu bóveda», «Próximamente» y la barra inferior.
- **Las pestañas de la barra aún no navegan** (`TODO(DEC-56)`), porque Salud, Generador y Ajustes no existen.
- **Activé `buildConfig = true`** en `app/build.gradle.kts` para usar `BuildConfig.DEBUG`, así R8 quita el código de depuración.

## Dudas o decisiones que tomé
- **Accesibilidad y contraseña (hallazgo 2):** ¿se verifica con un servicio de prueba en API 33 y, si se confirma, se cambia `PasswordField` para que el nodo solo exponga la máscara? Cambia DEC-26.
- **Ampliar DEC-31 (hallazgos 3, 4, 5 y 8):**
  - contexto para ZWJ, ZWNJ y etiquetas;
  - rechazar Cf, Co y Cn;
  - quitar FE0E/FE0F **antes** de NFKC;
  - y un mensaje que mencione emojis demasiado nuevos.

  Conviene decidir antes de la primera cuenta real, igual que lo anterior.
- **Bloqueo local en Desbloquear (hallazgo 6):** ¿cuántos intentos, cuánto tiempo y dónde se guardan (DataStore con reloj de pared y `elapsedRealtime`)? Hace falta antes de OP-19.
- **Emulador:**
  - Está al 94 %. `connectedDebugAndroidTest` no pudo instalar los dos APK juntos, así que instalé y corrí `am instrument` a mano y desinstalé el APK de prueba al terminar. No toqué las otras apps del emulador.
  - Cambié por error el tiempo de apagado de pantalla del emulador (`screen_off_timeout` a 10 min) con `settings put`. No sé cuál era el valor anterior; si importa, se restaura en Ajustes → Pantalla.

## Revisión
- [x] REQUIERE REVISIÓN HUMANA: no se tocó `core/crypto`, `core/session` ni SQL. Sí se tocaron:
  - la política y la normalización de la contraseña maestra (cambian la llave derivada);
  - la pantalla de Desbloquear (un secreto);
  - la navegación de la sesión (pila limpia, ruta de depuración).

  El Paso 1 y Desbloquear pasaron por `security-reviewer`.
