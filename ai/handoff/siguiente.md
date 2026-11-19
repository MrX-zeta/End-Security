# Tarea 35 · Veces en filtraciones: texto correcto y tope de 10 000

**Estado:** hecha
**Escrita:** 2026-11-18 · sesión de diseño

## Revisión de la tarea 34
Aprobada. Probado en el teléfono: el aviso temporal y la sincronización sin conexión funcionan como se esperaba.

**Ramas:** solo la app, `integracion/backend` (PR #2). No toques el backend. Sin merge.
**Reglas:**
- trabaja en la carpeta original de la app;
- sin capturas;
- no leas `.env*` ni llaves;
- ponytail en `full`.

## Problema visto en el teléfono
En Salud → Filtradas, la fila de `admin` muestra solo «Apareció en 42,267,808» y se corta el resto.
- `VaultLikeRow` (`feature/health/ui/HealthDetailScreens.kt`) pone el subtítulo con `maxLines = 1` y sin `overflow`. Con un número largo, «filtraciones» queda fuera sin aviso.
- **El texto es incorrecto.** El número de HIBP (`breachCount`) es cuántas veces aparece la contraseña en las bases filtradas, no cuántas filtraciones hubo. Hoy dice «Apareció en N filtraciones».

## Decisión (aprobada por la persona)
**Texto:** «Apareció N veces en filtraciones».
- Con N ≥ 10 000: «Apareció más de 10 000 veces en filtraciones», sin la cifra exacta.
- Por debajo, la cifra exacta con separador de miles del idioma (`NumberFormat.getIntegerInstance(locale)`, como ya hace la lista).
- Con plurales: N = 1 → «Apareció 1 vez en filtraciones».

**Una sola función** decide el texto (tope, formato y plural), y la usan los dos lugares donde sale el número:
1. la lista de Salud → Filtradas (`health_breached_seen`);
2. el detalle de la contraseña (`health_detail_breached_body` en `ItemDetailScreen.kt`). Hoy usa `%1$d` sin separador: sale `42267808` pegado.

**Strings:**
- reemplaza esos dos `plurals` por unos que digan «vez»/«veces»;
- agrega uno para el tope;
- el `10 000` también va formateado con el idioma, no escrito a mano.

**Que no se corte en silencio:** en `VaultLikeRow`, pon `overflow = TextOverflow.Ellipsis` al subtítulo (y al nombre, si no lo tiene). El texto nuevo debe caber en una línea a 360 dp de ancho con el tamaño de letra normal. Si no cabe, permite 2 líneas en el subtítulo; no lo acortes.

**Grafo:** anota la regla en el nodo de la pantalla Filtradas (`SCR-*`) y en DEC-21 (HIBP). Sube la versión.

## Pruebas (unitarias)
De la función, con locale `es-MX`:
- 1 → «1 vez»;
- 2 → «2 veces»;
- 9 999 → «9,999 veces»;
- 10 000 → «más de 10,000 veces»;
- 42 267 808 → «más de 10,000 veces».

Revisa también que los dos lugares la usan (por ejemplo con una prueba del ViewModel o del texto que recibe la UI, lo que ya use el proyecto).

## Cierre
1. `./gradlew testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug assembleProdDebug`, con el resultado real.
2. `/ponytail-review`.
3. Commits en `integracion/backend` y push.
4. Si el teléfono está conectado (`adb -s YTVSGUPBVGLBNVNJ shell true`):
   - `./gradlew :app:installProdDebug`;
   - `adb -s YTVSGUPBVGLBNVNJ reverse tcp:3000 tcp:3000`.
   Si no está, anótalo.
5. Reporte en `ai/handoff/reporte.md`, estado `hecha`, commit y **detente**.
