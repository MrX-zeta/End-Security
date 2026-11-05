# Tarea 22 · Legal y ayuda (PEND-12; DEC-64; contrato 1.4.0)

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 21
Aprobada.
- `EmailNormalizer` está compartido, también con `AuthRepositoryImpl`.
- Bien corregidos los tres hallazgos:
  - el correo del servidor solo se guarda si es válido, ya normalizado y sin cambio de sal, y `ServerAccountCheck` compara los dos lados normalizados;
  - el correo ocupado es indistinguible en EP-16 (en el falso);
  - el `Mutex` contra respuestas atrasadas.
- Bien que corrieras la suite completa (187, 0 fallas) en dos tandas.

Aprobadas tus desviaciones: el decorador `EmailSyncingAccountApi`, `showCode` en `ReauthSheet`, `Countdown` y el `alpha(0.6)`.

**Decisiones:**
- **Pantalla del código:** ponle `SecureScreen()` (Paso 0).
- **Cualquier `validation_failed` como «igual al actual»:** se queda así.
- **Hallazgo 2 para el backend real:** quedó en el alcance del backend como **B17** (contrato 1.4.0).

## Paso 0
- `SecureScreen()` en la pantalla del código del cambio de correo.

## Objetivo
Mostrar en la app el **aviso de privacidad** y los **términos y condiciones** (ya redactados con la rúbrica de Ética y Legislación en TI), las licencias y «Acerca de», y la **casilla opcional de finalidades secundarias** (DEC-64).

**Textos (fuente de verdad):** `docs/legal/aviso-de-privacidad.md` y `docs/legal/terminos-y-condiciones.md`. **No los reescribas.** Si algo no cabe o no se entiende, dilo en el reporte.

**Figma:** página **«Legal»**:
- Aviso de privacidad (T22);
- Términos y condiciones (T22);
- Crear cuenta · casilla opcional (T22).

## Paso 1 · Documentos en la app
- Copia los dos `.md` a `app/src/main/assets/legal/` con una tarea de Gradle (`copyLegalDocs`, antes de `preBuild`) desde `docs/legal/`, para que no haya dos copias que mantener.
- **Renderizador mínimo de Markdown** en `feature/settings/ui/legal`. Solo lo que usan estos archivos:
  - `#` y `##`;
  - una línea completa en `**negritas**` como subtítulo;
  - `**negritas**` en línea;
  - viñetas `- `;
  - listas `1. `;
  - tablas → una **tarjeta por fila** («Encargado» en negrita y debajo «Qué hace: …» y «Datos que recibe: …»).

  Sin enlaces ni HTML. Con pruebas unitarias del parser.
- **Para que no se sienta largo en el teléfono** (lo pidió el equipo):
  - arriba, el título, la fecha y la **tarjeta «Resumen»** (sección `## Resumen` del `.md`) abierta, con el fondo amarillo claro de Figma;
  - debajo, **cada sección `##` como un acordeón plegado**: título y flecha; al tocarlo se abre;
  - un botón de texto «Abrir todo / Cerrar todo»;
  - accesibilidad: cada encabezado de sección es un botón con estado «expandido / contraído», y TalkBack recorre el texto en orden.
- `LegalDocumentScreen` (ya existe con `LegalDocument.Terms` / `Privacy`) usa esto. Funciona **sin sesión** (desde Crear cuenta) y con sesión (desde Ajustes).

## Paso 2 · Crear cuenta
- Los enlaces «Términos» y «Aviso de privacidad» de la casilla obligatoria abren cada documento sin perder lo escrito en el formulario.
- **Casilla nueva, opcional y desmarcada:** «Quiero recibir novedades y encuestas de End Security (opcional)», debajo de la de términos. No bloquea el registro. Su valor viaja en `register` como `marketingOptIn`.

## Paso 3 · Ajustes
- **Cuenta:** fila con interruptor «Novedades y encuestas», con el valor de `getAccount().marketingOptIn`.
  - Al cambiarla: `PATCH /account { marketingOptIn }`.
  - Sin red o con error: vuelve a su valor y muestra el genérico.
  - Sin valor (sin red al abrir): deshabilitada.
- **Grupo nuevo «Legal y ayuda»**, al final:
  - «Términos y condiciones»;
  - «Aviso de privacidad»;
  - «Licencias»;
  - «Acerca de».
- **Licencias:** lista fija, a mano, de las bibliotecas de `gradle/libs.versions.toml` con su licencia (Apache 2.0 o MIT), más Montserrat (OFL 1.1) y «Pwned Passwords · Have I Been Pwned · CC BY 4.0». Al tocar una, se abre el texto de la licencia en una hoja; los textos van en `assets/licenses/`.
- **Acerca de:**
  - nombre y versión (`BuildConfig.VERSION_NAME`);
  - «Proyecto académico · Universidad Politécnica de Chiapas · sin fines comerciales»;
  - «Datos de filtraciones: Have I Been Pwned (CC BY 4.0)»;
  - «Reportar un problema de seguridad», que abre el correo con un `Intent` `ACTION_SENDTO` al correo de privacidad y el asunto «Reporte de seguridad»;
  - el enlace al repositorio.
- No hay Figma para estas filas: usa los componentes de Ajustes (`ListCard`, `ListRowIcon`) con íconos que ya existen.

## Contrato 1.4.0 y backend falso
- `marketingOptIn`:
  - opcional en `register` (falso por defecto);
  - obligatorio en `Account`;
  - `PATCH /account` acepta `displayName`, `marketingOptIn` o ambos (`minProperties: 1`).
- Aplícalo en los DTO y en `FakeServer` (**solo el falso**).

## Datos que faltan del equipo
Los documentos tienen marcadores entre corchetes: nombres, correo de privacidad y proveedor de hosting. **Déjalos como están.** El correo de «Reportar un problema» y el de los documentos salen de una sola constante `LegalInfo.PRIVACY_EMAIL` con el marcador. El equipo los completará antes de entregar (E4).

## Terminado cuando
- **Pruebas del parser:** cada elemento, negritas en línea, tabla → tarjetas y `## Resumen` aparte.
- **Pruebas de ViewModel:**
  - el interruptor con éxito, error y sin red;
  - el registro manda `marketingOptIn` (falso si no se marcó).
- **Pruebas de Compose:**
  - el documento muestra el resumen abierto y las secciones plegadas;
  - abrir una sección y «Abrir todo»;
  - la semántica expandido / contraído;
  - la casilla opcional desmarcada;
  - Ajustes con «Legal y ayuda»;
  - Licencias y Acerca de.
- **En el emulador:** abre los dos documentos desde Crear cuenta y desde Ajustes, y compara con la página «Legal» de Figma (con los acordeones abiertos).
- Paso 0.
- `testDebugUnitTest`, la suite completa de `connectedDebugAndroidTest` (salvo E3; en tandas si hace falta), lint y `assembleRelease` pasan.
- Commits hechos y subidos. Los `docs:` van aparte: `docs/legal`, contrato 1.4.0, alcance del backend y grafo v1.7.0.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
