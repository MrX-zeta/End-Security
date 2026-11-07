# Reporte · Tarea 27 · Movimiento dentro de las pantallas y métricas de salud nuevas

**REQUIERE REVISIÓN HUMANA**: contraseñas en memoria (Paso 4: formas reducidas y comparación), formato cifrado de las fotos diarias (versión 2) y el índice de salud (DEC-20). Revisó `security-reviewer` (abajo).

## Qué se hizo

### Paso 1 · Salud repite sus animaciones
- **Cada vez que se entra a una vista** (con el selector Resumen/Progreso o al volver a la pestaña Salud) se repite la animación completa: tarjetas escalonadas, aro de 0 al valor, línea de la gráfica y barras. **No se repite** al recomponer, ni al volver con Atrás de un detalle (Filtradas, Repetidas…), ni con «Revisar ahora» (ahí sigue «del valor anterior al nuevo» en 300 ms).
- **Duración:** la primera vez de la sesión 600 ms (`CountUp`/`ChartDraw`); las repeticiones 400 ms (`Motion.ChartReplay`); la escalera de tarjetas sigue en 30 ms.
- Se quitaron `summaryPlayed`/`progressPlayed` de `rememberSaveable`. Ahora lo decide `HealthEntrance`, un objeto del ViewModel (`EntranceMode.First / Replay / None`); la pantalla avisa con `leavingForDetail()` antes de abrir un detalle. Vive mientras la bóveda esté abierta y se descarta al bloquear.
- Interrumpible: si se cambia de vista a mitad, la nueva arranca y la anterior se descarta con su fundido (`DirectionalContentSwap`).
- *Limitación:* al girar el teléfono la actividad se recrea y la animación se repite una vez (no se guarda en estado persistente a propósito).

### Paso 2 · Movimiento dentro de las pantallas
Primitivas nuevas en `core/designsystem/component/ContentMotion.kt`: `Reveal`/`RevealValue` (se despliega con fundido en 200 ms y se pliega en 150 ms), `LoadingCrossfade` (200 ms), `StepSwap` (60 dp + fundido, 260/220 ms, mismo camino al volver), `FadeOnChange` (150 ms), `rememberArrivalWindow` + `staggeredEnter(maxStaggered = 8)` (filas al llegar los datos) y `rememberLeavingList` (filas que salen). Constantes en `Motion.kt`.

| Pantalla | Qué cambiaba de golpe | Qué movimiento tiene ahora |
|---|---|---|
| 2FA, Paso 1→2→3 | El contenido de cada paso aparecía de golpe | `StepSwap`: avanzar entra desde la derecha y regresar desde la izquierda, 60 dp + fundido, 260/220 ms |
| 2FA, barra «Paso N de 3» | Cambiaba de color de golpe | Cada tramo se llena de izquierda a derecha en 260 ms `EaseOut` (y se vacía igual al regresar) |
| 2FA Paso 1, la clave | El esqueleto saltaba a la clave | `LoadingCrossfade` de 200 ms |
| Cambiar correo: correo nuevo → código | Se cambiaba la pantalla completa | `StepSwap` (misma transición) |
| Registro y verificación de correo / 2FA del acceso | — | Son **rutas de navegación**: ya tienen la transición de `NavMotion` (tarea 24); no se añadió nada dentro |
| Salud: «Revisando…» → contenido | El esqueleto saltaba al contenido | `LoadingCrossfade` de 200 ms |
| Salud: «Revisado hoy a las…» | Cambiaba de golpe | `FadeOnChange` de 150 ms |
| Dispositivos: esqueleto → lista | Saltaba | `LoadingCrossfade` 200 ms; filas escalonadas (máx. 8) solo al llegar los datos |
| Dispositivos: cerrar sesión | La fila desaparecía de golpe | La fila se encoge y desvanece (200 ms, `rememberLeavingList`) |
| Actividad: esqueleto → lista y páginas nuevas | Saltaba / aparecían filas de golpe | `LoadingCrossfade` 200 ms; filas escalonadas al llegar los datos y con cada página nueva, sin mover las anteriores |
| Bóveda: lista | Las filas saltaban al eliminar, deshacer o buscar | `animateItem` (200 ms, claves estables por id): la fila sale con fundido, vuelve en su lugar con «Deshacer» y al buscar las demás se reacomodan; llegada escalonada (máx. 8) |
| Conflictos y listas de Salud (ranking, filtradas, repetidas, antiguas) | Las filas aparecían de golpe | Entrada escalonada (máx. 8) solo al llegar |
| Errores bajo los campos, error del código, términos del registro, requisitos de la contraseña (registro) y errores de Cambiar contraseña | Aparecían y empujaban lo de abajo de golpe | `Reveal`: se despliegan con fundido (200 ms) y se pliegan (150 ms); lo de abajo se desplaza suave |
| Documentos legales (acordeones) | Abrían y cerraban de golpe | `Reveal`: se despliegan 200 ms y se pliegan 150 ms |
| Detalle de una contraseña: bloque de salud | Aparecía de golpe al llegar el análisis | `RevealValue` |
| Medidor de fuerza | Las barras cambiaban de color de golpe | `animateColorAsState` de 200 ms por barra |
| Ajustes: número de dispositivos (y valores de fila) | Cambiaba de golpe | `FadeOnChange` de 150 ms |
| **Campo de contraseña y todo texto que se escribe** | — | **Sin animar, a propósito:** el texto que se escribe nunca se anima |

**No animado (a justificar o pendiente):**
- *Sin animar por decisión:* el campo de contraseña y los campos de texto al escribir; la hoja «¿Ya guardaste tus códigos?» y las hojas modales (ya tienen la animación del sistema).
- *Pendiente, no hecho en esta tarea:* los errores de campo de Desbloquear, Ajustes (eliminar cuenta/correo) y Eliminar cuenta, y los de la Bóveda al guardar (`InlineErrorMessage` de Nueva/editar contraseña y del conflicto) siguen apareciendo de golpe; el campo «Código de tu app» del acceso con 2FA; la lista de requisitos de Cambiar contraseña. Los patrones ya existen (`Reveal`/`RevealValue`); falta aplicarlos en esos sitios.
- *Sin prueba automática:* el medidor de fuerza (leer su color exigiría una captura) y el esqueleto de la Bóveda.

### Paso 3 · Índice de salud nuevo (cambia DEC-20)
- **Sana** = no filtrada, no repetida ni parecida, fuerte (zxcvbn 3–4) y de menos de 365 días. **Índice** = `round(100 × sanas / total)` sobre los elementos legibles; sin elementos, estado vacío.
- **Tope por filtradas:** con al menos una, el índice no pasa de `HealthMessage.AT_RISK_MAX`, que se deriva de los rangos de los mensajes (`ATTENTION_MIN − 1`, hoy 49). Los mensajes no cambiaron; los niveles por elemento tampoco (solo ordenan qué atender). `RiskLevel.weight` ya no existe.
- **Texto bajo el aro:** «N de M contraseñas están sanas» y, con filtradas, «Cambia las filtradas para subir tu índice».
- **Fotos diarias:** `HealthTotals` guarda `healthy` y el desglose por categoría (`HealthMix`, cada contraseña solo en su categoría más grave). **Versión 2** del JSON cifrado; las de la versión 1 se siguen leyendo con su `score` de entonces y su AAD de la versión 1 (sin recalcular el pasado). Una versión desconocida se salta.
- **Pruebas a mano** (`HealthAnalyzerTest`): 10/10→100; 8/10→80; 9 sanas + 1 filtrada→49; 6 sanas (1 filtrada y 3 repetidas)→49; 5 sanas (5 antiguas)→50; 0 elementos; una filtrada sola→0; 2 de 3→67; una «regular» no es sana; pendiente de revisar no cuenta como filtrada; el tope sale de los rangos; el desglose suma el total.

### Paso 4 · Contraseñas parecidas
- `PasswordSimilarity`: forma reducida (NFKC, minúsculas `Locale.ROOT`, **recorta** dígitos y símbolos del final **y luego** sustituye `@4→a 3→e 1!→i 0→o $5→s 7→t`) y regla (a) formas iguales de ≥ 4, o (b) Levenshtein ≤ 2 entre las originales de ≥ 8. Grupos por unión de conjuntos; las idénticas siguen siendo su caso (`ReuseKind.Identical`/`Similar`). «Parecida» cuenta como «Repetida»: nivel Alto, no sana.
- **UI:** «Repetidas y parecidas»; cada grupo dice «Misma contraseña en N sitios» o «Contraseñas parecidas en N sitios»; el detalle dice «Parecida a la de {Nombre}» (solo el nombre).
- **Rendimiento:** hasta 1 000 elementos todos contra todos: < 300 ms (probado). Con más, solo dentro de cubetas por el comienzo de la forma reducida y largo ±2, con ventana de 50 vecinos y un presupuesto global de 2 millones de comparaciones.
- **Aclaración de criterio:** `abcde1`/`abcde2` sí se agrupan (formas reducidas iguales de 5); el falso positivo de «6 caracteres a distancia 1» se prueba con `ab12cd`/`ab12ce`. Con más de 1 000 elementos, un par que difiera en los 3 primeros caracteres de su forma reducida solo se detecta por la regla (a).
- **Pruebas:** `Gato2024!`/`Gato2025!`, `P@ssw0rd`/`password`, `Netflix#2023`/`Netflix#2024`, `abc`/`abd`, `abc12345`/`xyz12345`, 6 caracteres, grupos transitivos, idénticas vs. parecidas con su `similarTo`, que el resultado no imprima contraseñas, 1 000 en < 300 ms, 4 000 con la misma raíz, textos enormes.
- **Memoria:** las formas reducidas son `CharArray` que se sobrescriben en un `finally`; las distancias no salen del análisis.

### Paso 5 · Filtradas, ranking y gráfica
- **Filtradas:** de más a menos apariciones y cada fila dice «Apareció en 52 341 filtraciones» (separador del idioma del teléfono).
- **Ranking:** «Más débiles | Mejoradas recientemente». *Mejoradas* = `passwordChangedAt` en los últimos 30 días y hoy sana, de la más reciente a la más antigua; vacío: «Todavía no hay contraseñas mejoradas este mes. Cambia una débil y aparecerá aquí.». «Débiles» del Resumen abre «Más débiles» (y «Ver ranking de fuerza» también).
- **Gráfica de Progreso:** la línea del índice y, debajo, **barras apiladas por semana** (sanas `success`, antiguas gris, débiles `primary`, repetidas o parecidas `primary` más tenue —no existe un token naranja—, filtradas `error`), cada contraseña solo en su categoría más grave (los «regulares» cuentan como débiles). Las semanas con fotos viejas muestran solo el total, en gris. Crecen desde abajo escalonadas 30 ms; leyenda debajo. TalkBack: «Semana del 2 de noviembre: 15 sanas, 2 débiles, 1 filtrada».

## Revisión de seguridad (`security-reviewer`)
0 altos, 1 medio, 4 bajos.
- **M1 (corregido):** una importación masiva de contraseñas que empiezan igual convertía una cubeta en O(m²). Ahora ventana de 50 vecinos por contraseña, presupuesto global de 2 millones y tope de 256 caracteres para la regla de distancia; prueba con 4 000 con la misma raíz.
- **B1:** los `String` intermedios de la normalización no se pueden sobrescribir → documentado en la clase (la contraseña original ya es un `String` en `AnalysisItem`).
- **B2 (corregido):** la lista de formas reducidas se crea antes del `try` para que el `finally` las borre aunque algo falle a medias.
- **B3 (previo, sin cambio):** el SHA-256 en hexadecimal queda como `String` local al análisis; aceptado.
- **B4 de la tarea 26 (riesgo aceptado):** documentado en `SecureScreen` (la contraseña del Generador va completa en la `contentDescription` de accesibilidad).
- Verificado sin problema: ningún resultado conserva formas reducidas, listas de contraseñas ni distancias; los `toString` no filtran; el AAD de la foto sigue ligado a la versión real de la fila; solo se guardan conteos (INV-29).

## Pruebas (salida real)
- `testDebugUnitTest`, `lintDebug` y `assembleRelease`: OK.
- Nuevas **sin capturas**: `HealthAnalyzerTest` (índice), `PasswordSimilarityTest`, `SnapshotStoreVersionTest` (v1 y v2), `HealthEntranceTest`, `HealthViewModelTest` (Mejoradas y Más débiles), `ContentMotionTest` (14: pasos con su dirección, fundido del esqueleto, `Reveal`, `FadeOnChange`, escalonado con máximo 8 y solo al llegar, `animateItem` al eliminar, fila que sale), `HealthMotionTest` (repite al cambiar de vista y la repetición es más corta, no repite al volver de un detalle, del valor anterior al nuevo) y `HealthMetricsScreenTest` (texto de sanas, parecidas, filtradas con miles, ranking, TalkBack por semana). Se retiraron dos pruebas de la tarea 26 que medían píxeles de la gráfica (`captureToImage`); su lógica queda cubierta por pruebas de valores de `rememberAnimatedFloat`/`rememberMorphingValues`.
- Lotes de `connectedDebugAndroidTest`: navegación 33/33, `core` 100/100, actividad 25/25, auth 22/22, salud 39/39, ajustes 25/25, bóveda 14/14, `KeyboardAuditTest` 17/17 a 412×891 y a 360×640 dp.
- Cuenta 58/58 (tras reiniciar el emulador; antes la corrida fue interrumpida por «System has crashed»).
- Pruebas de pantalla existentes ajustadas: `HealthScreensTest` (la fila ahora dice «La misma o muy parecidas…»; el ranking arranca en «Más débiles»), `ConflictScreensTest` y la clave del 2FA (tarea 26).

### Incidencias del entorno (no del código)
- El emulador se cayó («System has crashed») durante las corridas largas y dejó fallos en cascada; lo reinicié en frío y repetí los lotes. Tras el reinicio hubo que dejar el teclado listo (`ime set …`) para que `KeyboardAuditTest` lo viera, y reinstalar las apps (`WorkManager is not initialized`).
- Aparecen de forma intermitente `Activity never becomes DESTROYED`, `PixelCopy` y timeouts de captura en pruebas ajenas (`LegalScreensTest`, `DeleteAccountScreensTest`, `ChangeEmailScreensTest`); pasan al repetir.

## Decisiones y desviaciones a revisar
1. «Repetidas» del Resumen pasa a «Repetidas y parecidas» (su pista: «La misma o muy parecidas en varios sitios»).
2. Los «regulares» (zxcvbn 2) cuentan como «débiles» en las barras por semana y no son sanos.
3. Las repeticiones de Salud no se guardan entre giros de pantalla (ver Paso 1).
4. Dependencia nueva: ninguna.

## Pendiente
- Aplicar `Reveal`/`RevealValue` en los errores de campo que quedaron sin animar (ver Paso 2) y probarlos.
- Probar en el Redmi la sensación de las animaciones nuevas (el equipo la revisa en su teléfono; sin capturas).

## Vistas / cambios (cómo llegar)
No hay vistas nuevas. **Salud:** cambia entre Resumen y Progreso (las animaciones se repiten), «Débiles» abre «Más débiles | Mejoradas recientemente», «Repetidas y parecidas», «Filtradas» con el número de apariciones, y en Progreso las barras por semana. **Flujos:** Ajustes → Verificación en dos pasos (pasos que se deslizan) y Ajustes → Correo. **Listas:** Bóveda (eliminar, «Deshacer», buscar), Ajustes → Dispositivos (cerrar sesión), Ajustes → Actividad.
