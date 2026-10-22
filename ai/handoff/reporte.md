# Reporte · Tarea 11 · Cabos sueltos de la tarea 10 y Bóveda real (lista, detalle, nueva/editar, eliminar)

**Estado:** hecha
**Fecha:** 2026-10-22

**REQUIERE REVISIÓN HUMANA:** toca el cifrado de elementos (`VaultRepositoryImpl`), el portapapeles seguro, la accesibilidad del campo de contraseña y los borrados con «Deshacer».

## Qué se hizo

### Paso 0 · Commit de la sesión de diseño
`docs: grafo v1.5.3, contrato 1.1.0 y splash` con el grafo, `estado-actual.md`, `CLAUDE.md` y los dos archivos de `docs/api/`. No incluí `docs/backend/` (carpeta nueva de la sesión de diseño, fuera de los archivos que pedía la tarea).

### Paso 1 · Cabos sueltos de la tarea 10
1. **Contraseña en el árbol de accesibilidad: resuelto.**
   - `PasswordField` sobrescribe la semántica con una máscara (`editableText = "•" × longitud`) mientras está oculto.
   - Comprobado en el emulador (API 36) con `uiautomator dump`: el nodo es `EditText`, `password="true"` y su texto son solo puntos.
   - Un `Modifier.semantics` exterior gana al del propio `BasicSecureTextField`, así que no hizo falta envolver el campo (que habría borrado la semántica del botón del ojo).
   - Con el campo visible se expone el texto real, que ya está en pantalla.
   - **No pude probar TalkBack** (no hay lector de pantalla en el emulador). Lo que mantiene su comportamiento es que el nodo sigue siendo editable y de contraseña; quedan sin verificar a mano el anuncio «N caracteres» y escribir con el teclado accesible.
2. **Contrato 1.1.0:** el quinto fallo de `login` y del 2FA responde `429 rate_limited` con 900 s, también para correos inexistentes. Pruebas nuevas en `FakeBackendTest` (inclusive el correo inexistente y el 2FA).
   - En el emulador: 5 contraseñas incorrectas → el quinto intento ya muestra «Demasiados intentos. Vuelve a intentarlo en 15 min.»
3. **Modo demostración:** el aviso de Verificar correo ahora dice «Modo demostración · Código: 123456» y sale mientras `USE_FAKE_BACKEND` sea `true` (también en release). Sigue sin escribirse en logcat. Las cuentas `+2fa` siguen siendo solo de depuración.
4. **`fake_server.json`:** se anotó en `docs/api/backend-falso.md` que el archivo guarda el pepper y el secreto TOTP en claro y que se acepta.

### Paso 2 · Bóveda · Lista y vacía (SCR-10, SCR-11)
- **Repositorio (`feature/vault/data/VaultRepositoryImpl`):**
  - `observeEntries()` descifra con `ItemCipher` en `Dispatchers.Default` y con la bóveda bloqueada emite una lista vacía;
  - cada fila es `ReadableEntry`, `DamagedEntry` (etiqueta GCM que no coincide, «Elemento dañado») o `UnsupportedEntry` (`format_version` mayor, solo lectura, DEC-04);
  - un elemento dañado o de otra versión no tumba la lista.
- **`VaultListViewModel`:**
  - orden alfabético sin acentos ni mayúsculas, con los dañados al final;
  - búsqueda local por nombre, usuario y sitio, sin acentos (`ItemSearch`);
  - el texto buscado vive solo en memoria y se borra al bloquear;
  - el estado no imprime nombres.
- **Pantalla:**
  - avatar con la inicial de la cuenta, candado «Bloquear ahora» (`VaultSession.lock()`, DEC-52), título «Tu bóveda · N contraseñas guardadas», buscador, «Todas · A–Z», filas del componente «Elemento de bóveda», botón flotante «+» y barra inferior;
  - SCR-11 (vacía) con disco, «Guarda tu primera contraseña» y «Agregar contraseña»;
  - el aviso de códigos de respaldo de la tarea 10 se queda;
  - todo dentro de `RequireUnlocked` y con `SecureScreen()`.
- **Componentes nuevos del sistema de diseño:** `SearchField`, `LetterTile`, `FloatingAddButton`, `MessageBar` (el aviso oscuro), `ReadOnlyField`, `FieldActionButton` (extraído del ojo de `PasswordField`) y 10 íconos vectoriales.
- **Lo que no se muestra todavía (Figma lo tiene):** el aviso «3 contraseñas necesitan atención» y los triángulos rojos de las filas son de Salud.

### Paso 3 · Detalle y «copiada» (SCR-12, SCR-13)
- **`ItemDetailViewModel` / pantalla:**
  - nombre, «editada hace …» (de `password_changed_at`), usuario con copiar, contraseña oculta con el ojo, sitio con abrir, «Eliminar contraseña» y «Copiar contraseña»;
  - la contraseña se oculta al salir de primer plano;
  - `SecureScreen()` en el detalle;
  - el ViewModel sigue al elemento en Room, así que un cambio hecho en Editar se ve al volver.
- **Copiar (DEC-25):**
  - `SecretClipboard` marca el `ClipData` con `EXTRA_IS_SENSITIVE` (Android 13+) y lo borra a los `clipboard_clear_seconds`;
  - ajuste nuevo en DataStore, 30 s por defecto con las opciones 15, 30, 60 y 120;
  - el aviso «Copiada · se borra del portapapeles en 30 s» dura 2,5 s.
- **Abrir el sitio:** solo `http` y `https` (`SiteUrl`); `javascript:`, `intent:`, `file:`, `content:`, `mailto:` y demás no se abren. Un sitio sin esquema («netflix.com») se abre con `https://`.

### Paso 4 · Nueva / editar (SCR-14)
- **`ItemEditViewModel` / pantalla:**
  - los cinco campos son `TextFieldState` de la pantalla (no `rememberSaveable`, no el estado del ViewModel); el elemento completo se arma solo al tocar «Guardar»;
  - al editar, el ViewModel entrega los valores una vez y los suelta (`onPrefillApplied`);
  - `SecureScreen()`, `ExcludeFromAutofill()` y los campos se vacían al salir;
  - el medidor usa el mismo `PasswordStrengthEstimator`, con 150 ms de espera y sin la política de la contraseña maestra;
  - «Generar» queda deshabilitado con `TODO(tarea del Generador)`;
  - al salir con cambios, hoja «¿Descartar cambios?».
- **Guardar:**
  - **nuevo:** UUID del cliente, `created_at` y `password_changed_at` iguales, bloque cifrado y `VaultItemDao.insert` con `sync_state = pending`;
  - **editar:** con el `local_seq` esperado (regla 8); `password_changed_at` solo cambia si cambió la contraseña (DEC-04);
  - si otra escritura se adelantó, muestra «Esta contraseña cambió…» y no sale.

### Paso 5 · Eliminar con «Deshacer» (SCR-16, SCR-17)
- La hoja «¿Eliminar {nombre}?» y, al confirmar, vuelve a la lista con «{nombre} se eliminó · Deshacer» durante 5 s.
- **`DeletionUndoController`** (singleton): la lápida no se escribe hasta que pasan 5 s, se bloquea la bóveda, la app pasa a segundo plano o se programa otro borrado. «Deshacer» cancela sin tocar Room. Un elemento que nunca se subió se borra físicamente y uno sincronizado queda como lápida `pending` (lo que ya hacía `VaultItemDao.delete`).

## Compilación y pruebas (salida real)
- **`testDebugUnitTest`: 390, 0 fallas.** Nuevas:
  - `VaultRepositoryTest` (10): cifrado sin texto claro, `created_at`/`password_changed_at`, `local_seq` viejo, elemento dañado, AAD con otro id, versión de formato mayor, lápida contra borrado físico, bóveda bloqueada, recorte de campos;
  - `DeletionUndoControllerTest` (6): 5 s exactos, deshacer, bloquear, segundo plano y borrado nuevo;
  - `SecretClipboardControllerTest` (7): 30 s, solo borra lo suyo, segundo plano, segundos del ajuste;
  - `ItemSearchTest`, `ItemDisplayTest` y los ViewModels de lista, detalle y edición.
- **`connectedDebugAndroidTest`: 40, 0 fallas.** Nuevas:
  - `ClipboardDeviceTest`: `EXTRA_IS_SENSITIVE` con el portapapeles real, y la etiqueta identifica lo copiado;
  - `VaultRepositoryDeviceTest`: con Room y SQLite reales, ninguna columna lleva texto en claro;
  - `DaoTest`: `local_seq` que sube con un cambio remoto y `observe(id)`.
- **`assembleRelease` y `lintDebug`:** compilan, lint con 0 errores y 12 advertencias (las mismas de antes).

## Revisión con `security-reviewer`
No encontró nada crítico ni alto (cifrado y AAD, `local_seq` en las escrituras, nada en claro en Room, `SavedStateHandle` ni logs, `SiteUrl`, FLAG_SECURE). **Corregí en esta tarea:**
- **(Medio) Girar la pantalla en Editar vaciaba el formulario y «Guardar» podía borrar la contraseña guardada.** Ahora la pantalla pide los valores al ViewModel cada vez que arma sus campos (`requestPrefill`, se leen de nuevo del repositorio), y «Guardar» espera a que los campos tengan los datos. Comprobado en el emulador girando con el formulario abierto.
- **(Medio) El ViewModel de edición retenía la contraseña original (`baseline`).** Lo que había al abrir ahora vive con los campos en la pantalla y se borra al salir; el ViewModel ya no guarda ninguna copia.
- **(Medio) El detalle descifraba toda la bóveda en cada cambio.** `VaultRepository.observeEntry(id)` y `VaultItemDao.observe(id)`: solo se descifra ese elemento.
- **(Medio) `applyRemote` reiniciaba `local_seq` a 0.** Ahora sube (`anterior + 1`), para que una edición abierta no pase sobre un cambio remoto (regla 8). Con prueba en `DaoTest`.
- **(Medio) Portapapeles:** al volver la app a primer plano, si lo copiado es de la app y su temporizador no corrió (proceso muerto o congelado), se borra.
- **(Medio) `DeletionUndoController`:** un fallo de Room ya no cierra la app ni deja el aviso para siempre, y si `delete` devuelve `false` el aviso se quita igual.
- **(Bajo):**
  - doble toque en «Eliminar», «Descartar» y «Guardar» ya no cierra dos veces ni guarda un duplicado;
  - sin corrector de teclado en Nombre y Notas;
  - la hoja de eliminar y la de descartar también van como datos sensibles;
  - `OpenSite` ya no imprime el sitio;
  - un nonce corrupto aparece como «Elemento dañado» en vez de tumbar la lista;
  - la máscara de la contraseña también declara `password()`.

**Quedan abiertos:**
- **Lista y descifrado:** la lista sigue descifrando todo (contraseña incluida) para mostrar solo nombre y usuario; un `observeSummaries()` sin contraseña ni notas queda para la tarea de sincronización.
- **Tras un conflicto** el formulario conserva el `local_seq` viejo y no ofrece recargar.
- **Elementos dañados** sin salida: no se pueden abrir ni borrar (también está en las observaciones).
- **`updateContent` sin `format_version` en el SQL:** hoy lo protege la lectura previa. Un `AND format_version = :fv` sería defensa en profundidad.
- **Borrado físico y sincronización:** si un `pending` sin `serverVersion` se está subiendo cuando se borra, el servidor lo conserva y reaparece al bajar cambios. Tenerlo en cuenta en la tarea 12.
- **Prueba de regresión de la máscara:** el reporte afirma lo que vi con `uiautomator`; falta una prueba instrumentada que lea `SemanticsProperties.EditableText`.
- **Texto de DEC-25:** lo que se hace en segundo plano (borrar sin poder verificar) se aparta de la redacción actual; propongo «se borra salvo que se pueda comprobar que hay otra cosa copiada».
- **`USE_FAKE_BACKEND`:** al unir el backend real, la bandera debe pasar a `false` en release (el código de correo no debe verse).

## En el emulador (Pixel 7 Pro, API 36)
- Vacía → «Agregar contraseña» → crear 3 → lista A–Z, buscar («NET» deja una fila, «zzz» muestra «Sin resultados») → detalle → copiar (aviso «Copiada · se borra del portapapeles en 30 s») → editar (los campos llegan llenos; «¿Descartar cambios?» al salir con un cambio; guardar vuelve al detalle) → eliminar y «Deshacer» → eliminar y esperar → bloquear con el candado durante el aviso y desbloquear: el elemento ya estaba borrado y los otros siguen. ✔
- Las capturas de las pantallas con FLAG_SECURE salen negras (es lo esperado). Para ver las pantallas desactivé `SecureScreen` **temporalmente** y lo revertí (el archivo no tiene diferencias).
- **Crash que encontré y arreglé:** el botón flotante tenía `padding(end = -8.dp)` y cerraba la app al aparecer.

## Observaciones
1. **Portapapeles en segundo plano (importante):** Android 10+ no deja a una app leer la etiqueta de lo copiado cuando no tiene el foco (devuelve `null`, igual que con el portapapeles vacío); lo comprobé con una prueba instrumentada. Por eso «borrar solo si todavía tiene lo que copió la app» solo se puede verificar con la app en primer plano. En segundo plano, si no se puede ver, **se borra igual**: dejar la contraseña copiada es el riesgo mayor. Consecuencia: si la persona copia otra cosa en otra app dentro de esos 30 s, también se pierde. Está documentado en `SecretClipboard` y con prueba.
2. **El borrado del portapapeles depende del proceso:** si Android mata la app antes de los 30 s, se borra al volver a abrirla (primer plano); mientras tanto sigue copiado. Una alarma o un `WorkManager` cerrarían ese hueco; no estaba en la tarea.
3. **Elementos dañados o de otra versión:** aparecen en la lista pero no tienen detalle, así que no se pueden eliminar desde la app. Conviene decidir cómo se manejan.
4. **Notas:** el detalle de Figma no las tiene, pero el formulario las pide; las muestro como un campo de solo lectura si existen.
5. **Contador de la lista y el aviso:** con un solo elemento recién eliminado y aún en «Deshacer», la lista muestra SCR-11 (vacía) más el aviso «… se eliminó · Deshacer».
6. **Sin Android 9–11 y sin TalkBack** verificados (ver Paso 1.1).
7. **Autoguardado de la sincronización:** todo lo que se crea o edita queda `pending` en Room; subirlo es de las tareas 12 y 13.

## Vistas nuevas
- **Bóveda (lista y vacía):** se llega después de entrar o desbloquear.
- **Detalle:** al tocar un elemento de la lista.
- **Nueva / editar contraseña:** con el botón «+» o «Agregar contraseña», y con el lápiz del detalle.
- **Hoja «¿Eliminar …?» y «¿Descartar cambios?»**, y el aviso «… se eliminó · Deshacer».
