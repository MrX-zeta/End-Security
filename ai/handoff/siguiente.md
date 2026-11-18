# Tarea 34 · Sin conexión: la bóveda deja de mostrar el aviso fijo

**Estado:** hecha
**Escrita:** 2026-11-18 · sesión de diseño

## Revisión de la tarea 33
Aprobada. Probado en el teléfono contra el backend local:
- «Más débiles» funciona;
- el tope de reenvíos del código funciona.

Tú no tienes pendientes de esa tarea: la carpeta original ya la restauró la persona.

**Ramas:** solo la app, en `integracion/backend` (PR #2). No toques el backend. Sin merge.
**Reglas:**
- sin capturas;
- no leas `.env*` ni llaves;
- ponytail en `full`;
- no te detengas entre pasos salvo un bloqueo real.
**Carpeta:** trabaja en la carpeta original de la app (la persona instala desde ahí). Si un comando de git te bloquea, anótalo y sigue.

---

## Problema visto en el teléfono
Con la red débil, la bóveda muestra «Sin conexión. Tus cambios se guardan y se subirán al reconectar.». Cuando la red vuelve, el aviso se queda.

Causa (`core/sync/SyncScheduler.kt` y `SyncEngine.kt`):
- el aviso solo se apaga cuando una sincronización termina bien;
- con red débil, el reintento de `onAvailable` falla y no hay otro hasta desbloquear, guardar o volver a primer plano después de 5 min;
- el reintento con espera solo existe para `RetryLater` (5xx), no para `Offline`.

## Decisiones (aprobadas por la persona; cambian DEC-58)
1. **La bóveda es offline: sin aviso fijo.** Quita el `OfflineNotice` de `VaultListScreen` (y lo que solo existía para él en `VaultListViewModel`).
2. **Aviso temporal, una vez por corte.** Usa `AppMessages`/`MessageHost`, que ya existen; no crees un componente nuevo.
   - Se muestra cuando la sincronización pasa de «con conexión» a `Offline` **y** hay cambios locales sin subir.
   - Texto nuevo `vault_offline_toast` = «Sin conexión. Tus cambios se subirán al reconectar.». Dura unos 4 s; si `MessageHost` ya tiene una duración estándar, úsala.
   - No se repite en cada reintento, ni con el mismo corte. No hay mensaje al volver la red.
   - Si no hay cambios pendientes, no se dice nada.
3. **Reintento automático mientras esté `Offline`.** En `runSyncs`, trata `Offline` con espera creciente, como `RetryLater`, pero corta: 15 s, 30 s, 1 min, hasta 2 min.
   - Se detiene al sincronizar bien, al bloquear la bóveda o al cerrar sesión (`closing`).
   - No debe correr con la app en segundo plano; eso lo cubre `SyncWorker`.
4. **Reintentar cuando la red ya es usable.** Además de `onAvailable`, usa `onCapabilitiesChanged` con `NET_CAPABILITY_VALIDATED`.
   - Si está `Offline`, `requestNow()`.
   - Sin pedir permisos nuevos.
5. **Deslizar para actualizar solo en Dispositivos y Actividad.**
   - Usa `PullToRefreshBox` de Material 3: ya viene en la dependencia de Material 3; si no, anótalo y no agregues librerías.
   - Lanza la misma acción `Retry` que ya existe.
   - Funciona también cuando la lista se muestra bien (para actualizarla).
   - El botón «Reintentar» del estado de error se queda.
   - En la bóveda y en Salud **no** se agrega (Salud ya tiene «Revisar ahora»).

## Pruebas (unitarias, sin dispositivo)
- **Programador de sincronización**, con reloj y motor falsos:
  - con `Offline` reintenta a 15 s, 30 s y 1 min, y no pasa de 2 min;
  - se detiene al recibir `Synced`, y al cerrar sesión.
- **Aviso:**
  - sale una sola vez por corte y solo con pendientes;
  - con dos fallos seguidos, solo un mensaje;
  - online → offline → online → offline: dos mensajes.
- **`VaultListViewModel`:** ya no expone el estado de aviso fijo. Ajusta las pruebas que lo usaban.
- **Dispositivos y Actividad:** deslizar lanza `Retry` (ViewModel o prueba de UI JVM, lo que ya use el proyecto).

## Grafo y documentos
- **`knowledge-graph.json`:**
  - DEC-58 ya no dice que la Bóveda muestra un aviso fijo. Describe el aviso temporal, el reintento sin conexión y el deslizar en Dispositivos y Actividad.
  - SCR-44 «Bóveda · sin conexión» pasa a `descartado`, con una nota.
  - Sube la versión y agrega una línea a `historial`.
- **`docs/arquitectura.md`:** actualiza la parte de sincronización, si habla del aviso.

## Cierre
1. `./gradlew testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug assembleProdDebug`, con el resultado real.
2. `/ponytail-review`.
3. Commits en `integracion/backend` y push.
4. **Instala en el teléfono:**
   - `adb -s YTVSGUPBVGLBNVNJ shell true`;
   - si está, `./gradlew :app:installProdDebug` y `adb -s YTVSGUPBVGLBNVNJ reverse tcp:3000 tcp:3000`;
   - si no está conectado, anótalo y sigue.
5. Reporte en `ai/handoff/reporte.md`:
   - commits y resultados;
   - cómo probar el aviso en el teléfono (con el modo avión o apagando el backend);
   - qué quedó pendiente.
6. Estado `hecha`, commit y **detente**.
