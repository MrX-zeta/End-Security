# Tarea 27 · Movimiento dentro de las pantallas y métricas de salud nuevas

**Estado:** hecha
**Escrita:** 2026-11-07 · sesión de diseño

## Revisión de la tarea 26
Aprobada:
- barra persistente con `TabGuard`;
- `SlidingSelectionIndicator` compartido;
- `DirectionalContentSwap`;
- datos que van del valor anterior al nuevo;
- renglones completos sin tocar lo que se copia;
- anfitrión de avisos con buzón y `clear()` al bloquear;
- la tabla de la auditoría.

Correcciones de `security-reviewer` bien hechas (M1, M2 y B1–B3).

**Desviaciones aprobadas:**
- el aviso de conflictos resuelto que dice qué se eligió (reemplaza el de Figma);
- los avisos fijos de la Bóveda en línea;
- la barra sin `onTabSelected`.

**B4** se acepta como riesgo: la contraseña del Generador va en la `contentDescription`. Documéntalo en `SecureScreen` y lo anoto en el grafo.

## Criterio de movimiento
Igual que las tareas 24–26: `Motion.EaseOut`, menos de 300 ms en lo frecuente, `graphicsLayer`, interrumpible y nada con «Quitar animaciones».

## Paso 1 · Salud: las animaciones se repiten al cambiar de vista
El equipo quiere verlas **cada vez que se cambia entre Resumen y Progreso** (y entre «Más fuertes | Más débiles» / «Mejoradas recientemente», Paso 5), no solo la primera vez.
- **Al cambiar con el selector**, la vista que entra repite su animación completa:
  - tarjetas escalonadas;
  - el aro de 0 al valor;
  - la gráfica trazándose;
  - las barras creciendo.
- **Para que no canse**, en las repeticiones `CountUp` y `ChartDraw` bajan a **400 ms** (constante `Motion.ChartReplay`) y la escalera de tarjetas se queda en 30 ms. La primera vez de la sesión siguen en 600 ms.
- **También al entrar a Salud desde otra pestaña** la vista visible repite su animación (400 ms), para que sea coherente con el selector.
- **No se repite:**
  - al recomponer;
  - al volver de una pantalla de detalle de Salud (Filtradas, Repetidas…) con Atrás;
  - con «Revisar ahora»: ahí sigue «del valor anterior al nuevo» en 300 ms.
- **Interrumpible:** si se cambia de vista a mitad, la nueva arranca su animación y la anterior se descarta sin saltos.
- Quita `summaryPlayed`/`progressPlayed` de `rememberSaveable` o cambia su regla.

## Paso 2 · Movimiento dentro de las pantallas (lo que todavía cambia de golpe)
Las **transiciones entre pantallas ya están en todas**: crear contraseña, detalle, editar y cada opción de Ajustes usan el desplazamiento de 60 dp (tarea 25). Lo que el equipo ve «brusco» es lo que cambia **dentro** de una misma pantalla:
1. **Pasos dentro de un flujo:**
   - los 3 pasos de activar la 2FA;
   - correo nuevo → código en Cambiar correo;
   - la verificación de correo y 2FA del acceso.

   Usa `AnimatedContent` con la misma transición de avanzar y regresar de `NavMotion`: 60 dp + fundido, 260/220 ms, al revés al volver. **Mismo camino:** avanzar desde la derecha y regresar desde la izquierda. La barra «Paso N de 3» llena su segmento con `Motion.EaseOut` en 260 ms.
2. **De «cargando» al contenido** (esqueletos de Bóveda, Dispositivos, Actividad, 2FA y Salud):
   - **fundido cruzado de 200 ms** del esqueleto al contenido;
   - **las filas de la lista entran escalonadas:** suben 8 dp y aparecen en 200 ms, 30 ms entre filas, **máximo 8 escalonadas**; las demás aparecen con la octava;
   - solo cuando llegan los datos, no al desplazar la lista.

   Aplica a Dispositivos, Actividad (también las páginas nuevas: sus filas entran con el mismo efecto, sin mover las anteriores), la lista de la Bóveda, conflictos y las listas de Salud.
3. **Cambios dentro de una lista:**
   - **eliminar una contraseña:** la fila se encoge y se desvanece (200 ms);
   - **«Deshacer»:** la fila vuelve a aparecer en su lugar;
   - **cerrar sesión en un dispositivo:** su fila sale igual;
   - **buscar en la Bóveda:** las filas que se quedan se reacomodan con `Modifier.animateItem()` (200 ms);
   - todo con `LazyColumn` y claves estables por id.
4. **Lo que aparece o desaparece dentro de una pantalla:**
   - errores de los campos (tarea 15);
   - la lista de requisitos;
   - el campo «Código de tu app» cuando hay 2FA;
   - los acordeones de los documentos legales;
   - el bloque de salud del detalle.

   Usa `AnimatedVisibility` con `expandVertically` + fundido en 200 ms y `shrinkVertically` + fundido en 150 ms, para que el contenido de abajo **se desplace suave** y no salte.
5. **Elementos que cambian de valor:**
   - el medidor de fuerza: las barras se llenan en 200 ms;
   - el «Revisado hoy a las…» de Salud: fundido de 150 ms al cambiar el texto;
   - el número de dispositivos en Ajustes: fundido de 150 ms.

**Entrega en el reporte una tabla:** pantalla · qué cambiaba de golpe · qué movimiento tiene ahora. Si en algún lugar el movimiento estorba, déjalo sin animar y justifícalo; por ejemplo, el campo de contraseña al escribir: **nunca** animes el texto que se escribe.

## Paso 3 · Índice de salud nuevo (cambia DEC-20)
Lo pidió Diego y lo aprobó el equipo:
- **Contraseña sana:** cumple **las cuatro** condiciones: no filtrada, no repetida **ni parecida** (Paso 4), fuerte (zxcvbn 3–4) y con menos de 365 días desde `passwordChangedAt`.
- **Índice:** `round(100 × sanas / total)`, solo con los elementos legibles. Sin elementos no hay índice (estado vacío, como ahora).
- **Tope por filtradas:** si hay **al menos una filtrada**, el índice se limita al **máximo del rango «En riesgo»**. Hoy ese máximo es **49**, porque «En riesgo» es menos de 50. Escríbelo como **regla**, no como número fijo: una constante derivada de los rangos de los mensajes, para que si cambian los rangos, el tope cambie solo.
- **Los mensajes no cambian:**
  - 90 o más: «Excelente»;
  - 70–89: «Bien, pero puedes mejorar»;
  - 50–69: «Necesita atención»;
  - menos de 50: «En riesgo».
- **Texto nuevo bajo el aro:** «**N de M** contraseñas están sanas». Con filtradas, debajo: «Cambia las filtradas para subir tu índice».
- **Los niveles por elemento** (Crítico / Alto / Medio / Bajo, los triángulos y el aviso de la Bóveda) se quedan como están: sirven para ordenar qué atender primero. Solo cambia el cálculo del número global.
- **Fotos diarias:** guarda también `healthy` (número de sanas) en `health_snapshots`.
  - Sube la versión del formato del JSON cifrado y lee las fotos viejas sin `healthy`, con el índice de entonces.
  - En la gráfica, las semanas viejas usan su `score` guardado. **No recalcules el pasado.**

**Pruebas con casos hechos a mano** (tabla de la sesión de diseño):

| Caso | Índice |
|---|---|
| 10 de 10 sanas | 100 |
| 8 de 10 sanas (2 débiles) | 80 |
| 9 de 10 sanas y 1 filtrada | 49 |
| 6 de 10 sanas (1 filtrada y 3 repetidas) | 49 |
| 5 de 10 sanas (5 antiguas) | 50 |

Más casos para 0 elementos, una sola contraseña filtrada (0), redondeo (2 de 3 = 67) y que una «regular» (zxcvbn 2) no cuenta como sana.

## Paso 4 · Contraseñas parecidas (reutilización disfrazada)
**Hoy** solo se agrupan las idénticas (SHA-256 en memoria). **Agrega las parecidas**, también solo en memoria durante el análisis:
1. **Forma reducida** de cada contraseña:
   - NFKC y minúsculas (`Locale.ROOT`);
   - sustituciones comunes (`@→a`, `4→a`, `3→e`, `1→i`, `!→i`, `0→o`, `$→s`, `5→s`, `7→t`);
   - quitar los dígitos y símbolos **del final**.
2. **Dos contraseñas son parecidas si:**
   - (a) sus formas reducidas son iguales y tienen al menos **4 caracteres**, o
   - (b) la distancia de Levenshtein entre las **originales** es **≤ 2** y las dos miden al menos **8 caracteres**.

   Así `Gato2024!` y `Gato2025!` son parecidas, y `abc` y `abd` no.
3. **Grupos:** las parecidas se agrupan por componentes conectados (unión de conjuntos). Las idénticas siguen siendo su propio caso.
4. **Nivel:** «Parecida» cuenta igual que «Repetida» (nivel **Alto**, no sana).
5. **Pantalla Repetidas:** se llama ahora «Repetidas y parecidas». Cada grupo dice «Misma contraseña en N sitios» o «Contraseñas parecidas en N sitios». En el detalle de un elemento: «Parecida a la de {Nombre}».
6. **Rendimiento:** la comparación por pares es O(n²). Hasta 1 000 elementos debe tardar menos de 300 ms en `Dispatchers.Default`; con más, compara solo dentro de cubetas por forma reducida y por longitud ±2. Mídelo con una prueba de 1 000 contraseñas.
7. **Memoria:** las formas reducidas y las distancias **nunca** se guardan, se registran ni salen del análisis. Se sobrescriben o se descartan como las contraseñas descifradas (tarea 14).

**Pruebas:** `Gato2024!`/`Gato2025!`, `P@ssw0rd`/`password`, `Netflix#2023`/`Netflix#2024`, falsos positivos que **no** deben agruparse (`abc12345`/`xyz12345`, contraseñas de 6 caracteres a distancia 1), grupos transitivos y el tiempo con 1 000.

## Paso 5 · Filtradas, ranking y gráfica
1. **Filtradas:**
   - ya van de más a menos apariciones;
   - agrega el número en cada fila: «Apareció en 52 341 filtraciones», con separador de miles del idioma del teléfono.
2. **Ranking:** «Más fuertes | Más débiles» cambia a **«Más débiles | Mejoradas recientemente»**.
   - **Mejoradas recientemente:** `passwordChangedAt` en los **últimos 30 días** y **hoy sana**, ordenadas de la más reciente a la más antigua;
   - **vacío:** «Todavía no hay contraseñas mejoradas este mes. Cambia una débil y aparecerá aquí.»;
   - «Débiles» del Resumen sigue abriendo «Más débiles».
3. **Gráfica de Progreso:**
   - arriba, la **línea del índice** (como ahora);
   - **debajo**, **barras apiladas por semana** con cuántas contraseñas hay en cada categoría, de la última foto de esa semana:
     - sanas (`success`);
     - antiguas (gris);
     - débiles (amarillo `primary`);
     - repetidas o parecidas (naranja de advertencia; si no existe un token, usa `primary` con más opacidad);
     - filtradas (`danger`).
   - Una contraseña cuenta **solo en su categoría más grave** (filtrada > repetida/parecida > débil > antigua > sana), para que la altura de cada barra sea el total.
   - Leyenda pequeña debajo.
   - Las semanas con fotos viejas sin el desglose completo muestran solo el total en gris.
   - **Animación:** las barras crecen desde abajo, escalonadas 30 ms, con las reglas del Paso 1.
   - **TalkBack:** cada semana se lee como «Semana del 2 de noviembre: 15 sanas, 2 débiles, 1 filtrada».
   - No hay Figma para las barras: usa el estilo de la gráfica actual (mismos márgenes, ejes y tipografía).

## No entra
El aviso de sitios que admiten 2FA (2fa.directory) queda como **trabajo futuro**.

## Capturas: no hacen falta
El equipo revisa la sensación en su teléfono.
- **No tomes capturas**, ni hojas de contacto, ni grabaciones con `screenrecord`, ni pruebas que solo guarden imágenes (como `Tarea26FramesTest`).
- Comprueba el movimiento con **pruebas que afirman valores** con el reloj de Compose controlado: posición, alfa, escala, orden y tiempos.
- **No uses `captureToImage`** en las pruebas nuevas.
- Si una prueba existente falla solo por `PixelCopy` o una captura, dilo en el reporte y sigue.

## Terminado cuando
- **Pasos 1 y 2:**
  - pruebas con reloj controlado: Salud repite al cambiar de vista y al entrar a la pestaña, y no al volver de un detalle; pasos de los flujos con su dirección; listas escalonadas solo al llegar los datos; filas que salen y vuelven;
  - la tabla del Paso 2.
- **Pasos 3–5:** todas las pruebas descritas y `HealthAnalyzerTest` actualizado.
- `security-reviewer` revisa el Paso 4 (contraseñas en memoria). **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, la suite completa de `connectedDebugAndroidTest` (salvo E3, en tandas), lint y `assembleRelease` pasan. Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
