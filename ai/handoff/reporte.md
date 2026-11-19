# Reporte · Tarea 35

**Estado:** hecha, salvo la instalación (el teléfono no estaba conectado).
**Rama:** app `integracion/backend` (PR MrX-zeta/End-Security#2). Backend sin tocar. Sin merge.

## Commits
- `8cc8b86`: texto, función única, pruebas y grafo.
- Este: reporte y `siguiente.md` hecha.

## Qué cambió
- **Una sola regla:** `BreachTimes.of(count, locale)` (`feature/health/domain/BreachCount.kt`, sin Android) decide el tope, el formato y el plural. `breachSeenText` (`feature/health/ui/BreachText.kt`) la convierte en texto y la usan **los dos lugares**: la fila de Salud → Filtradas y el detalle de la contraseña (`ItemDetailScreen`), que antes mostraba `42267808` sin separador.
- **Texto:** «Apareció 1 vez en filtraciones», «Apareció 2 veces en filtraciones», «Apareció 9,999 veces…» y, desde 10 000, «Apareció más de 10,000 veces en filtraciones» sin la cifra exacta. El separador y el `10,000` salen de `NumberFormat` del idioma, no escritos a mano.
- **Strings:** los dos `plurals` ahora dicen vez/veces; nuevo `health_breached_seen_capped`.
- **Sin cortar en silencio** (`VaultLikeRow`): nombre con `Ellipsis`; subtítulo hasta 2 líneas con `Ellipsis`. No medí el ancho a 360 dp (sin capturas ni dispositivo): el texto largo puede ocupar 2 líneas, y esa es la salvaguarda.
- **Grafo 1.12.0:** regla anotada en SCR-26 (Filtradas) y DEC-21.

## Pruebas y resultados
- `BreachTimesTest` (4): 1 → vez, 2 → veces, 9 999 → «9,999» sin tope, 10 000, 42 267 808 e `Int.MAX_VALUE` → «más de 10,000», y el separador sigue al idioma (alemán «9.999»). Pedías verificar el texto final con `es-MX`: lo que prueba la regla es cantidad, tope y cifra; las palabras vez/veces las elige el recurso `plurals`, que no corre en pruebas JVM.
- Que los dos lugares usan la función: ambos llaman a `breachSeenText`; ya no hay otra ruta (`grep` sin más usos de los plurals).
- `androidTest` de `HealthMetricsScreenTest` actualizada al texto nuevo; solo compilada, no corrida.
- `testProdDebugUnitTest` **954/0**, `testDemoDebugUnitTest` **1086/0**, `lintProdDebug` **0 errores** (31 avisos), `assembleProdDebug` bien. Push hecho.

## `/ponytail-review` (inline)
Sin hallazgos. Notas: `breachCount` ≥ 10 000 ocurre con contraseñas comunes (HIBP lo reporta tal cual); el texto es el mismo en Filtradas y en el detalle, solo que el detalle sigue usando su propio `plurals`.

## Instalación
No instalé: `adb -s YTVSGUPBVGLBNVNJ shell true` dio «device not found». Cuando lo conectes:
```powershell
./gradlew :app:installProdDebug
adb -s YTVSGUPBVGLBNVNJ reverse tcp:3000 tcp:3000
```

## Pendiente
Instalar y mirar la fila de `admin` en Salud → Filtradas; correr las instrumentadas en un dispositivo.
