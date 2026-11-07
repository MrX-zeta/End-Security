# Tarea 26 · Movimiento en Salud, contraseñas que aprovechan el ancho y avisos de guardado

**Estado:** hecha
**Escrita:** 2026-11-06 · sesión de diseño

## Revisión de la tarea 25
Aprobada. Al equipo le gustó la píldora («le da un toque premium»):
- posición en espacio de índice, leída solo en `graphicsLayer`;
- resorte sin rebote que cambia de rumbo sin saltos;
- el color del ícono que sigue a la píldora (desviación aprobada: era la forma correcta);
- el pulso con 80 ms de retraso;
- las hojas de contacto cuadro por cuadro.

`security-reviewer`: 0 altos y 0 medios. Bien corregidos BAJO-2 y BAJO-3.

**Una cosa de arquitectura a corregir aquí (Paso 0):** «cada pantalla dibuja su propia barra». Por eso hace falta `TabBarHandoff`, y la barra vieja se desvanece mientras la nueva aparece. Una barra persistente es más simple y más fluida.

## Criterio de movimiento (igual que en las tareas 24 y 25)
Apple (*Designing Fluid Interfaces*) y Emil Kowalski:
- menos de 300 ms en lo frecuente;
- curva `Motion.EaseOut`;
- resortes sin rebote si no hay un gesto que lance algo;
- interrumpible;
- solo `alpha`/`translation`/`scale` en `graphicsLayer`;
- con «Quitar animaciones» (`rememberReducedMotion()`) no hay movimiento, solo el estado final.

Lo que se ve pocas veces puede darse un pequeño momento de «deleite»; lo que se usa muchas veces, lo mínimo.

**Nuevas constantes en `Motion.kt`, sin repetir números en las pantallas:**
- `EnterRise = 8.dp`
- `Stagger = 30` ms
- `ChartDraw = 600` ms
- `CountUp = 600` ms
- `MessageIn = 200` ms
- `MessageOut = 150` ms

## Paso 0 · Barra inferior persistente
- **Una sola `BottomNavBar` fuera del `NavHost`** (en el `Scaffold` raíz o un `Box` que lo envuelve):
  - se muestra cuando el destino actual es una de las cuatro raíces de pestaña o una pantalla que hoy la lleva (revisa cuáles: en Figma la llevan los flujos de Ajustes);
  - se oculta en el acceso, en Desbloquear, en «Cuenta eliminada» y con el teclado abierto.
- **Al mostrarse u ocultarse:** desliza hacia abajo y se desvanece en 200 ms con `Motion.EaseOut`.
- **Al cambiar de pestaña** la barra **no** se vuelve a crear: la píldora se desliza sobre la misma barra.
- **Quita `TabBarHandoff` / `LocalTabBarHandoff`.** Las pantallas reservan el alto de la barra con `contentPadding` o `WindowInsets` propios, para que el contenido no quede debajo.
- **Pruebas:** la píldora se desliza sin que la barra se desvanezca; la barra se oculta y aparece donde corresponde; las pruebas de `BottomNavBarTest` y `TabNavigationTest` siguen pasando.

## Paso 1 · Salud: selector, tarjetas y gráfica
1. **Selector «Resumen | Progreso» y «Más fuertes | Más débiles»** (`SegmentedSelector`):
   - mismo comportamiento que la píldora de la barra: **un solo fondo seleccionado que se desliza** entre opciones con `spring(dampingRatio = 1f, stiffness = 440f)`, en espacio de índice, leído en `graphicsLayer`;
   - el color del texto sigue al fondo, igual que el ícono de la barra;
   - extrae la lógica común de la barra a un componente o modificador compartido (`SlidingSelectionIndicator`), para no tenerla dos veces.
2. **Cambio de contenido entre «Resumen» y «Progreso»:**
   - fundido cruzado de 150 ms con desplazamiento de 16 dp en el sentido del selector: de izquierda a derecha al ir a «Progreso» y al revés al volver (mismo camino);
   - el de «Más fuertes | Más débiles», igual.
3. **Entrada de las tarjetas** (Resumen: el aro y las cuatro filas; Progreso: la tarjeta de puntos, los indicadores y «Por fuerza»):
   - suben `EnterRise` (8 dp) y aparecen, 200 ms con `Motion.EaseOut`;
   - escalonadas `Stagger` (30 ms) entre una y otra; **máximo 6 elementos escalonados**, el resto entra con el último;
   - **solo la primera vez** que se muestra cada vista en la sesión desbloqueada; al volver a la pestaña o recomponer, ya están en su lugar.
4. **Datos:**
   - **aro de la puntuación:** el arco se dibuja de 0 a su valor y el número cuenta de 0 a «78» a la par, en `CountUp` (600 ms) con `Motion.EaseOut`;
   - **gráfica de 12 semanas:** la línea se traza de izquierda a derecha en `ChartDraw` (600 ms), recortando el trazo con `PathMeasure` o un `clipRect` que avanza; los puntos aparecen cuando la línea llega a ellos;
   - **barras de «Por fuerza»:** crecen desde cero a su ancho en 300 ms, escalonadas 30 ms;
   - **cuándo:** solo al **primer** dibujo con datos de cada vista en la sesión. Si los datos cambian después («Revisar ahora»), el arco, el número y las barras van **del valor anterior al nuevo** en 300 ms, sin volver a cero.
   - Son 600 ms porque Salud se visita pocas veces y es el «momento» de la app. **No bloquean nada:** se puede tocar todo mientras se animan.
5. **TalkBack** lee el valor final desde el principio («78 de 100»), nunca los números intermedios.

## Paso 2 · Contraseñas largas que aprovechan el ancho
**Generador** (tarjeta oscura) y **2FA, paso 1** (la clave): hoy el texto se corta en 3 o 4 renglones dejando huecos, porque Android parte las líneas en los símbolos (`=`, `}`, `$`) como si fueran palabras.
- **Generador:** reparte la contraseña **carácter por carácter, renglones completos**.
  - La fuente es monoespaciada: mide el ancho de un carácter con `rememberTextMeasurer`, calcula cuántos caben por renglón en el ancho disponible y divide el texto en esos trozos (cada uno un renglón).
  - El último renglón puede quedar corto.
  - Funciona al girar la pantalla y con la escala de fuente del sistema (prueba con 1,3).
- **Clave del 2FA:** grupos de 4 separados por espacio, con **tantos grupos por renglón como quepan** (sin partir un grupo). Por ejemplo, «YRHL PYJN Z4BT» en un renglón si caben tres.
- **Importante:**
  - copiar sigue copiando el valor **original**, sin saltos de línea ni espacios añadidos (la clave del 2FA se copia sin espacios, como ahora);
  - el texto mostrado tampoco se guarda en ningún estado nuevo: se calcula al dibujar;
  - TalkBack lee el valor como hoy.
- **Pruebas:** con 16, 33 y 64 caracteres y anchos de 360 y 412 dp, ningún renglón (salvo el último) deja espacio para otro carácter; el portapapeles recibe el valor original.

## Paso 3 · Avisos: posición, animación y los que faltan
1. **Un solo lugar para los avisos (`MessageBar`):**
   - un anfitrión en el `Scaffold` raíz (junto a la barra persistente del Paso 0) que los muestra **encima de la barra inferior** con 12 dp de separación, o encima del borde inferior si no hay barra;
   - nunca encima de la barra ni del teclado;
   - las pantallas piden el aviso a ese anfitrión en lugar de dibujar el suyo. Hoy son 11 usos de `MessageBar(`.
2. **Animación del aviso:**
   - entra subiendo 16 dp y apareciendo en `MessageIn` (200 ms) con `Motion.EaseOut`;
   - sale bajando 8 dp y desvaneciéndose en `MessageOut` (150 ms): la salida es más rápida que la entrada;
   - si llega otro aviso mientras hay uno, el actual sale y el nuevo entra, sin amontonarse;
   - TalkBack lo anuncia (`liveRegion = Polite`), como ahora.
3. **Feedback de las operaciones de la bóveda** (hoy solo eliminar avisa):
   - **crear** → al volver a la lista: «{Nombre} se guardó»;
   - **editar** → al volver al detalle: «Cambios guardados»;
   - **guardar desde el generador** → «{Nombre} se guardó»;
   - **eliminar** → el aviso de siempre con «Deshacer», ahora en el lugar nuevo y animado;
   - **resolver un conflicto** → «Listo, se conservó tu versión» o «… la otra versión», según lo elegido.
   - **Sin conexión:** el texto añade «Se subirá cuando haya conexión», porque la escritura queda pendiente.
4. **Auditoría:** revisa **todas** las acciones que crean, cambian o borran algo y entrega en el reporte una tabla: acción · ¿tenía aviso? · aviso que queda. Incluye bóveda, conflictos, generador, 2FA, contraseña maestra, correo, dispositivos, huella, bloqueo automático, «Novedades y encuestas», copiar al portapapeles y cerrar sesión.
   - Donde falte, agrégalo con el mismo estilo.
   - **Donde no tenga sentido** (por ejemplo, el bloqueo automático, que ya muestra el valor nuevo en la fila), no lo agregues y dilo en la tabla.

## Terminado cuando
- **Paso 0:** la barra persistente, sin `TabBarHandoff`, con sus pruebas.
- **Paso 1:**
  - pruebas con reloj controlado: el selector se desliza y cambia de rumbo sin saltos; las tarjetas entran escalonadas solo la primera vez; el arco y la gráfica se dibujan al primer dibujo y van del valor anterior al nuevo al refrescar; con «Quitar animaciones» todo aparece en su estado final;
  - hojas de contacto cuadro por cuadro en `ai/handoff/capturas/tarea-26/`.
- **Paso 2** con sus pruebas.
- **Paso 3:**
  - pruebas del anfitrión (posición encima de la barra, entrada y salida, reemplazo) y de cada aviso nuevo;
  - la tabla de la auditoría.
- `testDebugUnitTest`, la suite completa de `connectedDebugAndroidTest` (salvo E3, en tandas), lint y `assembleRelease` pasan. Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
