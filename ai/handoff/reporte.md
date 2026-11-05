# Reporte · Tarea 22 · Legal y ayuda (PEND-12; DEC-64; contrato 1.4.0)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** el registro (`RegisterRequest`, `AuthRepository.register`, `AuthRepositoryImpl`, `AuthApiImpl`) ahora lleva `marketingOptIn` hasta el servidor, y `AccountApi` ganó `updateMarketingOptIn` (PATCH). No toca crypto, sesión ni SQL, pero es autenticación y consentimiento (DEC-64): conviene pasarlo por `security-reviewer`. El **Paso 0** (`SecureScreen` en el código de cambiar correo) también queda para revisión.

El `docs:` de esta tarea (textos legales, contrato 1.4.0, alcance del backend, grafo v1.7.0) ya se había subido antes (`07684af`).

## Qué se hizo

### Paso 0 · `SecureScreen` en el código de cambiar correo
`EmailCodeScreen` (paso del código de `ChangeEmailScreen.kt`) llama a `SecureScreen()`: el código de un solo uso y el correo nuevo ya no salen en capturas ni en recientes. El paso del correo no lo lleva (no hay secretos; la contraseña solo vive en la hoja de reautenticación, que ya oculta su contenido).

### Paso 1 · Documentos legales (`feature/settings/ui/legal/`)
- **Gradle `copyLegalDocs`** (en `app/build.gradle.kts`, ligada a `preBuild`) copia `docs/legal/*.md` a `assets/legal/`; la copia está en `.gitignore`, así que `docs/legal/` sigue siendo la única fuente.
- **`LegalMarkdown`** (renderizador mínimo, solo lo que usan los dos documentos): `#` título, línea de fecha, `##` secciones, línea completa en `**negritas**` como subtítulo, negritas en línea, viñetas, listas numeradas y **tablas → una tarjeta por fila**. Lo que no reconoce queda como párrafo con su texto (nada se pierde). `## Resumen` va aparte.
- **`LegalDocumentScreen` / `LegalDocumentSheet`** (mismo contenido): título, fecha, **tarjeta «Resumen» siempre abierta** (amarillo claro), «Abrir todo / Cerrar todo» y cada sección como **acordeón plegado**. Cada encabezado es un botón con `stateDescription` «Expandido / Contraído» para TalkBack. Las secciones abiertas sobreviven a girar la pantalla (son índices, nada sensible). Funciona **con y sin sesión** (la ruta no está tras `RequireUnlocked`).

### Paso 2 · Crear cuenta
- Los enlaces de «Términos» y «Aviso de privacidad» abren el documento en una hoja sobre el formulario: lo escrito no se pierde.
- Casilla nueva y **opcional**, sin marcar: «Quiero recibir novedades y encuestas de End Security (opcional)». No bloquea «Crear cuenta». `register` manda `marketingOptIn` (falso si no se marcó). Si el registro falla, la casilla conserva lo marcado.

### Paso 3 · Ajustes
- **Interruptor «Novedades y encuestas»** en la tarjeta Cuenta (después de «Actividad reciente»): toda la fila es un interruptor (`Role.Switch`). Valor: `getAccount().marketingOptIn`; al tocarlo, `PATCH /account { marketingOptIn }` con el cambio visible al instante. **Si falla (sin red o error), vuelve a su valor y avisa con el mensaje genérico** (`auth_error_generic`). Sin valor conocido se ve apagado y **deshabilitado**; mientras guarda no se puede volver a tocar. Se recarga al entrar y al volver a la pantalla.
- **Grupo «Legal y ayuda»** con `ListCard` / `ListRowIcon` e íconos que ya existían: Términos y condiciones, Aviso de privacidad, Licencias, Acerca de.
- **Licencias:** lista fija en `Licenses.kt` (AndroidX/Compose, Material 3, Hilt/Dagger, Kotlin/kotlinx, zxcvbn, Bouncy Castle, Montserrat y Pwned Passwords) y, al tocar una fila, hoja con el texto completo desde `assets/licenses/` (`apache-2.0.txt`, `mit.txt`, `cc-by-4.0.txt` nuevos; `montserrat/OFL.txt` ya estaba).
- **Acerca de:** nombre, `BuildConfig.VERSION_NAME`, línea académica, crédito de HIBP (CC BY 4.0), **«Reportar un problema de seguridad»** (`ACTION_SENDTO`, destinatario `LegalInfo.PRIVACY_EMAIL`, asunto «Reporte de seguridad», sin cuerpo) y enlace al repositorio. Si no hay app que lo abra, `MessageBar` «No hay una app para abrirlo en este teléfono.».
- Rutas nuevas `LICENSES` y `ABOUT` en `AppNavHost`.
- **`LegalInfo`**: una sola constante `PRIVACY_EMAIL = "[privacidad@correo.com]"` (se dejó el marcador tal cual, igual que en los documentos).

### Contrato 1.4.0 (solo DTOs y backend falso)
- `RegisterRequestDto.marketingOptIn` (no se escribe si es falso), `AccountDto.marketingOptIn` (obligatorio, como en el contrato) y `UpdateMarketingRequestDto`.
- **Backend falso:** guarda `marketingOptIn` en el registro y lo devuelve en `Account`; `PATCH /account` acepta `displayName` y/o `marketingOptIn` (un cuerpo vacío o con campos desconocidos es 400; un campo ausente no se toca; `displayName: null` sí borra el nombre).

## Pruebas (salida real)
- **`testDebugUnitTest`: sin fallas.** Nuevas:
  - `LegalMarkdownTest` (18): cada elemento, negritas en línea (incluida la sin pareja), tabla → tarjetas, `## Resumen` aparte, saltos `\r\n` y los dos documentos reales de `docs/legal`.
  - `SettingsViewModelTest` (+7): valor del servidor, sin red (sin valor y tocarlo no hace nada), activar y desactivar, **fallo → vuelve a su valor y avisa**, segundo toque mientras guarda y recarga al volver.
  - `SignUpViewModelTest` (+4): casilla sin marcar y opcional, registro con `marketingOptIn` falso / verdadero y la casilla se conserva si falla.
  - `MarketingOptInAgainstFakeServerTest` (6, contra el backend falso): registro con y sin casilla, PATCH activa/retira, nombre y marketing independientes, los dos campos juntos y el 400 de cuerpo vacío o desconocido.
  - `DtoContractTest`: el ejemplo de la cuenta ahora trae `marketingOptIn` (lo exige el contrato 1.4.0) y una prueba nueva de lo que se serializa.
- **Compose** (en el emulador): `LegalScreensTest` (8: resumen abierto y secciones plegadas, abrir una sección, «Abrir todo / Cerrar todo», semántica Expandido/Contraído, los dos documentos reales sin sesión, Licencias con su hoja de texto, textos de licencia presentes en assets, Acerca de y el intent del correo), `SettingsScreenTest` (16; +6 del interruptor y de «Legal y ayuda»; la prueba del interruptor de huella ahora lo distingue del nuevo por su texto) y `FormErrorsScreenTest` (6; +2 de la casilla opcional). Las 30 de estos tres archivos pasaron en el emulador.
- `lintDebug` sin errores y `assembleRelease` compila.
- **`connectedDebugAndroidTest` completa, en dos tandas** (el emulador se cae en una sola corrida larga): **202 pruebas, 0 fallas** (5 omitidas, las de huella sin sensor), sin contar `ClipboardDeviceTest`.
  - Tanda 1 (`feature.account`, `feature.activity`, `feature.auth`, `navigation`): 107. Una prueba de `TwoFactorScreensTest` falló por tiempo de espera de `PixelCopy` al capturar una hoja y luego el emulador se cayó («System has crashed»); tras reiniciarlo la clase pasó completa (15/15).
  - Tanda 2 (`core`, `feature.health`, `feature.settings`, `feature.vault`): 95 sin `ClipboardDeviceTest`.
  - **`ClipboardDeviceTest`: 2 de 4 fallan** («expected end-security:abc but was host clipboard»): es **E3** (el emulador comparte el portapapeles con el equipo); sigue pendiente de `revisiones-pendientes.md`, no es de esta tarea. No corrí `ExampleInstrumentedTest` por separado.

## En el emulador
Capturas de las pruebas de Compose (Aviso de privacidad real con acordeones, documento abierto con «Cerrar todo», Licencias, Acerca de, Ajustes con el interruptor y «Legal y ayuda», Crear cuenta con la casilla) revisadas a ojo; la viñeta quedaba demasiado separada del texto y se redujo su espacio.

**Figma — pendiente:** la página **«Legal»** no se puede alcanzar desde el MCP (el archivo solo expone el lienzo «Minimalista» hasta los frames de la tarea 21). Las vistas se hicieron con el enunciado de la tarea y los componentes del sistema de diseño; **no están comparadas con Figma**. Necesito los ids de nodo de la página «Legal» para compararlas con los acordeones abiertos.

## Decisiones y desviaciones a revisar
- Los nombres de los componentes de terceros en `Licenses.kt` son nombres propios y viven en el código (no en `strings.xml`); los textos de la pantalla, sí.
- El texto de `cc-by-4.0.txt` es el aviso de atribución con un resumen de la licencia y el enlace al texto legal completo, no el texto legal íntegro.
- El marcador `[privacidad@correo.com]` y los `[Nombre completo del integrante …]` de los documentos se dejaron como están hasta que el equipo ponga los datos reales (**E4 pendiente del equipo**).
- «Novedades y encuestas» va en la tarjeta Cuenta, después de «Actividad reciente».
- Un `PATCH /account` con `displayName` ausente no toca el nombre (distinto de `null`, que lo borra); el backend real debe hacer lo mismo.

## Pruebas manuales que no hice
Abrir «Reportar un problema de seguridad» y el repositorio con una app de correo / navegador reales; recorrer Crear cuenta → documento → volver con la app real (las pruebas de Compose cubren la pantalla, no el flujo completo, porque la prueba de la app real se cuelga, ver tarea 13).

## Vistas / cambios (cómo llegar)
- **Documentos legales:** Crear cuenta → «Términos» o «Aviso de privacidad» (hoja); Ajustes → «Legal y ayuda» → «Términos y condiciones» / «Aviso de privacidad» (pantalla).
- **Casilla de novedades:** Crear cuenta, debajo de la casilla de términos.
- **Interruptor «Novedades y encuestas»:** Ajustes → tarjeta «Cuenta».
- **Licencias y Acerca de:** Ajustes → «Legal y ayuda».
