# Tarea 11 · Cabos sueltos de la tarea 10 y Bóveda real (lista, detalle, nueva/editar, eliminar)

**Estado:** hecha
**Escrita:** 2026-10-22 · sesión de diseño

## Revisión de la tarea 10.1
Aprobada. Bien hecho:
- detectar que Android dibuja la capa frontal a 288 dp y corregirlo con el `<inset>` sin tocar el ícono del lanzador;
- `SplashSequencer` con tiempo virtual;
- el splash como capa y no como ruta.

También estuvo bien apartarte de las skills en «reducir movimiento», porque mandaba el tablero.
- **Android 9–11 sin verificar:** se acepta por ahora. Si algún día hay un emulador con API 28–30, mídelo.
- **SCR-46 en el grafo:** el grafo y otros documentos que actualicé están en la carpeta pero **sin commit** (ver Paso 0).

## Paso 0 · Commit de la sesión de diseño
Haz un commit aparte, `docs: grafo v1.5.3, contrato 1.1.0 y splash`, con lo que la sesión de diseño dejó sin commit:
- `ai/context/knowledge-graph.json`;
- `ai/context/estado-actual.md`;
- `CLAUDE.md` (ruta del backend falso);
- `docs/api/README.md` y `docs/api/openapi.yaml` (contrato **1.1.0**).

## Paso 1 · Cabos sueltos de la tarea 10
1. **Contraseña en el árbol de accesibilidad (medio).** Mientras `PasswordField` está oculto, el nodo de accesibilidad solo debe exponer la máscara, nunca el texto real.
   - Prueba sobrescribir la semántica del campo (`password()`, `editableText` con `•` × longitud) sin romper la edición con TalkBack.
   - **Criterio:** con el campo oculto, `uiautomator dump` no muestra el texto en ninguna API, y TalkBack sigue diciendo «Contraseña maestra, campo de contraseña, N caracteres» y permite escribir.
   - Si no se logra sin romper TalkBack, **no lo fuerces**: repórtalo y lo dejo como riesgo aceptado ampliado en DEC-26.
2. **Contrato 1.1.0:** el quinto fallo de `login` y del 2FA ya responde `429 rate_limited` con 900 s, también para correos inexistentes, y el código de correo dura 15 min. Ajusta el backend falso y sus pruebas.
3. **Modo demostración:** siempre que `USE_FAKE_BACKEND` sea `true` (también en release), Verificar correo muestra el aviso «Modo demostración · Código: 123456». Quita la condición de depuración, pero sigue sin escribir el código en logcat.
4. **Secretos de `fake_server.json`:** se aceptan como están. Es un simulador que desaparece al unir el backend real. Anótalo en `docs/api/backend-falso.md`.

## Paso 2 · Bóveda · Lista y vacía (SCR-10, SCR-11; OP-04, OP-07, OP-43)
**Figma:** lista `46:356` y vacía `46:521` (https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security?node-id=46-356).
- Reemplaza el marcador de `Routes.VAULT`.
- **Arriba:** la inicial del usuario y el candado **«Bloquear ahora»**, que llama a `VaultSession.lock()` (DEC-52). Luego «Tu bóveda» y el buscador.
- **La lista:**
  - usa el componente «Elemento de bóveda» de Figma: inicial o ícono, nombre y usuario;
  - los elementos se **descifran en memoria** (`ItemCipher`) a partir de `observeVisible()`, fuera del hilo principal;
  - el orden es alfabético por nombre;
  - un elemento que no se puede descifrar (`AuthenticationFailed`) aparece como «Elemento dañado» y no tumba la lista;
  - un `format_version` desconocido aparece en solo lectura (DEC-04).
- **Búsqueda** local en memoria, por nombre, usuario y sitio, sin acentos ni mayúsculas. Nunca se guarda el texto buscado.
- **Botón flotante «+»** → Nueva contraseña.
- **Vacía (SCR-11):** «Agregar contraseña».
- **Barra inferior:** Salud, Generador y Ajustes van a marcadores por ahora.
- Todo dentro de `RequireUnlocked`, y el ViewModel se vacía al bloquearse.
- El aviso de los códigos de respaldo que hiciste en la tarea 10 se queda.

## Paso 3 · Detalle y «copiada» (SCR-12, SCR-13; OP-05, OP-06)
**Figma:** `46:608` y `62:2946`.
- Muestra nombre, «editada hace …» (de `password_changed_at`), usuario, contraseña oculta con el ojo y sitio.
- El bloque de salud («Segura…») **no se muestra todavía**; llega con Salud.
- **Copiar** (DEC-25):
  - `ClipData` con `EXTRA_IS_SENSITIVE` en Android 13+;
  - el portapapeles se borra a los `clipboard_clear_seconds`: clave nueva en DataStore, **30 s por defecto**, con las opciones 15, 30, 60 y 120 (grafo v1.5.3). Solo se borra si todavía tiene lo que copió la app;
  - el estado «copiada» dura 2,5 s.
- `SecureScreen()` en el detalle, porque muestra la contraseña.
- **Editar** (lápiz) → Nueva/editar con los datos. **Eliminar contraseña** → Paso 5.

## Paso 4 · Nueva / editar (SCR-14; OP-01, OP-02)
**Figma:** `46:793`.
- Campos «Nombre» (obligatorio), «Usuario o correo», «Contraseña» (con ojo y medidor), «Sitio web» y «Notas (opcional)», más el botón «Guardar».
- El **medidor** usa el mismo `PasswordStrengthEstimator`, sin la política de la contraseña maestra: solo informa la fuerza.
- **La contraseña del elemento:**
  - va en `TextFieldState` y nunca en el estado del ViewModel, con `SecureScreen` y `ExcludeFromAutofill`;
  - el elemento completo se arma solo al tocar «Guardar».
- **Guardar:**
  - **nuevo:** UUID del cliente, `created_at` y `password_changed_at` igual a ahora, `ItemCipher.encrypt` y `VaultItemDao.insert` con `sync_state = pending`;
  - **editar:** con el `local_seq` esperado (regla 8). `password_changed_at` solo cambia si cambió la contraseña (DEC-04).
- Un botón «Generar» junto a la contraseña queda deshabilitado con `TODO(tarea del Generador)`.
- Al salir sin guardar con cambios, una hoja: «¿Descartar cambios?», con «Descartar» (peligro) y «Seguir editando».

## Paso 5 · Eliminar con «Deshacer» (SCR-16, SCR-17; OP-03, OP-47)
**Figma:** hoja `62:2972` y lista con aviso `62:3009`.
- La hoja muestra «¿Eliminar {nombre}?», con «Eliminar» (peligro) y «Cancelar».
- Al confirmar, la lista muestra «{nombre} se eliminó · **Deshacer**» durante **5 s**.
- La lápida **no se escribe hasta que pasan los 5 s** (o se bloquea la bóveda, o la app pasa a segundo plano). «Deshacer» la cancela sin tocar Room (DEC-56).
- Un elemento que nunca se subió se borra físicamente; uno ya sincronizado queda como lápida `pending` (como ya hace `VaultItemDao.delete`).

## Fuera de esta tarea
La **sincronización** (subir los `pending` y bajar cambios, OP-33/34), los conflictos (SCR-18…21), el Generador y Salud
llegan en las tareas 12 y 13. Por ahora, lo que se crea o edita queda en `pending` en Room.

## Terminado cuando
- **Pruebas unitarias:**
  - búsqueda sin acentos;
  - orden;
  - elemento dañado;
  - guardar nuevo y editar (con `password_changed_at` solo si cambia);
  - «Deshacer» dentro y fuera de los 5 s;
  - bloquear durante los 5 s aplica la lápida;
  - el portapapeles se borra solo si es lo de la app.
- **Prueba instrumentada:** el portapapeles con `EXTRA_IS_SENSITIVE`.
- **En el emulador:**
  - crear 3 contraseñas, buscar, ver el detalle, copiar, editar, eliminar y deshacer, eliminar y esperar;
  - bloquear con el candado y desbloquear: los elementos siguen ahí.
- `security-reviewer` revisa los Pasos 1.1, 3 y 4. **REQUIERE REVISIÓN HUMANA.**
- `assembleRelease` y lint pasan. Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
