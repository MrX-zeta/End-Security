# Tarea 28 · Correcciones visuales (2FA encimado y selector del ranking)

**Estado:** hecha
**Escrita:** 2026-11-07 · sesión de diseño

## Revisión de la tarea 27
Aprobada. Al equipo le gustaron las animaciones («quedaron como esperaba»):
- el índice de sanas con el tope derivado de los rangos y los casos a mano;
- las parecidas, con 1 000 contraseñas en menos de 300 ms;
- las fotos v2 que leen las viejas;
- las barras por semana;
- `security-reviewer` sin hallazgos altos.

Aprobadas tus desviaciones: «Repetidas y parecidas», las regulares contadas como débiles en las barras y que las repeticiones no sobrevivan al giro.

Lo probaron en el Redmi y hay **dos defectos visuales**, más lo que dejaste pendiente. **Es una tarea corta.** Sin capturas (como en la tarea 27): solo pruebas que afirman posiciones y tamaños.

## Paso 1 · 2FA: el contenido de los pasos se encima
**Qué se ve:**
- **Paso 1:** el título «Copia tu clave», la tarjeta oscura con la clave y la lista de instrucciones quedan **uno encima del otro**. La nota «La clave solo se muestra ahora…» sale arriba, tapando el texto.
- **Paso 2:** las seis casillas del código quedan **encima del título** «Escribe el código».

**Causa:**
- `StepSwap` → `DirectionalContentSwap` usa `AnimatedContent`, cuyo contenido se coloca en un **`Box`**;
- `KeyStep` y `CodeStep` emiten **varios elementos sueltos en la raíz** (título, tarjeta, lista…) pensados para un `Column` padre, así que dentro del `Box` se apilan en la misma posición;
- el paso 3 funciona porque su contenido ya va en un `Column`.

**Arreglo:**
- **en `DirectionalContentSwap`:** envuelve **siempre** el contenido en un `Column(Modifier.fillMaxWidth())`, para que cualquier pantalla que lo use quede bien sin depender de cada llamada. Cambia la firma a `content: @Composable ColumnScope.(Int) -> Unit`;
- **revisa todos los usos:** `StepSwap` en 2FA y en Cambiar correo, el cambio de Resumen/Progreso, el ranking y cualquier otro `AnimatedContent`/`Crossfade` agregado en las tareas 26 y 27. Busca el mismo patrón (varios hijos en la raíz) y corrígelo;
- **prueba de diseño:** en cada paso de 2FA y de Cambiar correo, los elementos principales (título, tarjeta o casillas, lista) están **en orden vertical sin solaparse**. Compara los límites con `onNodeWithTag(...).getBoundsInRoot()`: el `bottom` de cada uno debe ser ≤ el `top` del siguiente. También a la mitad de la transición: el que entra no se encima consigo mismo.

## Paso 2 · Selector del ranking: «Mejoradas recientemente» se parte y queda a la izquierda
En `SegmentedSelector`, el texto largo se va a dos renglones, pegado a la izquierda, mientras «Más débiles» queda en uno.
- **Etiqueta más corta:** «**Mejoradas**». El título de la pantalla, «Fuerza de tus contraseñas», y el texto vacío ya explican qué es. Si quieres, agrega bajo el selector, en pequeño, «Mejoradas recientemente: cambiadas en los últimos 30 días y hoy sanas» solo cuando esa opción está activa.
- **Regla para todos los selectores** (`SegmentedSelector`):
  - cada opción ocupa **el mismo ancho** (`weight(1f)`);
  - texto **centrado** (horizontal y vertical) y en **una sola línea** (`maxLines = 1`, `softWrap = false`);
  - si aun así no cabe (fuente grande), se reduce el tamaño del texto hasta un mínimo de 12 sp en lugar de partirse; y si tampoco, `Ellipsis`;
  - el fondo deslizante sigue ocupando la celda completa.
- **Prueba:** con escala de fuente 1,0 y 1,3, cada etiqueta queda en una línea y su centro horizontal coincide con el centro de su celda (±1 dp), en «Resumen | Progreso» y en «Más débiles | Mejoradas».

## Paso 3 · Lo que quedó pendiente de la tarea 27
Aplica `Reveal` / `RevealValue` a los errores de campo que quedaron sin animar (los enumeraste en el Paso 2 del reporte), con su prueba.

## Terminado cuando
- Pasos 1–3 con sus pruebas (sin capturas).
- `testDebugUnitTest`, lint, `assembleRelease` y las pruebas instrumentadas de `feature.account`, `feature.health` y del sistema de diseño pasan. La suite completa solo si tocaste algo fuera de eso.
- Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
