# Reporte · Tarea 26 · Movimiento en Salud, contraseñas que aprovechan el ancho y avisos de guardado

**REQUIERE REVISIÓN HUMANA**: se tocó la navegación y el bloqueo (barra persistente, `LockGuard`, `clearSavedTabStates`), el flujo de 2FA y los avisos con nombres de elementos. Revisó `security-reviewer` (abajo).

## Qué se hizo

### Paso 0 · Barra inferior persistente
- **Una sola `BottomNavBar` fuera del `NavHost`** (`PersistentTabBar`, en `AppNavigation`). `tabForRoute(destino)` dice si hay barra y qué pestaña se enciende; sin barra en acceso, Desbloquear, «Cuenta eliminada», documentos legales y con el teclado abierto (se oculta al instante; el teclado ya se mueve). Con la bóveda bloqueada tampoco (hallazgo B1).
- Aparece y se oculta **deslizando hacia abajo y desvaneciéndose en 200 ms** (`Motion.BarVisibility`, `EaseOut`); con «Quitar animaciones», sin movimiento.
- **No se vuelve a crear al cambiar de pestaña**: la píldora se desliza sobre la misma barra. Se quitó `TabBarHandoff`/`LocalTabBarHandoff`.
- Las pantallas ya no dibujan la barra ni reciben `onTabSelected`. Como la barra queda **fuera** del contenido, el `NavHost` ocupa el espacio de arriba y nada queda debajo (no hizo falta `contentPadding`).
- **`TabGuard` / `InterceptTabSelection`**: antes cada pantalla decidía qué hacer al tocar una pestaña. Ahora la pantalla visible intercepta el toque: Cambiar contraseña, Cambiar correo y Eliminar cuenta lo ignoran mientras trabajan; 2FA Paso 3 pide confirmar («¿Ya guardaste tus códigos?») antes de salir. Es la única pantalla que conserva `onTabSelected` (navega ella tras confirmar).
- **`SlidingSelectionIndicator`** (estado + fondo + `trackSelectionCenter`): la lógica común de la píldora, usada por la barra y por los selectores.

### Paso 1 · Salud
- **Selector** («Resumen | Progreso», «Más fuertes | Más débiles»): un solo fondo que se desliza (`spring(1, 440)`, espacio de índice, leído en `graphicsLayer`); el color del texto sigue al fondo (`BasicText` con `ColorProducer`).
- **Cambio de contenido**: `DirectionalContentSwap`, fundido de 150 ms con 16 dp en el sentido del selector (a la derecha al ir a «Progreso», al revés al volver). Igual en el ranking (`HealthUiState.rankingFor(tab)`).
- **Entrada de tarjetas** (`staggeredEnter`): suben 8 dp y aparecen en 200 ms `EaseOut`, escalonadas 30 ms, máximo 6; solo la primera vez por vista en la sesión desbloqueada (`rememberSaveable` por entrada de la pila; se pierde al bloquear). Resumen: aro (0), textos (1), cuatro filas (2–5). Progreso: gráfica (0), dos indicadores (1, 2), «Por fuerza» (3).
- **Datos** (`rememberAnimatedFloat`): el arco se dibuja y el número cuenta de 0 al valor en 600 ms; la línea de 12 semanas se traza de izquierda a derecha en 600 ms con un recorte que avanza, y el último punto aparece al llegar; las barras de «Por fuerza» crecen en 300 ms escalonadas 30 ms. Si los datos cambian («Revisar ahora»), arco, número, puntos y barras van **del valor anterior al nuevo** en 300 ms, sin volver a cero. No bloquean nada.
- **TalkBack** lee «78 de 100» desde el primer cuadro (la descripción del aro usa el valor final).
- Constantes nuevas en `Motion.kt`: `EnterRise`, `Stagger`, `ChartDraw`, `CountUp`, `MessageIn`, `MessageOut` y las que se desprenden (`EnterMillis`, `MaxStaggered`, `ContentSwap`, `ContentSwapSlide`, `BarGrow`, `ValueChange`, `BarVisibility`, `MessageRiseIn`, `MessageDropOut`).

### Paso 2 · Renglones completos
- `FullWidthPassword` (Generador): mide el ancho de un carácter monoespaciado con `TextMeasurer`, calcula cuántos caben y parte el texto en esos trozos; el último puede quedar corto. `FullWidthGroups` (clave del 2FA): grupos de 4 con tantos por renglón como quepan, sin partir un grupo. Ambos dibujan con `softWrap = false`, sin guardar nada en estado; copiar usa el valor original (sin saltos ni espacios añadidos). TalkBack lee igual que antes.
- Resultado medido (`renglones-completos.png`): 33 caracteres a 360 dp → 2 renglones llenos (antes 3 o 4); 64 a 412 dp → 4. La clave a 412 dp cabe en 3 grupos por renglón; a 360 dp caben 2 (el ancho que deja la tarjeta con el botón de copiar).

### Paso 3 · Avisos
- **Anfitrión único** (`MessageHostState`, `MessageHost`, `HostedMessage`): se dibuja encima de la barra (12 dp), o encima de la barra de gestos si no hay barra; nunca encima del teclado. Las pantallas lo piden con `HostedMessage` (se ve mientras esa función esté en la composición; su texto cambia en su lugar). Los resultados de una operación pasan por un buzón (`AppMessages`) porque la pantalla que los pidió ya no está.
- **Animación**: entra subiendo 16 dp y apareciendo en 200 ms; sale bajando 8 dp y desvaneciéndose en 150 ms; si llega otro, el actual sale y el nuevo espera a que termine. `liveRegion = Polite` como antes.
- **De los 11 usos de `MessageBar(`: 8 pasaron al anfitrión** (copiado en el detalle, en el Generador y en 2FA, Salud sin conexión, Ajustes, Acerca de, Dispositivos/Actividad y eliminar con «Deshacer»), **1 se eliminó** (el «quedó resuelta» de la lista de conflictos, ver desviaciones) y **2 se quedan en la lista de la Bóveda** (conflictos por resolver y contraseñas que necesitan atención): son **contenido permanente** de la pantalla, no avisos de resultado, y llevan su acción.
- **Feedback nuevo**: crear y guardar desde el Generador «{Nombre} se guardó»; editar «Cambios guardados»; resolver un conflicto «Listo, se conservó tu versión» / «…la otra versión»; activar 2FA «Verificación en dos pasos activada». **Sin conexión** el texto añade «Se subirá cuando haya conexión.».

### Auditoría de acciones (crear, cambiar o borrar)

| Acción | ¿Tenía aviso? | Aviso que queda |
|---|---|---|
| Crear contraseña | No | «{Nombre} se guardó» (+ «Se subirá…» sin conexión) |
| Guardar desde el Generador | No | «{Nombre} se guardó» (misma vía que crear) |
| Editar contraseña | No | «Cambios guardados» (+ «Se subirán…» sin conexión) |
| Eliminar contraseña | Sí | «{Nombre} se eliminó · Deshacer», ahora en el anfitrión y animado |
| Deshacer la eliminación | No | Ninguno: la fila reaparece en la lista; un aviso más sería ruido |
| Resolver un conflicto | Sí («{nombre} quedó resuelta» en la lista) | «Listo, se conservó tu versión» / «…la otra versión» (+ sin conexión). **Reemplaza** al de la lista; ver desviaciones |
| Copiar contraseña o usuario (detalle) | Sí | «Copiada · se borra… en 30 s», hospedado |
| Copiar desde el Generador | Sí | Igual |
| Regenerar contraseña | No | Ninguno: la tarjeta ya cambia (fundido y dado girando) |
| Activar 2FA | No | «Verificación en dos pasos activada» |
| Copiar clave o códigos de 2FA | Sí | «Copiada…», hospedado |
| Regenerar códigos de respaldo | No | Ninguno: la pantalla muestra los códigos nuevos |
| Desactivar 2FA | Sí | «Verificación en dos pasos desactivada» |
| Cambiar contraseña maestra | Sí | «Contraseña cambiada» / «huella desactivada» |
| Cambiar correo | Sí | «Tu correo ahora es …» |
| Cerrar sesión de un dispositivo / de los demás | Sí | «… se cerró» / «Se cerraron las demás» / error |
| Huella (activar/desactivar) | No | Ninguno: el interruptor de la fila ya muestra el estado; si se cancela, vuelve a apagado |
| Bloqueo automático | No | Ninguno: la fila ya muestra el valor nuevo |
| «Novedades y encuestas» | Solo el error | Ninguno de éxito (el interruptor ya lo muestra); el error se conserva |
| Cerrar sesión | No | Ninguno: navega a Iniciar sesión |
| Eliminar cuenta | No | La propia pantalla «Cuenta eliminada» |
| «Revisar ahora» sin conexión | Sí | «Sin conexión. Te mostramos la última revisión.» |

## Revisión de seguridad (`security-reviewer`)
0 altos, 2 medios, 4 bajos. **Corregidos**:
- **M1**: un aviso con el nombre de una entrada podía verse sobre Desbloquear tras bloquear. `MessageHostState.clear()` (vacía y cancela relojes) se llama al bloquear y al eliminar la cuenta; prueba en `MessageHostTest`.
- **M2** (viene de la tarea 23): `clearSavedTabStates` solo limpiaba las 4 raíces. Ahora también `VAULT_LOADING`, `CONFLICTS`, `HEALTH_*`, `DEVICES`, `ACTIVITY`, ignorando las rutas que no estén en un grafo reducido (por eso un `runCatching`).
- **B1**: la barra se veía unos cuadros con la bóveda ya bloqueada. `LockGuardViewModel.unlocked` y `barTab = if (unlocked) … else null`.
- **B2**: `InterceptTabSelection` podía borrar el interceptor de la pantalla que acababa de entrar; ahora `unregister` compara identidad (`TabGuardTest`).
- **B3**: salir del Paso 3 por una pestaña no avisaba que el 2FA quedó activo; nueva acción `SetupAction.LeaveForTab` (vacía los secretos y avisa; `TwoFactorViewModelsTest`).
- **B4**: `AppMessage.args` es `List<String>` («solo nombres»). **No cambia** y queda como riesgo aceptado: la contraseña del Generador va completa en la `contentDescription` de accesibilidad (decisión de la tarea 8; un servicio de accesibilidad malicioso la puede leer y `FLAG_SECURE` no lo impide). Conviene documentarlo en `SecureScreen`.

## Pruebas (salida real)
- Unitarias (`testDebugUnitTest`), `lintDebug` (tras corregir un error propio: `LocalContext` → `LocalResources`) y `assembleRelease`: OK.
- Nuevas, con reloj controlado en el emulador (todas pasan): `HealthMotionTest` (selector que se desliza y cambia de rumbo sin saltos, cambio de contenido en ambos sentidos, escalonado con máximo 6, solo la primera vez, aro 0→valor y valor anterior→nuevo, línea de la gráfica, barras, «Quitar animaciones»), `PersistentTabBarTest`, `MessageHostTest` (9: posición encima de la barra, entrada, salida, reemplazo, `clear()`), `FullWidthTextInstrumentedTest` (16, 33 y 64 caracteres a 360 y 412 dp y con letra al 130 %; ningún renglón salvo el último deja lugar a otro carácter), `BottomNavBarTest` (ahora «la barra no se recrea»). JVM: `FullWidthTextTest`, `TabForRouteTest`, `TabGuardTest`; los VM de editar, conflictos y 2FA verifican qué aviso se pide y su variante sin conexión.
- Lotes de `connectedDebugAndroidTest`: navegación 33/33 (incluye `NavTransitionsTest`/`TabSwitchTimingTest`), actividad 25/25, salud 30/30, ajustes 25/25, bóveda 14/14, `KeyboardAuditTest` 17/17 a 412×891 y a 360×640 dp; `core`: solo fallan 2 de `ClipboardDeviceTest` (**E3**, pendiente del equipo).
- Pruebas de pantallas existentes que hubo que ajustar por el cambio de arquitectura: `GeneratorScreenTest` (encontraba «Generador» por la etiqueta de la pestaña, que ya no vive en la pantalla) y `TwoFactorScreensTest` (la clave ya no se agrupa de dos en dos).

### Incidencias del entorno (no del código)
- El emulador falló con «System has crashed» a mitad de los lotes largos; lo reinicié en frío y repetí los lotes. Aparecen de forma intermitente `Activity never becomes DESTROYED`, `PixelCopy failed` y `Window doesn't have a backing surface` en pruebas ajenas; pasaron al repetir.
- `TwoFactorScreensTest`: en la pasada completa fallaron, de forma distinta cada vez, pruebas que hacen captura (`PixelCopy`, `waitUntil`) con el emulador cargado (carga ~9). `cargandoMuestraGenerandoTuClaveYContinuarDeshabilitado` pasa sola (1/1); la clase completa pasó 14/15 en la última corrida, solo con esa por la captura. `FormErrorsScreenTest` (auth) falló una vez por `Activity never becomes DESTROYED` y es ajena a esta tarea.
- Un primer lint falló por un archivo generado que faltaba tras cancelar una compilación; al repetirlo pasó.

## Revisión cuadro por cuadro (`ai/handoff/capturas/tarea-26/`)
Las pantallas de Salud usan `FLAG_SECURE` (negras en `screenrecord`), así que se capturaron con `Tarea26FramesTest` (reloj de Compose, sin afirmar nada):
- `salud-resumen-primera-vez.png`: el aro cuenta 0 → 34 con el arco a la par, el texto y la fila de «Filtradas» entran escalonados; a los ~700 ms todo en su lugar.
- `salud-progreso-cambio-y-grafica.png`: el fondo del selector cruza a «Progreso» mientras el Resumen sale con fundido y desplazamiento y entra el Progreso; la línea se traza de izquierda a derecha y el punto amarillo final aparece al llegar.
- `salud-datos-nuevos-sin-volver-a-cero.png`: con datos nuevos la gráfica nunca se vacía.
- `salud-selector-se-desliza.png`: el fondo viaja sin rebote.
- `aviso-entra-y-sale.png`: sube y aparece en ~150 ms, se queda y sale con fundido y una caída corta. **Defecto encontrado y corregido en esta revisión**: la primera versión saltaba hacia abajo al salir (el contenedor se encogía de golpe); ahora el contenido va pegado al borde inferior.
- `renglones-completos.png`: 16, 33 y 64 caracteres y la clave a 360 y 412 dp.

## Pendiente manual en el Redmi
- M19/M20 de las tareas 24 y 25 (sensación de la píldora, transiciones, regreso predictivo) y ahora también: la barra al aparecer/ocultarse con el teclado y al bloquear, y los avisos encima de la barra con la barra de gestos. E3 (`ClipboardDeviceTest`) sigue pendiente del equipo.

## Decisiones y desviaciones a revisar
1. **SCR-20 (Figma)**: se quitó el aviso «{nombre} quedó resuelta» de la lista de conflictos y se reemplazó por el de la tarea («Listo, se conservó tu versión/la otra versión»), que dice qué se eligió y también se ve cuando era el último conflicto (la lista se cierra). Si prefieren conservar el de Figma, se restaura.
2. Los avisos fijos de la lista de la Bóveda se quedan inline (contenido, no resultado).
3. El anfitrión se limpia al bloquear; los avisos se piden por `AppMessages` (singleton) o por `HostedMessage` (mientras dure su causa).
4. La barra ya no recibe `onTabSelected` por pantalla; el control de toques es `TabGuard`.
5. En Generador y 2FA el reparto es solo de dibujo; no cambia el estado ni lo que se copia.
6. Dependencia nueva: ninguna.

## Vistas / cambios (cómo llegar)
No hay vistas nuevas. **Barra**: cambia de pestaña en Bóveda, Salud, Generador y Ajustes (la píldora se desliza sobre la misma barra); bloquea la bóveda para verla bajar y desvanecerse. **Salud**: Salud → Resumen/Progreso (el selector y las animaciones, solo la primera vez por sesión; «Revisar ahora» para ver los datos nuevos) y Salud → Progreso → «Ver ranking» (Más fuertes/Más débiles). **Renglones**: Generador con una longitud de 33 o más; Ajustes → Verificación en dos pasos → Paso 1. **Avisos**: guarda una contraseña nueva, edítala, elimínala, copia una, resuelve un conflicto o activa 2FA.
