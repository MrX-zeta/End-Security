# Reporte · Tarea 23 · Toques finales: legal, detalles de uso y brechas del backend

**Estado:** hecha
**Fecha:** 2026-11-05

**REQUIERE REVISIÓN HUMANA:** los pasos 1 y 3 tocan el registro (`marketingOptIn`), el consentimiento (`PATCH /account`), `SecureScreen` y la pila de navegación con secretos (`TabNavigation`). Pasaron por `security-reviewer` (ver abajo): **sin hallazgos altos**, 3 medios y 5 bajos; los tres medios y tres de los bajos quedaron corregidos.

Antes de empezar se subió en un commit aparte (`docs:`, `5d8ac36`) lo que el equipo había completado en `docs/legal/*.md` y `revisiones-pendientes.md`.

## Qué se hizo

### Paso 1 · Datos reales (E4)
- `LegalInfo`: `PRIVACY_EMAIL = endsecurity.privacidad@gmail.com`; **nuevos** `SUPPORT_EMAIL = endsecurity.soporte@gmail.com` y `SUPPORT_SUBJECT = «Soporte End Security»`. «Reportar un problema de seguridad» sigue yendo a `PRIVACY_EMAIL`.
- **Acerca de:** fila nueva **«Soporte»** (`ACTION_SENDTO`, destinatario y asunto fijos, sin cuerpo).
- **Prueba (`LegalPlaceholdersTest`):** ni los dos documentos, ni `strings.xml`, ni los textos de licencias contienen `[privacidad@correo.com]` ni `[Nombre completo`; los documentos nombran a los dos responsables y el canal. El único marcador que queda es el del proveedor de hosting.

### Paso 2 · Comparado con Figma (`235:4976`, `236:5003`, `236:5063`)
Descargué los tres frames a su tamaño real y los comparé con capturas del emulador. Diferencias corregidas:
- **Barra superior con título** junto al botón de atrás («Aviso de privacidad», «Términos y condiciones»): `BackTopBar(title)`.
- **Fecha:** «**End Security**» en negrita y el resto normal (el parser conserva las negritas: `metaSpans`).
- **Resumen:** en Figma es el amarillo **sólido** `FFCB74` (= `primary`), no el amarillo claro del enunciado; se usó el de Figma.
- **Títulos de sección:** planos sobre la página (sin tarjeta blanca), con el chevrón como único indicio del acordeón y una línea fina cuando están cerrados; el texto abierto queda como en Figma. Subtítulos en negrita, viñetas con el punto a 16 dp del texto y números en negrita, texto en `onSurfaceVariant` (`2F2F2F`).
- **Tarjetas de encargados:** blancas, radio 16; la negrita solo donde la trae el texto (antes todo el título iba en negrita y los campos en gris pequeño).
- **Crear cuenta con la casilla opcional:** coincide con Figma (casilla sin marcar, texto de dos líneas).
- Se conservaron «Abrir todo / Cerrar todo» y el estado Expandido/Contraído (accesibilidad), que Figma no dibuja.

### Paso 3 · `security-reviewer`
Revisó `marketingOptIn` en el registro, `updateMarketingOptIn`, el `SecureScreen` del código de correo y, por lo que se tocó hoy, la pila de pestañas y `VaultListViewModel`. **Verificó sin problema:** el consentimiento nace en falso y solo viaja `SignUpViewModel → register → DTO` (sin `SavedStateHandle`, Room ni logs); `ApiJson` no puede omitir un `true`; `EmailSyncingAccountApi` no filtra nada; todas las pantallas con códigos o secretos llevan `SecureScreen` o `SecureOn`; `SECRET_ROUTES` sale de la pila sin guardarse; `clearSavedTabStates` está en todos los caminos de bloqueo, cierre de sesión y cuenta eliminada.

| Hallazgo | Severidad | Qué se hizo |
|---|---|---|
| M1 · el interruptor de «Novedades y encuestas» podía mostrar un valor distinto del servidor (una lectura atrasada pisaba el cambio; con la respuesta del PATCH perdida se revertía aunque el servidor lo hubiera aplicado) | Media | **Corregido:** contador de generación que descarta las lecturas anteriores a un cambio y relectura del servidor tras un fallo o una respuesta distinta de la pedida. 2 pruebas nuevas. |
| M2 · `HealthViewModel` (ahora vivo con `saveState`) no se vaciaba al bloquear con la pantalla en segundo plano | Media | **Corregido:** `SharingStarted.Lazily` y `combine(session.state)` que lo vacía en `Locked`. Prueba nueva. |
| M3 · el consentimiento enviado era el leído *dentro* del `launch`, no el de tocar «Crear cuenta»; la casilla seguía tocable mientras cargaba | Media | **Corregido:** se captura al tocar y la casilla no cambia mientras `isLoading`. Prueba nueva. |
| B1 · el backend falso no era estricto en `PATCH /account` (`displayName:""` borraba, números y cadenas se aceptaban como valor) | Baja | **Corregido** y probado (7 casos). |
| B2 · `AccountInfo.marketingOptIn = false` por defecto en el dominio | Baja | **Corregido:** sin valor por defecto. (La coordinación de versión del backend, que ya no decodifica `Account` sin `marketingOptIn`, queda en `brechas-backend.md` G-12/D-8.) |
| B3 · sin red el interruptor se ve apagado y deshabilitado | Baja | **Se deja** como pide el enunciado de la tarea 22 («deshabilitado sin valor»); sí avisa cuando el servidor responde un valor distinto del pedido. |
| B4 · `CodeField` sin `ExcludeFromAutofill` (Verificar correo, 2FA, código de correo) | Baja | **Se deja y se documenta:** son códigos de un solo uso con `SecureScreen` y teclado `NumberPassword`; `ContentType.SmsOtpCode` es deliberado (autocompletar desde el SMS/correo). |
| B5 · la prueba de bloqueo copiaba el cuerpo de `navigateClearingStack` | Baja | **Corregido:** llama a la función real; casos nuevos de hijos no secretos y de estado guardado bajo la Bóveda. |

### Paso 4 · Preparar la unión con el backend real (solo análisis)
Clonado `develop` (`921cf2d`) en solo lectura fuera del repositorio. **`docs/api/brechas-backend.md`**: 13 brechas generales y la tabla de EP-01…EP-26 (contrato / backend hoy / falta) con 11 decisiones. En corto: el backend responde en `/api` (no `/v1`), con `{success,data}`, errores sin `code`, base64 estándar con relleno, `authHash`/`pvk`/`memoryKib`; el **registro responde 409** con un correo existente (rompe DEC-13) y no acepta `userId` (la AAD de la `protectedVaultKey` no puede incluirlo); la bóveda sube en **lote atómico** en vez de un elemento por petición; **no existen** 2FA, `PATCH /account`, sesiones (EP-23/24), `GET /vault/items/{id}`, cambio de contraseña ni eliminar cuenta. Solo `logout` (EP-09) es equivalente. **No se cambió el contrato ni `ApiTransport`.** Decisión que bloquea a la app: **D-4** (lote atómico vs. un elemento por petición).

### Paso 5 · Detalles de uso (Redmi)
1. **Aviso de la Bóveda** («N contraseñas necesitan atención · Ver»; el botón dice «Ver», no «Revisar»). Con la prueba de Compose a escala 1.3 y a 296 dp **no logré reproducir el texto debajo del botón** con el componente anterior, pero sí un defecto real: el botón quedaba **pegado arriba** de su caja de 48 dp, no centrado. `MessageBar` ahora es `Row` centrada con el texto en `weight(1f)` (máx. 2 líneas con acción) y el botón en su propia caja de ≥ 48 × 48 dp que se mide **antes** que el texto, así que no puede pasar por debajo. 3 pruebas (1×, 1.3×, 1.3× angosto).
2. **Retraso al volver a la Bóveda.** *Qué era:* las pestañas usaban las transiciones por defecto del `NavHost`: un fundido de entrada de **700 ms** (la Bóveda ya no se recreaba: su ViewModel seguía vivo) y, además, la lista se volvía a descifrar entera si pasaban más de 5 s fuera (`WhileSubscribed(5 s)`). *Medición* (prueba `TabSwitchTimingTest`, reloj de animación de Compose, mediana de 5 vueltas Bóveda → Generador → Bóveda): **784 ms antes, 80 ms ahora**. *Qué se cambió:* las cuatro pestañas sin transición (`TabEnterNone/TabExitNone`), `saveState`/`restoreState` y `VaultListViewModel` con `Lazily` (la lista ya descifrada está en el primer cuadro; al bloquear se vacía sola). No medí con Layout Inspector ni trazas del sistema: la medición es de la animación, no del descifrado.
3. **Pila de las pestañas** (`TabNavigation.kt`): `popUpTo(VAULT){saveState}`, `launchSingleTop`, `restoreState`. Atrás desde Salud, Generador o Ajustes vuelve a la Bóveda; desde la Bóveda sale de la app; dentro de una pestaña recorre su pila. Los destinos con secretos (`SECRET_ROUTES`: Generador, detalle y edición, conflictos, 2FA, contraseña maestra, correo, eliminar cuenta) **salen de la pila sin guardarse** antes de cambiar de pestaña, y `navigateClearingStack` olvida lo guardado (`clearSavedTabStates`) al bloquear o cerrar sesión. `TabNavigationTest`: 13 pruebas (cada caso del enunciado, estado conservado con los mismos ViewModels, secretos vaciados y bloqueo con la función real).
4. **Barra inferior sin onda:** `selectable(indication = null)`, con `Role.Tab`, estado seleccionado y 76 × 54. Pruebas: la pestaña no cambia de píxeles al tocarla y mide ≥ 48 dp.
5. **Teclado:** auditoría con **teclado real** (`KeyboardAuditTest`, captura de pantalla completa) a **412 × 891** y **360 × 640**. Tabla abajo.
6. **Salud sin conexión:** la línea solo dice «Revisado hoy/ayer a las HH:MM» o «Sin revisar»; «Revisar ahora» va como botón de texto (≥ 48 dp) **en su propia línea**; al tocarlo sin red sale `MessageBar` «Sin conexión. Te mostramos la última revisión.» y lo no revisado sigue como «N contraseñas sin revisar todavía». Quitado «· sin conexión» de la línea. Pruebas: ViewModel (con y sin red, la bandera vieja no avisa) y Compose (escala 1.3: el botón queda debajo de la fecha y en una sola línea).

#### Tabla del teclado (vista · ¿tenía problema? · qué se cambió)

| Vista | ¿Problema? | Cambio |
|---|---|---|
| Iniciar sesión | No a 412; a 360 «Entrar» queda en el borde y se alcanza desplazando | Ninguno (ya acomodaba el formulario) |
| Crear cuenta | No: cuatro campos, el botón se alcanza desplazando | Campos con `keepVisibleWhenFocusedWithKeyboard` |
| Verificar correo | **Sí:** «Verificar» quedaba a medias bajo el teclado | Se oculta el disco decorativo con el teclado abierto; el botón se acomoda solo |
| 2FA del acceso | **Sí:** «Continuar» a medias | Igual |
| Código de respaldo | **Sí:** campo cortado y botón fuera | Igual |
| Desbloquear | **Sí:** campo cortado y botón oculto; a 360 solo se veía la cabecera | Cabecera compacta con teclado; **huella debajo de «Desbloquear»** y oculta con el teclado; el acomodo espera la altura final del teclado |
| Nueva contraseña | No (el botón «Guardar» es fijo y se ve; la burbuja «See more features» es de Gboard) | Ninguno |
| Búsqueda de la Bóveda | No: el campo queda a la vista, la lista se desplaza | Ninguno |
| Contraseña maestra | **Sí:** la barra inferior se quedaba encima del teclado | Barra oculta con teclado (`FlowScaffold`) |
| Correo (nuevo) | **Sí:** idem | Idem |
| Correo (código) | **Sí:** botón a medias; la captura sale negra por `SecureScreen` (Paso 0) | Disco oculto, botón acomodado |
| 2FA, paso 2 (código) | **Sí:** barra inferior encima | Barra oculta |
| Eliminar cuenta | **Sí:** barra inferior y campo a medias | Barra oculta |
| Hoja de reautenticación | A 412 no; la prueba inicial no usaba el `imePadding` real | Se audita la hoja real (`ReauthSheet`) |
| Generador | Sin campos de texto | — |

Resultado: **14 de 14** pasan a 412 × 891 (campo y botón completos y a la vista, sin desplazar, salvo Crear cuenta) y **14 de 14** a 360 × 640. **Límite conocido a 360 × 640:** en Contraseña maestra, Código de respaldo y la hoja de reautenticación el campo enfocado queda **rozando ~16 dp contra el borde del área desplazable**; sigue visible y se alcanza desplazando, y la auditoría solo exige a 640 dp que se vea y se alcance. Con ~250 dp útiles tras el teclado no cabe todo sin desplazar.

## Pruebas (salida real)
- `testDebugUnitTest`: sin fallas. Nuevas: `LegalPlaceholdersTest` (3), `HealthViewModelTest` (+3), `SettingsViewModelTest` (+2), `SignUpViewModelTest` (+1), `MarketingOptInAgainstFakeServerTest` (+1), `LegalMarkdownTest` (+1, `metaSpans`).
- Compose / dispositivo nuevas: `TabNavigationTest` (13), `TabSwitchTimingTest` (2), `KeyboardAuditTest` (14, a dos tamaños), `MessageBarTest` (3), `BottomNavBarTest` (+2), `HealthScreensTest` (+2), `LegalScreensTest` (9; Soporte y los documentos con barra y «Abrir todo»).
- `lintDebug`: sin errores (había uno real: `TabNavigation` usaba `NavController.currentBackStack`, API restringida; ahora usa `getBackStackEntry`). `assembleRelease`: compila.
- **`connectedDebugAndroidTest` completa, en cinco tandas** (el emulador se cae en corridas largas y hubo que reiniciarlo): **243 pruebas, 0 fallas**, 5 omitidas (huella sin sensor): `feature.account` 58, `feature.activity` 25, `feature.auth` + `navigation` 39, `core` 55, `feature.health` + `settings` + `vault` 52, y `KeyboardAuditTest` 14 (a 412 × 891 y a 360 × 640). No corrí `ExampleInstrumentedTest`. Una prueba mía nueva (`TabNavigationTest`, bloqueo con hijos de la Bóveda) falló en la primera tanda por un error de la propia prueba (comparaba contra una entrada que no se reabría); se corrigió y pasa (13 de 13).
- **`ClipboardDeviceTest` pasó 4 de 4 esta vez**, pero **E3 sigue pendiente**: no se desactivó «Enable clipboard sharing» (la corrida anterior fallaba por eso). `KeyboardAuditTest` se corrió por última vez antes de las correcciones del revisor, que no tocan ninguna pantalla.

## Cómo reproducir la auditoría del teclado
El AVD `Pixel_7_Pro` se puso en `hw.keyboard = no` (si no, el emulador no muestra el teclado en pantalla). Tamaño pequeño: `adb shell wm size 1080x1920; adb shell wm density 480`; volver con `wm size reset; wm density reset`. Las capturas quedan en `cache/captures/teclado-<vista>-<ancho>x<alto>.png` de la app.

## Decisiones y desviaciones a revisar
- El resumen es el amarillo sólido de Figma, no el «amarillo claro» del enunciado.
- Las secciones de los documentos son títulos planos con chevrón (como Figma abierto), no tarjetas blancas.
- «Ver» (no «Revisar») es el botón del aviso de la Bóveda.
- `ItemEditScreen` y `FormFields` pasaron de `private` a `internal` para poder auditarlos.
- Las capturas de pantalla completa de la auditoría de las vistas con `SecureScreen` salen negras (FLAG_SECURE); la comprobación numérica sí aplica.
- Lo pendiente del equipo: **E3** (`ClipboardDeviceTest` con el portapapeles compartido desactivado), el proveedor de hosting en el aviso, y decidir **D-4** del backend.

## Vistas / cambios (cómo llegar)
- **Soporte:** Ajustes → «Legal y ayuda» → «Acerca de» → «Soporte».
- **Documentos con barra y estilo de Figma:** Ajustes → «Legal y ayuda» → «Términos y condiciones» / «Aviso de privacidad».
- **Salud sin conexión:** pestaña Salud, tocar «Revisar ahora» sin red.
- **Teclado:** abrir cualquier vista con campos (Desbloquear, Verificar correo, Contraseña maestra…) y subir el teclado.
