# Reporte · Tarea 34

**Estado:** hecha, salvo la instalación en el teléfono (no estaba conectado).
**Rama:** app `integracion/backend` (PR MrX-zeta/End-Security#2). Backend sin tocar. Sin merge.

## Commits
| Commit | Qué |
|---|---|
| `1b6caba` | Código: sin aviso fijo, aviso temporal por corte, reintento sin conexión, `VALIDATED`, deslizar para actualizar, pruebas. |
| `e84da21` | Grafo (DEC-58, SCR-44 descartado, versión 1.11.0) y `docs/arquitectura.md`. |
| este | Reporte y `siguiente.md` hecha. |

## Qué cambió
1. **Bóveda sin aviso fijo:** se quitó `OfflineNotice` de `VaultListScreen` (y el componente, que no usaba nadie más), el campo `offline` de `VaultListUiState` y `SyncStatus` del constructor de `VaultListViewModel`.
2. **Aviso temporal, una vez por corte** (`SyncEngine`): cuando una sincronización termina `Offline`, no se ha avisado en este corte y hay filas `pending`, publica en `AppMessages` el texto `vault_offline_toast` («Sin conexión. Tus cambios se subirán al reconectar.», con `ic_wifi_off_20`; dura los 4 s estándar del `MessageHost`). Se rearma solo cuando una sincronización termina bien. Sin pendientes no dice nada; si el corte empezó sin pendientes y luego se guarda algo, avisa al siguiente intento. No hay mensaje al volver la red. `SyncEngine` recibe `AppMessages` por constructor.
3. **Reintento mientras esté `Offline`** (`SyncLoop`, nuevo, sin Android): 15 s, 30 s, 1 min y luego cada 2 min. Se detiene al sincronizar bien, al cerrar sesión (`closing`), al bloquear y con la app en segundo plano (`SyncScheduler` ahora guarda `foreground` con `onStart`/`onStop`; en segundo plano lo cubre `SyncWorker`). Un cambio guardado o el regreso de la red **cortan la espera** (canal `wake`), para no esperar hasta 2 min. El `5xx` conserva su espera de 30 s a 15 min.
4. **`onCapabilitiesChanged` con `NET_CAPABILITY_VALIDATED`:** si está `Offline`, `requestNow()`. Sin permisos nuevos.
5. **Deslizar para actualizar** en Dispositivos y Actividad con `PullToRefreshBox` (ya viene en Material 3; sin librerías nuevas), por un parámetro opcional de `AccountListPage`. Con la lista a la vista se conserva y solo gira el indicador (`isRefreshing`); si falla, la lista se queda. El botón «Reintentar» del error sigue. No se agregó en la Bóveda ni en Salud.

**Desviación a la tarea:** en Dispositivos, deslizar lanza `Retry`, como pedía. En Actividad lancé una acción nueva, `Refresh`, porque `Retry` con la lista a la vista significa «pedir la página siguiente» y no habría actualizado nada.

## Pruebas nuevas o ajustadas
- `SyncLoopTest` (6, ambas variantes): reintenta a 15 s, 30 s, 1 min y no pasa de 2 min; se detiene al `Synced`, al cerrar o bloquear y en segundo plano; el `5xx` sigue igual; un cambio pedido durante la pasada hace otra.
- `SyncEngineTest` (4, variante demo, motor real contra el backend falso): un aviso con dos fallos seguidos; sin pendientes no avisa; conectado → desconectado → conectado → desconectado da dos avisos y ninguno al volver; corte sin pendientes y luego un cambio avisa.
- `VaultListViewModelTest`: se quitó la prueba del aviso fijo.
- `DevicesViewModelTest` y `ActivityViewModelTest` (2 cada uno): deslizar conserva y renueva la lista; si falla, la lista se queda.
- `ConflictScreensTest` (instrumentada): ahora comprueba que el aviso fijo **no** aparece. Las instrumentadas solo las compilé (`compileDemoDebugAndroidTestKotlin` y la de prod); no las corrí, no hay dispositivo conectado.
- `SyncEngine(...)` en `SyncWorld` y `SyncConflictDeviceTest` recibe `AppMessages`.

## Resultados reales
- `testProdDebugUnitTest` **950/0**, `testDemoDebugUnitTest` **1082/0**, `lintProdDebug` **0 errores** (31 avisos, 2 sugerencias), `assembleProdDebug` bien.
- Push hecho.

## `/ponytail-review` (inline, sin subagente)
Sin hallazgos que arreglar. Revisé:
- Si cierra sesión o bloquea mientras espera, el ciclo sale en la comprobación posterior a la espera; no hace una pasada de más.
- Si pidieron otra pasada (`rerun`) justo cuando se corta el reintento por segundo plano, esa pasada se pierde; la cubre `SyncWorker` y el próximo `onStart`.
- Con la bóveda bloqueada el reintento corto no corre; el único reintento sin conexión con bóveda cerrada es el del `SyncWorker`.

## Instalación en el teléfono
**No instalé.** `adb -s YTVSGUPBVGLBNVNJ shell true` falló («device not found»); `adb devices` solo muestra el emulador, offline. Cuando lo conectes:

```powershell
./gradlew :app:installProdDebug
adb -s YTVSGUPBVGLBNVNJ reverse tcp:3000 tcp:3000
```

## Cómo probar el aviso en el teléfono
1. Con el backend corriendo y la sesión abierta, desbloquea la bóveda y espera a que sincronice.
2. Activa el **modo avión** (o apaga el backend).
3. Crea o edita una contraseña: aparece una sola vez «Sin conexión. Tus cambios se subirán al reconectar.» unos 4 s y no queda nada fijo en la lista. Guardar otra no repite el aviso.
4. Mantén la app a la vista: reintenta a los 15 s, 30 s, 1 min, luego cada 2 min.
5. Quita el modo avión (o enciende el backend): en cuanto Android valide la red, sube solo, sin avisar. Corta otra vez con un cambio nuevo: sale un segundo aviso.
6. En Ajustes → Dispositivos y Actividad reciente, desliza hacia abajo para actualizar; con la lista a la vista no vuelve al esqueleto.

## Pendiente
- Instalar y probar en el teléfono (arriba).
- Correr las instrumentadas de `ConflictScreensTest` y de Dispositivos/Actividad en un dispositivo.
- Figma: SCR-44 ya no se implementa; SCR-47 y SCR-48 (de la tarea 33) siguen sin nodo.
- En la carpeta original queda `docs/bd/end_security_db.jpg` modificado (no se versiona).
