# Reporte · Tarea 25 · Transiciones más notorias y barra inferior animada

**REQUIERE REVISIÓN HUMANA** (se tocó la salida de la zona segura en `NavMotion` y la limpieza de la contraseña en `UnlockScreen`). El subagente `security-reviewer` revisó la tarea 24 (Paso 0) y sus hallazgos están abajo.

## Paso 0 · Revisión de seguridad de la tarea 24
0 altos, 0 medios, 4 bajos:
- **BAJO-2** (corregido): con animaciones reducidas, la salida de una zona segura hacia el acceso ya no deja la pantalla vieja visible; `NavMotion` usa `fadeOut(snap())` en ese caso (`exit` y `popExit`).
- **BAJO-3** (corregido): `UnlockScreen` limpia el campo de contraseña y oculta el texto (`passwordVisible = false`) antes de navegar a Bóveda o Inicio de sesión.
- **BAJO-1** (parcial): el NavHost de pruebas ahora también lleva `predictivePopEnter/ExitTransition`. No hay pruebas con el gesto real ni con rutas seguras reales.
- **BAJO-4**: informativo, sin cambios.

## Qué se hizo

### Paso 1 · Valores de movimiento (`Motion.kt`)
`Push` 260 ms, `PushExit` 220 ms, `Slide` 30 → **60 dp**, `PushLimitMillis` 280 ms. Pestañas sin cambios (fade 150/90, límite 200 ms).

### Paso 2 · Píldora deslizante (`BottomNavBar.kt`, reescrito)
- Una sola píldora de 44×36 dp dibujada detrás de la fila; su posición vive en espacio de índice (`Animatable<Float>` con `spring(dampingRatio = 1, stiffness = 440)`, sin rebote) y solo se lee en `graphicsLayer` (fase de dibujo, sin recomposición).
- Si se toca otra pestaña a mitad del camino, el resorte continúa desde donde está (sin saltos).
- Cada pantalla dibuja su propia barra, así que se añadió `TabBarHandoff` + `LocalTabBarHandoff` (se provee en `AppNavHost`): la barra nueva arranca desde la pestaña anterior, con validez de 1,5 s. Sin handoff, la primera pantalla dibuja la píldora ya en su lugar.
- Animaciones reducidas: la píldora salta (`snapTo`) y no hay pulso.

### Paso 3 · Ícono
- El color del ícono sigue la cercanía de la píldora (se dibuja en `drawBehind`); la etiqueta usa `animateColorAsState` 150 ms.
- Pulso del ícono destino: escala 0,92 → 1 en 200 ms EaseOut, con 80 ms de retraso para coincidir con la llegada de la píldora. No hay pulso en el primer dibujo ni al volver a tocar la pestaña activa.
- Se mantienen `Role.Tab`, ancho de 76 dp y accesibilidad.

### Revisión cuadro por cuadro
Las pantallas seguras salen negras en `screenrecord`, así que la barra se revisó con `BottomNavBarFramesTest` (reloj de Compose controlado, 16 ms por cuadro; guarda PNG sin afirmar nada). Hojas de contacto en `ai/handoff/capturas/tarea-25/`:
- `pildora-boveda-a-ajustes.png`: la píldora recorre Bóveda → Ajustes en ~270 ms, arranca rápido, frena sin rebote y termina centrada; el ícono de origen se apaga y el de destino se enciende conforme la píldora lo cubre.
- `pildora-cambio-de-rumbo.png`: a los 6 cuadros se cambia el destino a Salud; la píldora gira sin salto ni parada.
- `pildora-toques-rapidos.png`: cinco toques seguidos; la píldora nunca se queda atrás ni salta, y termina centrada en Bóveda.
- Defecto encontrado y corregido en esta revisión: el ícono destino se ponía blanco sobre la barra clara mientras la píldora aún viajaba; ahora el color sigue a la píldora y el pulso espera 80 ms.

## Pruebas (salida real)
- `testDebugUnitTest`, `lintDebug` y `assembleRelease`: OK (estático = 0).
- `BottomNavBarTest`: 11/11 (7 nuevas: primer dibujo en su lugar, deslizamiento y centrado, continuidad ante interrupción, salto con animaciones reducidas, pulso, sin pulso en primer dibujo/re-toque/animaciones reducidas, handoff).
- `connectedDebugAndroidTest` por lotes: navegación OK (incluye `NavTransitionsTest` y `TabSwitchTimingTest`: push/pop ≤ 280 ms, pestañas ≤ 200 ms), cuenta OK, actividad 25/25, auth 22/22, salud 13/13, ajustes 25/25, bóveda 14/14, `core` 65 con 5 omitidas y **3 fallos solo en `ClipboardDeviceTest` (E3, pendiente del equipo)**.
- `KeyboardAuditTest`: 17/17 a 412×891 y a 360×640 dp (tamaño y densidad restaurados después).
- `feature.twofactor` y `feature.unlock` no tienen pruebas instrumentadas propias.

### Incidencias del entorno (no del código)
- El emulador se saturó (carga ~16, CPU de sistema 372 %) y un primer `core` dio 10 timeouts de `captureToImage`, incluso en `MessageBarTest`. Tras reiniciarlo en frío, los mismos tests pasaron.
- Una vez `GeneratorScreenTest` falló al cerrar la actividad ("never becomes DESTROYED"); al repetir el lote de bóveda pasó 14/14.

## Pendiente manual en el Redmi
- M19: sentir la píldora y el pulso al cambiar de pestaña, con animaciones activadas y reducidas.
- M20: transiciones push/pop y regreso predictivo con los 60 dp de desplazamiento.
- E3 (`ClipboardDeviceTest`) sigue pendiente para el equipo.

## Decisiones y desviaciones a revisar
1. La posición de la píldora está en espacio de índice y no en píxeles, para que el resorte sea independiente del ancho de pantalla.
2. El color del ícono sigue a la píldora y no usa el `animateColorAsState` fijo de 150 ms del texto de la tarea: con 150 ms EaseOut el ícono entrante quedaba invisible mientras la píldora llegaba.
3. El pulso lleva 80 ms de retraso para coincidir con la llegada.
4. Como cada pantalla dibuja su barra, la barra vieja se desvanece (90 ms) mientras la nueva aparece (150 ms) y la píldora se desliza; la regla de "ya en su lugar" depende de `TabBarHandoff` (válido 1,5 s).
5. Bóveda, Salud y Generador no se pudieron grabar con `screenrecord` por FLAG_SECURE; la revisión visual de la barra se hizo con capturas de Compose.
6. BAJO-1 sigue parcial (sin pruebas del gesto real).

## Vistas / cambios (cómo llegar)
No hay vistas nuevas. La barra inferior animada se ve al cambiar de pestaña en Bóveda, Salud, Generador y Ajustes. Las transiciones más marcadas se ven al abrir cualquier pantalla de detalle (p. ej. Ajustes → Soporte) y al volver con el gesto atrás.
