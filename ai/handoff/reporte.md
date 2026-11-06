# Reporte · Tarea 24 · Desbloquear con el teclado y transiciones entre pantallas

**Estado:** hecha
**Fecha:** 2026-11-06

**REQUIERE REVISIÓN HUMANA:** se tocó el diseño de la pantalla de bloqueo (`UnlockScreen`, que muestra el campo de la contraseña maestra) y el orden de FLAG_SECURE respecto a las transiciones (`SecureScreen` + `NavHost`). Sugiero revisarlo con el subagente `security-reviewer`; **no lo corrí en esta tarea** (siguiente.md no lo pedía y el cambio no toca criptografía, sesión ni autenticación, solo disposición y animación).

## Qué se hizo

### Paso 1 · Teclado en Desbloquear (y Iniciar sesión)
- Nuevo `core/designsystem/component/KeyboardAwareLayout.kt`: un `Layout` propio con tres zonas (encabezado decorativo, contenido principal, pie). Lee `WindowInsets.ime` en la fase de **colocación**, así que se mueve cuadro a cuadro con la animación del teclado, sin animación propia.
- Con teclado: la fila de acciones queda a **16 dp ± 2** del borde del teclado, ni más arriba de lo necesario ni movida si ya cabía. Lo que no cabe se quita en este orden: primero el encabezado decorativo, luego el enlace «¿No eres …? Cerrar sesión». Título, texto, campo y fila nunca se ocultan.
- `UnlockScreen`: una sola fila «Acciones» de 56 dp (huella 56 × 56, 12 dp de separación, «Desbloquear» ocupa el resto), 28 dp bajo el campo. La huella no se oculta ni se mueve con o sin teclado.
- `LoginScreen` revisada con la misma regla (`pinFooterToBottom = false`): el botón quedaba pegado/tapado y ahora queda a **16,0 dp** del teclado. Se quitaron el scroll, `imePadding` y `BringIntoView` que tenía.
- Pruebas (`KeyboardAuditTest`, +3: `desbloquearFilaAccionesConHuella`, `…SinHuella`, `iniciarSesionElBotonQuedaA16DpDelTeclado`): altura de fila, tamaño de huella, separaciones, margen izquierdo, distancia al teclado, que no se mueva si ya cabía y que regrese al cerrar el teclado. **17 de 17 a 412 × 891 y 17 de 17 a 360 × 640.**
- Capturas antes/después en `ai/handoff/capturas/tarea-24/` (`desbloquear-412x891-…`, `desbloquear-360x640-…`, `iniciar-sesion-despues-412-y-360`).
- **Límite conocido a 360 × 640:** con un Gboard alto, la primera línea del título puede quedar bajo la máscara de la barra de estado; el campo, el botón y la huella siguen completos y a la vista.

### Paso 2 · Transiciones
- `core/designsystem/theme/Motion.kt`: `EaseOut` (cubic-bezier 0,23·1·0,32·1), pestaña 150 ms (salida 90), avanzar 240 (salida 200), cambio de estado 200, desplazamiento 30 dp, y los límites que usan las pruebas (200 y 260 ms).
- `navigation/NavMotion.kt`: **una sola política** (`NavTransitions`) que el `NavHost` aplica a todo; ninguna pantalla pone la suya.

| Caso | Transición |
|---|---|
| Cambiar de pestaña | fundido cruzado, sin desplazamiento (150 ms) |
| Avanzar dentro de una pestaña | desliza +30 dp → 0 con fundido (240 ms) |
| Regresar | el mismo camino al revés (200 ms) |
| Bloquear / iniciar sesión / cerrar sesión / cuenta eliminada | fundido de 200 ms; al bloquear, la pantalla con secretos sale **de inmediato** (`fadeOut(snap())`) |
| Hojas, diálogos, splash | sin cambios |

- Solo se anima `alpha` y `translationX`.
- **Regreso predictivo:** `enableOnBackInvokedCallback="true"` en el manifiesto, Navigation 2.10.2, y en el `NavHost` `predictivePopEnterTransition`/`predictivePopExitTransition` iguales a `popEnter`/`popExit`. Sin esto, el gesto mostraba la animación por defecto (la pantalla encogiéndose), que no coincide con «Regresar». `BackHandler` sigue ganando si hay conflicto.
- **«Quitar animaciones»** (`ANIMATOR_DURATION_SCALE = 0`): `rememberReducedMotion()` lee la escala y la política devuelve transiciones nulas; hay una prueba que lo comprueba.
- **SecureScreen:** FLAG_SECURE se pone antes de que la pantalla entre y se quita después de que sale (prueba en `NavTransitionsTest`).
- **Tiempos medidos** (animación = tiempo crudo − piso de Navigation de ~64 ms, que existe incluso sin animación): cambiar de pestaña **144 ms** (límite 200); avanzar y regresar **224 ms** (límite 260). El fundido por defecto anterior tardaba **752 ms**. `TabSwitchTimingTest` sigue pasando y ahora mide la política nueva.
- Interrumpibilidad: se probó avanzar y regresar a mitad de la animación (prueba y grabación) sin saltos.

### Revisión de la grabación real (emulador)
Se grabó Ajustes ↔ Dispositivos con botón Atrás, gesto cancelado a medias, gesto completo e interrupción, y se revisaron los cuadros. El gesto sigue el mismo fundido que «Regresar», al cancelarlo vuelve a Dispositivos sin salto y la interrupción queda limpia.
Dos notas honestas sobre el método:
- Compose **no** escala estas animaciones con la escala del sistema, así que grabar a 5× no sirve para verlas más lentas; se revisó a velocidad normal.
- Las pantallas con secretos salen negras en la grabación (FLAG_SECURE), por eso se usó una pantalla sin secretos. Además `screenrecord` del emulador entrega pocos cuadros por transición; lo fino de los tiempos lo cubren las pruebas con reloj controlado, no la grabación.

## Pruebas (salida real)
- `testDebugUnitTest`: sin fallas (nueva: `NavMotionKindTest`).
- `lintDebug`: sin errores. `assembleRelease`: compila.
- `connectedDebugAndroidTest` en tandas en el emulador `Pixel_7_Pro` (se cayó y hubo que reiniciarlo): `feature.account`, `feature.activity`, `feature.auth`, `navigation`, `core`, `feature.health`, `feature.settings`, `feature.vault`, `feature.twofactor` y `feature.unlock`, **todas con salida 0 (sin fallas)**; no anoté el total por tanda. `KeyboardAuditTest`: **17 de 17 a 412 × 891 y 17 de 17 a 360 × 640**. La primera corrida de `KeyboardAuditTest` se abortó con «System has crashed» del emulador (no fue una aserción) y se repitió completa tras reinstalar los paquetes.
- Sigue pendiente del equipo **E3** (`ClipboardDeviceTest` con el portapapeles compartido desactivado). No corrí `ExampleInstrumentedTest`.

## Pendiente manual en el Redmi
Sentir el gesto real de regreso (cancelar a medias, soltar rápido) y el cambio de pestañas: son los puntos **M19/M20** de `revisiones-pendientes.md`, que ya agregó el equipo.

## Decisiones y desviaciones a revisar
- `KeyboardAwareLayout` es un componente nuevo en el sistema de diseño; Unlock y Login lo comparten.
- La salida «instantánea» al bloquear (`fadeOut(snap())`) es deliberada: con `ExitTransition.None` la pantalla con secretos seguía visible hasta terminar la entrada de la siguiente.
- En 360 × 640 el título puede perder su primera línea bajo la máscara de la barra de estado con teclado alto (ver arriba).
- `TabEnterNone`/`TabExitNone` de `TabNavigation` se eliminaron: la política única los reemplaza.

## Vistas / cambios (cómo llegar)
- **Desbloquear con teclado:** bloquear la bóveda (o reabrir la app) y tocar el campo de la contraseña maestra; la fila «huella + Desbloquear» sube con el teclado.
- **Iniciar sesión:** pantalla de inicio de sesión, tocar el campo de contraseña; el botón queda 16 dp sobre el teclado.
- **Transiciones:** cambiar de pestaña (fundido), Ajustes → Dispositivos (desliza y regresa con el botón Atrás o el gesto desde el borde).
