# Tarea 10.1 · Splash (SCR-46)

**Estado:** hecha
**Escrita:** 2026-10-21 · sesión de diseño

## Revisión de la tarea 10
Aprobada. La app ya funciona de punta a punta con el backend falso. Tus cinco puntos abiertos (accesibilidad de la
contraseña, el bloqueo en el quinto intento, el código de prueba en release, los secretos de `fake_server.json` y la
ruta del falso en `CLAUDE.md`) se resuelven en la tarea 11. **No los toques en esta.** La ruta de `CLAUDE.md` ya la corregí yo.

## Objetivo
Hoy, quien ya pasó el onboarding cae directo en Iniciar sesión o Desbloquear. Se agrega un splash con la marca:
la **propuesta 3 sin manchas ni disco**, elegida por el equipo.

**Figma:** tablero «Splash · Final», https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=165-3711
- **A · Splash del sistema:** fondo `#2F2F2F` (el `background` del tema oscuro) con el ícono adaptativo centrado.
- **B · Pantalla propia:**
  - el escudo a 0,79× y 86 dp más arriba que en A;
  - «**End** Security» en 32, con la primera palabra Bold y el resto Regular, interletrado −2 %;
  - el lema «TUS CONTRASEÑAS, SOLO TUYAS» en 13 Medium, `on-surface-variant` (#B0B0B0) y +4 %;
  - tres puntos de carga `primary` abajo, **solo si el destino tarda más de 800 ms**.
- El tablero trae la línea de tiempo de la animación; es la referencia.

## Skills que debes usar
La persona del equipo te pide que trabajes con estas skills:
- **`/design-banner-logo`**: composición de marca, jerarquía y espacio en blanco de la pantalla B.
- **`/animate`**: decidir qué se anima, con qué curvas y duraciones, cómo se interrumpe y cómo sale.
- **`/apple-design`**: movimiento fluido y físico (resortes sin rebote visible, continuidad espacial entre A y B, respeto a «reducir movimiento»).

Además, te dan acceso a la carpeta de skills local **`C:\Users\brian\Dev-projects\SKILLS\Claude`**, por si necesitas
algún recurso extra (referencias o datos de esas skills). Úsala solo para leer.
Si una skill sugiere algo que contradice el tablero de Figma o una regla del proyecto, **manda el tablero y el proyecto**.
Anótalo en el reporte.

## Paso 1 · Splash del sistema
- `androidx.core:core-splashscreen` (agrégala a `libs.versions.toml`) e `installSplashScreen()` en `MainActivity` antes de `super.onCreate`.
- Tema `Theme.EndSecurity.Starting`:
  - `windowSplashScreenBackground` = `#2F2F2F`;
  - `windowSplashScreenAnimatedIcon` = la capa frontal del ícono (`@drawable/ic_launcher_foreground`);
  - `postSplashScreenTheme` = el tema actual;
  - **sin** `windowSplashScreenBrandingImage`.
- `setKeepOnScreenCondition` solo hasta que Compose dibuje su primer cuadro (no hasta que se decida el destino).
- `setOnExitAnimationListener`: quita la vista del sistema **sin animarla** (`remove()`), porque la continuidad la da el escudo de Compose, que ocupa exactamente el mismo lugar.
- Si quieres medir la posición real del ícono, usa `splashScreenView.iconView` para alinear. Compruébalo en Android 12+ y en Android 9–11, donde la librería de compatibilidad dibuja su propia versión.

## Paso 2 · Pantalla propia (`feature/auth/ui/splash/`)
- `SplashScreen` en tema oscuro (DEC-51), con `SystemBarsAppearance(darkBackground = true)`. Reemplaza el `Box` vacío que `AppNavHost` muestra mientras `startRoute == null`.
- El escudo es el mismo vector del ícono (`ic_launcher_foreground` en un `Image`, o su `pathData`).
  - **Posición inicial:** el centro y el tamaño del ícono del sistema (lienzo de 240 dp centrado).
  - **Posición final:** la de B.
- **Animación** (Figma manda en los valores; las skills, en la sensación):
  - **0–500 ms:** el escudo sube y se reduce con un resorte crítico o casi crítico, sin rebote visible;
  - **120–520 ms:** la marca pasa de opacidad 0 a 1 y sube 12 dp;
  - **260–620 ms:** el lema hace lo mismo;
  - **≥ 800 ms:** los puntos con un pulso suave, solo si `StartDestinationViewModel` todavía no decide;
  - **salida:** cuando hay destino **y** terminó la entrada, fundido de 250 ms hacia la ruta. No agregues esperas artificiales más allá de terminar la entrada (~620 ms).
- **«Quitar animaciones»** (`rememberReducedMotion()`): se dibuja B directamente y se sale con un corte.
- Si el usuario regresa con «atrás» durante el splash, sale de la app.
- **Accesibilidad:** el escudo es decorativo (`contentDescription = null`). La pantalla anuncia «End Security» una vez y los puntos no se anuncian.
- Textos en `strings.xml`: el lema es nuevo y la marca usa `app_name`.

## Paso 3 · Navegación (DEC-56, grafo v1.5.2)
- El splash se muestra **en cada arranque en frío**, también la primera vez.
- Destino, sin cambios a la lógica actual:
  - sin onboarding visto → onboarding;
  - con cuenta local → Desbloquear;
  - si no → Iniciar sesión.
- El splash no queda en la pila: atrás desde el destino sale de la app.
- **No** se muestra al volver de segundo plano (arranque en caliente) ni al bloquearse la bóveda.

## Terminado cuando
- Pasan las pruebas del `ViewModel` o estado del splash: los puntos aparecen solo después de 800 ms; la salida espera a que termine la entrada; con «reducir movimiento», B aparece sin animación.
- Hay `@Preview` de B (final), del estado inicial y con los puntos.
- **En el emulador** (Android 12+) y, si es posible, en un emulador con Android 9–11:
  - no hay salto visible entre A y B;
  - los tres destinos funcionan;
  - con «Quitar animaciones» activado no hay movimiento.
- Si puedes grabar un video corto con `adb shell screenrecord`, deja la ruta en el reporte, no en el repositorio.
- `assembleDebug`, `testDebugUnitTest`, `assembleRelease` y lint pasan.
- Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`. Incluye qué aportó cada skill y en qué te apartaste de ellas. Cambia el estado de arriba a `hecha` y detente.
