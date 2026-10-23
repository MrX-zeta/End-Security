# Tarea 12 · Sincronización, sin conexión y conflictos (OP-33, OP-34, OP-46; SCR-18…21, SCR-44)

**Estado:** hecha
**Escrita:** 2026-10-22 · sesión de diseño

## Revisión de la tarea 11
Aprobada, con la revisión humana del cifrado por elemento, el portapapeles y la máscara de accesibilidad. Muy bien:
- la máscara con `Modifier.semantics` exterior, que conserva el ojo;
- encontrar que girar la pantalla en Editar podía borrar la contraseña guardada;
- `local_seq` que sube con un cambio remoto;
- `SiteUrl`, que solo acepta http y https.

Respuestas (grafo v1.5.5, contrato **1.2.0**):
- **Portapapeles en segundo plano (DEC-25):** se acepta tu criterio y lo dejé redactado así: «se borra salvo que se pueda comprobar que hay otra cosa copiada». El hueco del proceso muerto se cierra al volver a primer plano, como ya lo hiciste. No hace falta `WorkManager`.
- **Elementos dañados o de versión desconocida:** al tocarlos se abre un detalle mínimo.
  - Dañado: «No se puede abrir esta contraseña», con «Eliminar» (lápida normal).
  - Versión desconocida: «Actualiza la app para ver esta contraseña», también con «Eliminar».
- **`updateContent`:** agrega `AND format_version = :fv` en el SQL, como defensa en profundidad.
- **Conflicto al guardar en Editar:** se ofrece «Recargar», que vuelve a leer el elemento con su `local_seq` nuevo y conserva lo que la persona escribió solo si ella lo confirma.
- **Prueba de regresión de la máscara:** sí, instrumentada, leyendo `SemanticsProperties.EditableText`.
- **TalkBack:** la persona del equipo lo probará en su Redmi. No hace falta que lo hagas tú.
- **`USE_FAKE_BACKEND = false` en release** cuando se una el backend real: anotado para la tarea de integración.
- **`docs/backend/README.md` es nuevo** (alcance del backend, con syslog RFC 5424 de 5 niveles, DEC-62). Inclúyelo en el commit del Paso 0.

## Paso 0 · Commit de la sesión de diseño
Haz el commit `docs: alcance del backend, syslog y contrato 1.2.0` con:
- `docs/backend/README.md`;
- `docs/api/README.md` y `docs/api/openapi.yaml`;
- `ai/context/knowledge-graph.json` y `ai/context/estado-actual.md`.

## Paso 1 · Cabos de la tarea 11
Los cinco puntos de arriba: el detalle mínimo de los elementos dañados, el SQL con `format_version`, «Recargar» en Editar, la prueba de la máscara y `observeSummaries()`.
- **`observeSummaries()`:** la lista ya no descifra contraseñas ni notas para mostrarse. Descifra el bloque, toma solo nombre, usuario y sitio, y borra el resto del JSON en `finally`.

## Paso 2 · Motor de sincronización (`core/sync`, `docs/bd/modelo-datos.md` §8.2)
- **`SyncEngine`**, con una sola ejecución a la vez (`Mutex`):
  1. **Subir pendientes (OP-34):** de uno en uno, en orden de `local_seq`, con `PUT /vault/items/{id}` (con `expectedVersion = server_version`, o `null` si es nuevo) o con `DELETE /vault/items/{id}?expectedVersion=…` para las lápidas.
  2. **Bajar cambios (OP-33):** `GET /vault/items?since=sync_cursor`, paginado, hasta `hasMore = false`. Aplica la tabla §8.2:
     - un cambio remoto sobre una fila `synced` la reemplaza, o la borra si es lápida;
     - un cambio remoto sobre una fila `pending` la marca `conflict`;
     - guarda `sync_cursor` y `last_sync_at` en `account` **en la misma transacción** de Room que los cambios.
  3. **Respuestas de la subida, según §8.2:**
     - `created` o `updated`: `synced` si el `local_seq` no cambió; si cambió, sigue `pending` con el `server_version` nuevo;
     - `deleted`: borrado físico;
     - `409`: `conflict`;
     - `404` al editar: `server_version = null` y se vuelve a crear.
  4. **`410 cursor_expired`:** descarga completa y reconciliación. Las filas `pending` y `conflict` no se pierden.
- **Borrado mientras se sube (tu observación 9 de la tarea 09 y la de la tarea 11):** no se borra físicamente una fila que tenga una subida en curso. Se marca como lápida `pending`, y al terminar la subida se envía el `DELETE`.
- **Cuándo corre:**
  - al desbloquear;
  - al guardar o eliminar (con 2 s de espera para juntar cambios);
  - al volver a primer plano si pasaron más de 5 min;
  - con un `WorkManager` periódico de 15 min con red disponible.
- **El `SyncWorker` puede correr con la bóveda bloqueada:** solo mueve bloques y marca `conflict`, **sin `vault_key`** (DEC-47).
- **Errores:**
  - sin red, `NetworkUnavailable` → se reintenta después, sin aviso intrusivo;
  - `session_revoked` → cierra la sesión local (ya existe);
  - `5xx` → se reintenta con espera exponencial.

## Paso 3 · Sin conexión (SCR-44, DEC-58)
**Figma:** `69:3744`.
- La Bóveda muestra el aviso sin conexión de Figma cuando la última sincronización falló por red. Se sigue pudiendo usar todo.
- El aviso se quita al sincronizar bien. Usa `ConnectivityManager` solo para disparar el reintento, no como verdad.

## Paso 4 · Conflictos (SCR-18…21, OP-46, DEC-57)
**Figma:**
- lista con aviso `62:3168`;
- «Por resolver» `65:3017`;
- «Por resolver · 1 resuelta» `65:3145`;
- «Elegir versión» `46:1196`.

Comportamiento:
- **Aviso agrupado en la Bóveda:** «N contraseñas cambiaron en dos dispositivos · Resolver». Con 1 conflicto va directo a «Elegir versión»; con varios, a «Por resolver».
- **`ConflictResolver`** (con la bóveda desbloqueada):
  - para cada elemento en `conflict` pide la versión del servidor con **`GET /vault/items/{id}`** (EP-26, nuevo en el contrato 1.2.0; agrégalo al backend falso);
  - descifra las dos versiones solo en memoria.
- **«Elegir versión»:**
  - muestra las dos versiones (este teléfono y el otro dispositivo, con fecha), con la contraseña oculta y su ojo;
  - «Conservar esta versión»:
    - **la del servidor:** reemplaza la fila local y queda `synced`;
    - **la local:** se vuelve a cifrar (nonce nuevo), `server_version` = la del servidor, queda `pending` y se sincroniza.
  - Si la del servidor es una **lápida**: «Se eliminó en otro dispositivo», con «Conservar la mía» o «Eliminarla».
- Al resolver, aparece «{nombre} quedó resuelta» (SCR-20). Cuando no quedan conflictos, desaparece el aviso.
- **No se guardan copias ni versiones anteriores** (FDA-11). Mientras tanto, el elemento se usa con la versión local.
- `SecureScreen()` en «Elegir versión», y los ViewModel se vacían al bloquear.

## Terminado cuando
- **Pruebas unitarias de `SyncEngine` con el backend falso**, para cada fila de la tabla §8.2:
  - `created` y `updated` con y sin cambio de `local_seq` durante la subida;
  - `409` → `conflict`;
  - `404` → recrear;
  - `deleted`;
  - lápida remota sobre `synced`;
  - cambio remoto sobre `pending`;
  - `410` → reconciliación sin perder pendientes;
  - borrar durante una subida;
  - cursor y filas en la misma transacción.
- **Pruebas de `ConflictResolver`:** elegir la local, la del servidor y una lápida remota.
- **Prueba de punta a punta en el emulador con dos «dispositivos»:**
  - simula el otro dispositivo con una prueba o con un comando de depuración que escriba en `fake_server.json` como si fuera otro teléfono;
  - edita la misma contraseña en los dos lados;
  - sincroniza: aparece el aviso → resolver → queda una versión y sin aviso.
- **También en el emulador:** modo avión → aviso sin conexión → crear → quitar modo avión → se sube.
- `security-reviewer` revisa los Pasos 2 y 4. **REQUIERE REVISIÓN HUMANA.**
- `assembleRelease` y lint pasan. Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
