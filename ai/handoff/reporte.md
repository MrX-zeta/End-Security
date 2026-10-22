# Reporte · Tarea 10.1 · Splash (SCR-46)

**Estado:** hecha
**Fecha:** 2026-10-22

No toqué `core/crypto`, `core/session`, autenticación ni SQL: **no requiere revisión de seguridad**.
Dejé fuera, como pedía la tarea, los cinco puntos de la tarea 11.

## Qué se hizo

### Paso 1 · Splash del sistema (A)
- **`androidx.core:core-splashscreen` 1.2.0** en `libs.versions.toml` y `app/build.gradle.kts`.
- **`Theme.EndSecurity.Starting`** (`themes.xml`), puesto en `MainActivity` en el manifiesto:
  - `windowSplashScreenBackground` = `@color/splash_background` (nuevo en `colors.xml`, `#2F2F2F`, igual al `background` oscuro);
  - `windowSplashScreenAnimatedIcon` = `@drawable/ic_splash_icon` (ver «Diferencias»);
  - `postSplashScreenTheme` = `Theme.EndSecurity`;
  - sin imagen de marca.
- **`MainActivity`:**
  - `installSplashScreen()` antes de `super.onCreate`;
  - `setKeepOnScreenCondition` solo hasta que Compose dibuja su primer cuadro (`withFrameNanos`), no hasta decidir el destino;
  - `setOnExitAnimationListener { it.remove() }`, sin animar.

### Paso 2 · Pantalla propia (B) en `feature/auth/ui/splash/`
- **`SplashScreen.kt`:**
  - `SplashRoute` (con estado) y `SplashContent` (sin estado, tres `@Preview`: inicial, final y con puntos);
  - tema oscuro con `SystemBarsAppearance(darkBackground = true)`.
  - El escudo es `ic_launcher_foreground` en un `Image` de 240 dp centrado. **Sale del mismo lugar y tamaño que el ícono del sistema** y sube 86 dp, a 0,7875× (104,27 / 132,4, que Figma redondea a «0,79»).
  - **Marca:** «**End** Security» en 32, Bold + Regular, −2 %, Montserrat.
  - **Lema:** «TUS CONTRASEÑAS, SOLO TUYAS» en 13 Medium, +4 %, `on-surface-variant`.
  - **Puntos:** tres de 8 dp `primary` con pulso desfasado.
  - Textos en `strings.xml`: el lema es nuevo (`splash_tagline`) y la marca usa `app_name`.
- **`SplashSequencer.kt`:** el orden de las fases, sin dibujar, para poder probarlo con tiempo virtual:
  - la entrada corre completa aunque ya haya destino;
  - los puntos salen solo pasados 800 ms y con el destino sin decidir;
  - la salida espera a que haya destino **y** termine la entrada, sin esperas extra (entrada + 250 ms);
  - con «Quitar animaciones» no hay entrada y se sale al decidir el destino, con un corte.
- **Animación:**
  - **escudo (0–500 ms):** resorte crítico (amortiguamiento 1, rigidez 247 ≈ respuesta 0,4 s), sin rebote;
  - **marca (120–520 ms) y lema (260–620 ms):** opacidad 0→1 y 12 dp hacia arriba, con `cubic-bezier(0.23, 1, 0.32, 1)`;
  - **puntos:** aparecen con un fundido de 200 ms;
  - **salida:** fundido de 250 ms de toda la pantalla.
  - Todo va en `graphicsLayer`, así que animar no recompone.
- **Accesibilidad:**
  - el escudo es decorativo (`contentDescription = null`);
  - la pantalla anuncia «End Security» una vez (`paneTitle`);
  - los puntos se limpian de la semántica;
  - mientras el splash está encima no pasan toques a lo de abajo;
  - atrás durante el splash cierra la app.

### Paso 3 · Navegación
- **`AppNavHost`:** el splash es una capa sobre la navegación, no una ruta.
  - `AppNavigation` (el contenido que ya había) se arma debajo en cuanto `StartDestinationViewModel` decide, para que el fundido de salida la revele.
  - `splashFinished` es `rememberSaveable`: no reaparece al girar ni al restaurar la actividad, y **no queda en la pila**.
  - La lógica de destino no cambió.
  - Con una ruta de depuración (`debug_route`) no se muestra.

## Qué aportó cada skill y en qué me aparté
- **`/animate`:**
  - **Primera pregunta de la skill («¿debe animarse?»):** el arranque es poco frecuente, así que entra en el presupuesto de la skill.
  - **Propósito:** «continuidad espacial», no decorar.
  - **Salida y puntos:** salida de 250 ms con `ease-out`; puntos solo si hay espera, sin esperas inventadas.
  - **Propiedades:** solo `transform` y opacidad.
  - **Curvas y tiempos:** la curva `ease-out` fuerte (0.23, 1, 0.32, 1) para la marca y el lema.
  - **Reducir movimiento:** va incluido.
- **`/apple-design`:**
  - **Resorte:** críticamente amortiguado (damping 1,0, respuesta ≈ 0,4 s; la tabla de la skill) para el escudo, porque no hay gesto con momento que justifique rebote.
  - **Continuidad A→B:** el escudo empieza exactamente donde estaba el del sistema.
  - **Reducir movimiento:** una variante estática en vez de una versión atenuada.
- **`/design-banner-logo`:**
  - **Poco aplicable:** es una skill de logos y banners con scripts de generación de imágenes (Gemini), y aquí el logo ya existe y está decidido.
  - **Lo que sí usé:** su criterio de composición (espacio en blanco amplio, una sola jerarquía: escudo → marca → lema, mismo eje centrado), medido contra Figma.
  - **No ejecuté sus scripts.**
- **Me aparté de las skills en:**
  - **Reducir movimiento:** `apple-design` recomienda un fundido corto y `animate` «menos, no cero». Aquí mandó el tablero: se dibuja B directo y se sale con un corte.
  - **Salida sin esperas:** Figma manda la entrada de ~620 ms aunque el destino esté listo antes; no la acorté.
- **No usé la carpeta de skills local** (`Dev-projects\SKILLS\Claude`): las tres skills ya traían lo necesario.

## Diferencias con Figma o con la tarea
1. **Ícono del sistema (importante):** en Android 12+ el sistema dibuja la capa frontal en un lienzo de **288 dp** si no hay fondo de ícono, no de 240.
   - **Medido en el emulador:** el escudo salía de 158 dp de ancho, no los 132,4 de Figma.
   - **Lo que probé:** el atributo de fondo de ícono (que la documentación dice que da 240) no cambió nada en el emulador.
   - **Corrección:** `ic_splash_icon.xml`, un `<inset>` de 8,333 % por lado sobre `ic_launcher_foreground`. El escudo del sistema mide **130,5 dp** (la medición por muestreo de píxeles tiene ±1–2 dp) y está centrado a 445 dp (centro de pantalla: 446).
   - El ícono del lanzador **no cambió**.
2. **Medidas de B comprobadas en el emulador** (pantalla de 412 × 892 dp, el marco de Figma incluye la barra de gestos):
   - escudo: 104 dp de ancho, centro a 359 dp (esperado: 360);
   - marca: centro a 476 dp (esperado: 475);
   - lema: centro a 512,5 dp (esperado: 512,5);
   - puntos: centro a 791 dp (esperado: 791).
3. **Escala final** 0,7875 en vez de 0,79 (son 0,3 px de diferencia).
4. **En Android 12+ el sistema muestra su propio splash también en arranques en caliente.** Eso no se puede quitar; lo que no aparece en caliente es el splash propio, como pedía la tarea (comprobado).
5. **El grafo** no tiene el nodo SCR-46 en esta rama del reporte; no lo toqué (propuesta v1.5.2 de la sesión de diseño).

## Compilación y pruebas (salida real)
- **`testDebugUnitTest`:** 319 pruebas, 0 fallas. **Nuevas:** `SplashSequencerTest` (7):
  - los puntos aparecen a los 800 ms y no a los 799;
  - no salen si el destino llega antes de 800 ms;
  - se quitan al llegar el destino;
  - con destino listo, la salida espera a que termine la entrada (620 ms) y no más (620 + 250);
  - sin destino, la salida espera aunque la entrada ya acabó;
  - con reducir movimiento no hay entrada y se sale sin esperas, también si el destino tarda.
- **`lintDebug`:** 0 errores, 12 advertencias (una más que antes, de versiones nuevas de librerías; ninguna es del splash).
- **`assembleRelease`:** compila. **`assembleDebug`:** instala y corre en el emulador.
- **No corrí las instrumentadas**, porque esta tarea no toca Room ni el Keystore.

## En el emulador (Pixel 7 Pro, Android 16, API 36)
- **Sin salto entre A y B:** medido en capturas, el escudo del sistema (130,5 dp, centro 445) sigue siendo el mismo en el primer cuadro de Compose y desde ahí sube sin saltar.
- **Los tres destinos:** arranque en frío sin datos → onboarding; con onboarding visto y sin cuenta → Iniciar sesión; con cuenta → Desbloquear. ✔
- **Arranque en caliente (Inicio y volver):** no aparece el splash propio. ✔
- **«Quitar animaciones»** (`animator_duration_scale = 0`): llega al destino sin esperar la entrada. ✔ (El movimiento en sí se cubre con `SplashSequencerTest`; no lo vi cuadro por cuadro.)
- **Pantalla B con puntos:** comprobada reteniendo el splash con un cambio temporal (revertido) para capturarla.
- **No se probó en Android 9–11:** solo hay imágenes de sistema 34, 36 y 37.0. Queda sin verificar si la librería de compatibilidad (API < 31) dibuja el escudo en 240 dp o en otro tamaño; con el `<inset>` esperado debería coincidir, pero no lo medí. Conviene medir en un Android 9–11 o en el Redmi.
- **Video:** `adb screenrecord` (a 720 × 1560, porque la resolución nativa no es soportada por el codec) está en
  `C:\Users\brian\AppData\Local\Temp\claude\C--Users-brian-UPchiapas-Septimo-Cuatri-Seguridad-Informatica-U4-proyecto-U4\7f1e0efd-02e8-4b57-bb50-fc4910230e43\scratchpad\splash_arranque.mp4`.
  Está fuera del repositorio. El emulador tarda unos 11 s en abrir en frío, así que ahí el sistema pasa más tiempo en A de lo que pasaría en un teléfono real.

## Vistas
- **Nueva: el splash (SCR-46).** Se ve en cada arranque en frío, antes de la ruta inicial: escudo → «End Security» → lema, y se funde hacia onboarding, Iniciar sesión o Desbloquear.
- Ninguna otra pantalla cambió.
