# Reporte · Tarea 12 · Sincronización, sin conexión y conflictos (OP-33, OP-34, OP-46; SCR-18…21, SCR-44)

**Estado:** hecha
**Fecha:** 2026-10-22

**REQUIERE REVISIÓN HUMANA:** toca la sincronización (`SyncEngine`, `SyncScheduler`, `SyncWorker`), SQL de Room (`Daos.kt`), el re-cifrado de elementos al resolver conflictos (`ConflictResolver`) y pantallas que muestran contraseñas. Pasó por el subagente `security-reviewer` (ver abajo).

## Qué se hizo

### Paso 0 · Commit de la sesión de diseño
`docs: alcance del backend, syslog y contrato 1.2.0` (`a13cdad`).

### Paso 1 · Cabos de la tarea 11
- **Detalle mínimo** de elementos dañados («No se puede abrir esta contraseña») y de versión más nueva («Actualiza la app para ver esta contraseña»): solo «Eliminar», sin campos ni «Editar». El borrado va por `DeletionUndoController` con `PendingDeletion(name = null)` y el aviso genérico «Se eliminó una contraseña · Deshacer». En la lista, todas las filas abren un detalle.
- **SQL de `updateContent`** con `AND format_version = :fv`: un bloque de formato más nuevo nunca se reescribe.
- **Editar tras un conflicto al guardar:** «Recargar» (relee el elemento con su `local_seq` nuevo y vuelve a llenar los campos) y «Conservar lo mío» (solo toma el `local_seq` nuevo; lo escrito se queda; lo decide la persona). Texto del error actualizado.
- **Prueba instrumentada de la máscara** (`PasswordFieldMaskTest`): lee `SemanticsProperties.EditableText` y `Password`; oculta solo hay puntos de la misma longitud, al mostrar/ocultar y al escribir. **TalkBack con lector real lo prueba la persona en el Redmi.**
- **`observeSummaries()`**: la lista ya no descifra contraseñas ni notas (`ItemCipher.decryptSummary`).

### Paso 2 · Motor de sincronización
- **`SyncEngine`** (`core/sync/`): `Mutex` (una ejecución a la vez); sube los pendientes de uno en uno por `local_seq` con PUT/DELETE y versión esperada; baja cambios paginados; todas las filas de §8.2; `410 cursor_expired` → descarga completa y reconciliación sin perder `pending` ni `conflict`; borrar mientras se sube → lápida `pending` y luego DELETE; una fila cuya subida el servidor ya aceptó pero cuya respuesta se perdió se reconoce por su propio bloque (no es conflicto).
- **DAO:** `applyRemotePage` guarda filas y cursor en la misma transacción.
- **`SyncScheduler`:** al desbloquear, al guardar/eliminar (espera 2 s), al volver a primer plano tras más de 5 min, al volver la red (`ConnectivityManager`, solo dispara el reintento) y espera exponencial en 5xx. Una sincronización empezada **no se cancela** (un cambio nuevo pide otra pasada al terminar).
- **`SyncWorker`:** WorkManager periódico cada 15 min con red; puede correr con la bóveda bloqueada (solo mueve bloques cifrados; no usa `vault_key`).
- `session_revoked` → el cierre local que ya existía; sin red → reintentar.
- **EP-26** (`GET /vault/items/{id}`) en `VaultApi`, `VaultApiImpl` y el backend falso.

### Paso 3 · Sin conexión (SCR-44)
Aviso «Sin conexión. Tus cambios se guardan y se subirán al reconectar.» en la Bóveda cuando la última sincronización falló por red; se quita al sincronizar bien.

### Paso 4 · Conflictos (SCR-18…21)
- Aviso «N contraseñas cambiaron en dos dispositivos · Resolver» en la Bóveda: con 1 conflicto va directo a «Elegir versión»; con varios, a «Por resolver».
- **`ConflictResolver`:** pide la versión del servidor (EP-26), descifra las dos solo en memoria; «la del servidor» reemplaza la fila y queda `synced`; «la mía» re-cifra con nonce nuevo, `server_version` = la del servidor y queda `pending`. Lápida remota: «Conservar la mía» / «Eliminarla»; lápida local: «Conservar la del otro dispositivo» / «Eliminarla». Aviso «{nombre} quedó resuelta» (SCR-20). Sin copias ni versiones anteriores (FDA-11).
- `SecureScreen()` en «Por resolver» y «Elegir versión»; las contraseñas se ocultan al salir de primer plano; los ViewModel se vacían al bloquear.
- **Simulación del otro dispositivo:** receptor solo de depuración `app/src/debug/.../OtherDeviceReceiver` (permiso `DUMP`, solo `adb shell`; no existe en release). Comando en `docs/api/backend-falso.md`.

## Pruebas (salida real)
- **`testDebugUnitTest`: 447, 0 fallas.** Nuevas: `SyncEngineTest` (19: cada fila de §8.2, offline, 5xx, 410, borrar durante una subida, orden por `local_seq`, eco de una subida perdida), `ConflictResolverTest` (12: local, servidor, lápida remota y local, `local_seq` viejo, sin red, 404, bóveda bloqueada) con dos «teléfonos» reales (cifrado real + backend falso), `ConflictViewModelsTest` (16), detalle mínimo, Recargar/Conservar lo mío, lista (offline, conflictos).
- **`connectedDebugAndroidTest` en el emulador: 56 pruebas, 53 pasan y 3 fallan: las 3 de `ClipboardDeviceTest`.** Fallan porque el portapapeles del emulador se sincroniza con el del PC (la etiqueta del portapapeles aparece como «host clipboard») y pisa lo que la prueba acaba de copiar; esas mismas 3 pasaron esta tarde en la primera corrida y no tocan código de esta tarea. No cambié nada para taparlo: conviene desactivar «Enable clipboard sharing» del emulador o repetirlas. Pasan: `SyncConflictDeviceTest` (3, con Room real: editar lo mismo en dos teléfonos → conflicto → resolver → una sola versión y sin aviso, en las dos direcciones; y modo avión → «sin conexión» → crear → volver la red → se sube), `PasswordFieldMaskTest` (3), `ConflictScreensTest` (4), `DaoTest` y las demás.
- **`lintDebug`: 0 errores** (corregí uno nuevo, `LocalContextConfigurationRead`, en `ConflictListScreen`). **`assembleRelease`: compila.**

## Verificación visual
No pude ver las pantallas seguras en el emulador (FLAG_SECURE las vuelve negras para `screencap` y `uiautomator`), y **no quité FLAG_SECURE para verlas**: ese intento fue bloqueado y lo dejé así. En su lugar `ConflictScreensTest` dibuja los composables sin `SecureScreen` (que solo está en el `Route`) y comprueba sus textos; revisé las capturas de la Bóveda (aviso sin conexión + aviso de conflictos) y de «Elegir versión»: coinciden con el diseño. **No recorrí a mano en el emulador la secuencia completa por la interfaz** (editar → comando `adb` → aviso → resolver); esa parte está cubierta por `SyncConflictDeviceTest` con la misma lógica y Room real. Queda para la persona probarla con el comando de depuración.

## Revisión con `security-reviewer`
Sin hallazgos críticos ni altos. **Corregí:**
- (Medio) `resolveWithRemote` no comparaba `local_seq`: «Conservar la del servidor» podía pisar una edición reciente. Ahora lo recibe y devuelve `Stale`; `keepMineAsNew` valida antes de crear (no deja duplicados). Pruebas en JVM y en `DaoTest`.
- (Medio) `SyncScheduler` cancelaba una subida a mitad de un PUT, lo que producía falsos conflictos con contenido idéntico. Ya no cancela una ejecución empezada, y un bloque idéntico en la descarga se reconoce como eco, no como conflicto.
- (Medio) «Conservar la del servidor» con un bloque remoto ilegible ya no reemplaza lo legible (`Unavailable`; solo se acepta uno de formato más nuevo, DEC-04); la pantalla no deja elegir una versión ilegible.
- (Medio) `ConflictResolver.toRow` captura `VaultLockedException` (el autobloqueo no tumba la lista).
- (Bajo) El nombre de «quedó resuelta» se limpia al bloquear y al cerrar la pantalla; `ConflictDetailViewModel` no deja nada descifrado si se bloquea mientras carga y se vacía en `onCleared`; comentario de `decryptSummary` corregido.

**Quedan abiertos (anotados, no corregidos):**
- (Bajo) Una descarga completa tras `410` interrumpida a mitad puede dejar elementos eliminados en otro dispositivo como «fantasmas» `synced` (el cursor avanza por página; la limpieza va al final). No pierde `pending` ni `conflict`. Arreglo propuesto: no avanzar el cursor hasta la última página.
- (Bajo) Borrar justo al subir con respuesta perdida puede resucitar el elemento si el DELETE de rescate falla (se ignora su resultado).
- (Bajo) «Conservar la mía» ante una lápida remota crea un elemento nuevo: `createdAt` y `password_changed_at` se reinician (afecta «Antiguas» de Salud). Pide un parámetro de fechas en `VaultRepository.create`.
- `SyncWorker.cancel()` no se llama todavía (hay que llamarlo al cerrar sesión cuando se una ese flujo).

## Decisiones y desviaciones a revisar
- El contrato no da el nombre del otro dispositivo en EP-26: la pantalla dice «En otro dispositivo».
- En lugar de las comparaciones «Cambió / Sin cambios» de Figma se resalta en negritas lo que difiere entre las dos versiones.
- Con una lápida (local o remota) se usan botones en vez del selector.
- Un 5xx corta la ejecución antes de bajar cambios.
- Se agregó `ACCESS_NETWORK_STATE`. `INTERNET` no (backend falso).
- Dependencia nueva: `androidx.work:work-runtime-ktx` 2.10.2 (`gradle/libs.versions.toml`); sin `hilt-work` (el worker toma el motor por un `EntryPoint`).
- No hubo nada que anotar del contrato más allá de EP-26 (ya en 1.2.0).

## Vistas nuevas (cómo llegar)
- **Por resolver (SCR-19/20):** Bóveda → aviso «N contraseñas cambiaron en dos dispositivos · Resolver» (con 2 o más conflictos).
- **Elegir versión (SCR-21):** desde «Por resolver», o directo desde el aviso si hay un solo conflicto.
- **Sin conexión (SCR-44):** aviso en la Bóveda con modo avión.
- **Detalle mínimo** de dañados / versión nueva: tocar esa fila en la lista.
- Para probar un conflicto: editar un elemento, mandar el comando de `docs/api/backend-falso.md` y volver a primer plano.
