# Tarea 33 · Ajustes tras las pruebas en el teléfono, endurecimiento del backend y grafos

**Estado:** hecha
**Escrita:** 2026-11-18 · sesión de diseño

## Revisión de la tarea 32
Aprobada. Backend e integración probados por la persona en el teléfono con el backend local: registro, verificación, desbloqueo, contraseñas, 2FA, cambio de contraseña y borrado funcionan.
Decisiones de la revisión:
- **BR-22** (bloqueo por cuenta que un tercero puede provocar): riesgo aceptado.
- **Cambio de correo con Resend** (se puede inferir por tiempo si el correo nuevo existe): riesgo aceptado hasta tener dominio.
- `docs/bd/end_security_db.jpg`: **no lo versiones** y no lo toques.

**Ramas (igual que la tarea 32):**
- App: `integracion/backend`. Backend: `integracion/app`.
- Los commits nuevos van a esas ramas y a sus PR (#2 app, #1 backend). Nada en `develop`, `development` ni `main`. Tú no haces merge.
- El `siguiente.md` y el `reporte.md` de esta tarea los commiteas en `integracion/backend`.
- Sin capturas de pantalla. No leas `.env*`, `keystore.properties` ni claves.
- ponytail en `full`; no te detengas entre pasos salvo bloqueo real.

---

## Paso 0 · Estado del árbol (app)
La carpeta de la app tuvo un cambio de rama interrumpido: varios archivos de `app/` aparecían distintos de `HEAD`, como si fueran de una versión anterior de la rama (BR-10 y BR-20 deshechos).
- Comprueba que `HEAD` es `integracion/backend` y que `git status` no muestra más que `docs/bd/end_security_db.jpg` y los archivos de esta tarea.
- Si aparecen otros, restáuralos con `git restore -- <archivos>`; no hay trabajo de la persona en ellos.
- Anota en el reporte qué encontraste.

## Paso 1 · «Más débiles» solo por fuerza (app)
**Error visto en el teléfono:** una contraseña `admin123` aparece en «Más débiles»; se cambia por una robusta y sigue ahí con «muy fuerte», y además aparece en «Mejoradas».

**Causa:** `HealthUiState.rankingFor(RankingTab.Weakest)` en `feature/health/ui/HealthViewModel.kt` ordena todos los elementos por fuerza y toma 10, sin filtrar. Con una bóveda chica, las fuertes también entran.

**Decisión (la persona la aprobó):** «Más débiles» se guía por el título, solo por la fuerza.
- Excluye los de `StrengthClass.Strong` antes de ordenar y tomar los 10.
- Las repetidas o filtradas no entran por eso: ya tienen sus pantallas.
- Estado vacío para la pestaña «Más débiles», con texto nuevo en `strings.xml`: `health_ranking_weakest_empty` = «No tienes contraseñas débiles. Buen trabajo.». Reutiliza el componente del estado vacío de «Mejoradas».
- Anota la regla en el nodo de la pantalla del ranking (`SCR-*`) del grafo.

**Pruebas** (unitarias del ViewModel o de `HealthUiState`, sin dispositivo):
1. Tres elementos (débil, regular, fuerte): la fuerte no sale en «Más débiles».
2. El flujo de la persona: `admin` / `admin123` → está en «Más débiles» y no en «Mejoradas». Se cambia por una contraseña fuerte → ya no está en «Más débiles», sí en «Mejoradas», y «Más débiles» queda vacío.
3. Con 12 débiles, salen solo 10.

## Paso 2 · Backend: dos hallazgos de la auditoría (`integracion/app`)
Son los dos que la persona quiere cerrar antes de desplegar. **REQUIERE REVISIÓN HUMANA** (auth y SQL).
Escribe el plan en el reporte antes del código (sin esperar aprobación) y elige lo más simple que cumpla.

**a) Reenvío del código de registro sin tope.** Quien conozca un correo puede pedir códigos sin fin y llenarle la bandeja. Hoy solo hay una espera entre reenvíos.
- Agrega un tope por correo (por ejemplo 5 envíos por hora y 10 por día) contado en la BD, como ya se hace con el cambio de correo.
- Al pasarse: 429 con `retryAfterSeconds`, igual que DEC-63.
- La respuesta no debe revelar si el correo existe.

**b) Cuentas sin verificar que no vencen.** Un registro sin verificar aparta el correo para siempre.
- Define un plazo (por ejemplo 24 h). Pasado ese plazo, un registro nuevo con el mismo correo reemplaza a la cuenta sin verificar y sin bóveda.
- El reemplazo respeta la cadena de auditoría y las FK, igual que `deleteAccount` (anonimizar, no borrar la fila).
- Una cuenta ya verificada nunca se toca.
- Si ves una forma más sencilla (por ejemplo, purga periódica), explícala en el plan y elige una.

**Pruebas:** Jest para el tope (por correo y la respuesta 429) y para el reemplazo (vencida sí, vigente no, verificada nunca). Añade los casos a `scripts/e2e/flujo-completo.mjs` si caben. Reporta `npm run build`, `npm test` y lint con el resultado real.

## Paso 3 · Grafos y documentos
Parchea el JSON directamente (el generador de la app no existe). Sube la versión y `actualizado` en `meta`, y agrega una línea a `historial`.

**`ai/context/backend-graph.json`:**
- Marca como resueltas, con su commit, las brechas BR-06, 07, 16, 17, 18, 22, 23, 25 y 27.
- BR-10, 20 y 21 quedan «adaptadas en la app» (tarea 32).
- BR-24 (dominio de correo) y BR-26 (URL pública y pines SPKI) siguen abiertas.
- Agrega los dos hallazgos nuevos del Paso 2 como brechas con su estado.

**`ai/context/knowledge-graph.json` (app):**
- Dos riesgos aceptados nuevos (los de la revisión de arriba), con sus aristas.
- La regla del Paso 1.
- Agrega el flujo de código para las pantallas de Cambio de correo (DEC-63), si no está.

**Documentos:**
- `docs/api/brechas-backend.md`: una línea al principio que diga «Reemplazado por `ai/context/backend-graph.json`; no se mantiene».
- `README.md` de la app: sección corta «Probar con el backend local».
  1. Las pasos del reporte de la tarea 32.
  2. `adb -s <serie> reverse tcp:3000 tcp:3000`, porque con un emulador abierto `adb` pide elegir dispositivo.
  3. Que `.env` lo escribe cada persona y que las migraciones se aplican con `npx prisma migrate deploy` desde la rama `integracion/app`.
- `ai/context/estado-actual.md`: ponlo al día.

## Paso 4 · Cierre
1. `/ponytail-review` en las dos ramas; corrige lo importante.
2. App: `./gradlew testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug assembleProdDebug`.
3. Push de las dos ramas (los PR #1 y #2 se actualizan solos).
4. Reporte en `ai/handoff/reporte.md`: Paso 0 (qué había), cada paso con sus commits, resultados reales, y lo que quedó pendiente.
5. Estado `hecha`, commit en `integracion/backend` y **detente**.
