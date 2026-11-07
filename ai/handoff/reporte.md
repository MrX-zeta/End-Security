# Reporte · Tarea 28 · Correcciones visuales (2FA encimado y selector del ranking)

No se tocó `core/crypto`, `core/session`, autenticación ni SQL: no hace falta revisión de seguridad. Sin capturas: las pruebas afirman posiciones, tamaños y líneas.

## Paso 1 · 2FA: el contenido de los pasos se encimaba
**Causa confirmada:** `AnimatedContent` coloca su contenido en un `Box`; `KeyStep` y `CodeStep` emitían varios elementos sueltos en la raíz y se apilaban en la misma posición (el Paso 3 ya iba en un `Column`).

**Arreglo:** `DirectionalContentSwap` envuelve **siempre** el contenido en `Column(Modifier.fillMaxWidth())`; su firma y la de `StepSwap` ahora son `content: @Composable ColumnScope.(Int) -> Unit`, así que ninguna pantalla depende de poner su propio `Column`.

**Revisión de todos los usos** (tareas 26 y 27):
| Uso | ¿Varios hijos en la raíz? | Resultado |
|---|---|---|
| `StepSwap` en 2FA (Pasos 1, 2 y 3) | Sí (Paso 1 y 2) | Corregido por el `Column` del componente |
| `StepSwap` en Cambiar correo | No: cada paso es una pantalla con una sola raíz | Sin cambio; se probó el orden |
| `DirectionalContentSwap` en Resumen/Progreso y en el ranking | No (una raíz) | Sin cambio |
| `LoadingCrossfade` (Salud, Dispositivos, Actividad, clave del 2FA) | No: cada rama tiene una sola raíz (la de la clave ya iba en un `Column`) | Sin cambio |
| `FadeOnChange` (Revisado hoy, valores de Ajustes) | No (un `Text`) | Sin cambio |

**Prueba de diseño** (`StepLayoutTest`, 5 pruebas): en el Paso 1 del 2FA, título → tarjeta de la clave → instrucciones → nota «La clave solo se muestra ahora» quedan en orden vertical (el `bottom` de cada uno ≤ el `top` del siguiente); en el Paso 2, título → casillas → «El código cambia en…»; **a la mitad de la transición** (avanzando y regresando) el paso que entra tampoco se encima consigo mismo; y en Cambiar correo, aviso → correo actual → botón Continuar. Para ello se añadieron etiquetas de prueba en los pasos.

## Paso 2 · Selector del ranking
- **Etiqueta:** «Mejoradas recientemente» → «**Mejoradas**»; bajo el selector, solo con esa opción activa, aparece en pequeño «Mejoradas recientemente: cambiadas en los últimos 30 días y hoy sanas» (se despliega con `Reveal`).
- **Regla de `SegmentedSelector`** (para todos): cada opción ocupa el mismo ancho (`weight(1f)`); texto centrado en una sola línea (`maxLines = 1`, `softWrap = false`); si no cabe, `autoSize` lo achica hasta un mínimo de 12 sp y, si tampoco, termina en «…»; el fondo deslizante sigue ocupando la celda completa.
- **Prueba** (`SegmentedSelectorLayoutTest`, 5 pruebas): con letra 1,0 y 1,3, en «Resumen | Progreso» y «Más débiles | Mejoradas», cada etiqueta queda en **una línea**, el centro de su línea coincide con el centro de su celda (±1 dp) y las dos opciones miden lo mismo; una etiqueta larga («Mejoradas recientemente») con letra 1,3 tampoco se parte ni se va a la izquierda.

## Paso 3 · Pendiente de la tarea 27
`Reveal`/`RevealValue` aplicado a: aviso de huella invalidada en Desbloquear, «Necesitas registrar una huella» en Ajustes, los dos errores de Eliminar cuenta, la lista de requisitos de Cambiar contraseña, el error de guardado de Nueva/editar contraseña (genérico y de conflicto, con sus botones) y el error «no disponible» del conflicto. Prueba: `ErrorRevealScreensTest` (el aviso de Desbloquear aparece, está a la vista pasados 300 ms, y al quitarse sigue un momento mientras se pliega y luego se va); la mecánica de posición y tiempos (200/150 ms, el contenido de abajo se desplaza suave) ya la cubre `ContentMotionTest`.
- **No aplica:** el «Código de tu app» del acceso con 2FA no es un campo dentro de una pantalla sino la ruta `TWO_FACTOR` (ya tiene la transición de navegación). El error completo de «sin conexión» del conflicto reemplaza toda la pantalla y se queda sin animar.
- *Limitación de la prueba:* en Desbloquear no se pudo medir el desplazamiento de lo de abajo (el campo queda anclado y la etiqueta no expone límites), así que ahí solo se afirma presencia/ausencia en el tiempo.

## Pruebas (salida real)
- `testDebugUnitTest`, `lintDebug` y `assembleRelease`: OK. Las dos pruebas de tiempo de `PasswordSimilarityTest` fallaron una vez porque la máquina estaba ocupada (462 ms contra el límite de 300): ahora toman el mejor de varias medidas.
- Instrumentadas: `feature.account` (incluye `StepLayoutTest`), `feature.health` (incluye `SegmentedSelectorLayoutTest`), `ErrorRevealScreensTest` y `core`: pasan, salvo `ClipboardDeviceTest` (E3, pendiente del equipo). `ChangeEmailScreensTest.laEsperaDeHorasMuestraElMensajeGris` falló una vez con «Compose not idle» en la pasada completa y pasó al repetir (25/25 con `HealthMetricsScreenTest`).
- Pruebas existentes ajustadas: `HealthMetricsScreenTest` (el ranking ahora dice «Mejoradas»).

## Pendiente
- Probar en el Redmi el selector con letra grande y los pasos del 2FA.

## Vistas / cambios (cómo llegar)
No hay vistas nuevas. **2FA:** Ajustes → Verificación en dos pasos (Pasos 1 y 2, que ya no se encima). **Selector:** Salud → Progreso → «Ver ranking de fuerza» (Más débiles | Mejoradas). **Errores animados:** Desbloquear con la huella invalidada, Ajustes (huella sin registrar), Eliminar cuenta, Cambiar contraseña y Nueva/editar contraseña (error al guardar).
