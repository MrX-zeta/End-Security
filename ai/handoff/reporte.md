# Reporte · Tarea 13 · Cabos de la sincronización y Generador (SCR-15, OP-08)

**Estado:** hecha
**Fecha:** 2026-10-22

**REQUIERE REVISIÓN HUMANA:** `PasswordGenerator` (aleatoriedad), la entrega de la contraseña generada (`PasswordHandoff`), el cierre de sesión con la sincronización (`SyncScheduler.stop`, `SyncEngine`) y SQL/rescate de borrado. Pasó por el subagente `security-reviewer` (ver abajo).

## Qué se hizo

### Paso 1 · Cabos de la tarea 12
1. **Descarga completa tras `410`:** el cursor no avanza hasta la última página; si se interrumpe, la siguiente ejecución recibe otra vez `410` y empieza desde cero. Prueba: 205 elementos, se corta en la segunda página (cursor intacto, el «fantasma» sigue), y la segunda pasada reconcilia y lo quita.
2. **DELETE de rescate:** la lápida `pending` (con la versión del servidor) se escribe **antes** del DELETE y sin poder cancelarse; solo se quita si el servidor confirma (o responde `not_found`). Si falla, se reintenta en la siguiente ejecución. Prueba con el DELETE rechazado y luego aceptado.
3. **«Conservar la mía» ante una lápida remota:** conserva `createdAt` y `password_changed_at` originales (`VaultRepository.create(draft, createdAt, passwordChangedAt)`). Prueba en `ConflictResolverTest`.
4. **Cerrar sesión:** `signOutLocally` llama a `SyncShutdown.stop { clearAllTables() }`: cancela el job, quita el `SyncWorker` periódico, impide que cualquier disparador arranque otra pasada y ejecuta el borrado **dentro del mutex del motor** (`SyncEngine.whileIdle`). Prueba de que `whileIdle` espera a una subida en curso.

### Paso 2 · Generador (SCR-15, DEC-24)
- **`PasswordGenerator`** (`core/crypto`): solo `SecureRandom` con `nextInt(bound)` (sin sesgo de módulo), al menos un carácter de cada clase activa, Fisher-Yates, devuelve `CharArray`. Longitud 8–64 (16 por omisión), minúsculas siempre, símbolos `!@#$%^&*()-_=+[]{};:,.?/`, «Evitar parecidos» quita `l 1 I O 0 |`.
- **Opciones** en DataStore (`generator_length`, `generator_upper`, `generator_digits`, `generator_symbols`, `generator_avoid_ambiguous`); la contraseña **nunca** se guarda en disco y se genera de nuevo al abrir.
- **Pantalla** (Figma `46:996`): título en dos pesos, resultado en monoespaciada sobre la tarjeta oscura con medidor (barras de 28 dp y la etiqueta debajo), dado («Generar otra») y copiar; «Longitud» con deslizador (pista de 4 dp, disco de 24 dp); cuatro interruptores con el carácter que agregan (`A`, `1`, `#`, `Il`) y «Guardar en la bóveda». La pestaña **Generador** de la barra inferior ya navega (y **Bóveda** vuelve); Salud y Ajustes siguen como marcadores.
- **Copiar** usa `SecretClipboard` (DEC-25), con el aviso «Copiada · se borra del portapapeles en N s».
- **«Guardar en la bóveda»:** `PasswordHandoff` (memoria, una sola copia, se borra al tomarla o al bloquear, y solo se guarda con la bóveda abierta) → Nueva contraseña con el campo ya lleno; **no** viaja por la ruta ni por `SavedStateHandle`. Al salir sin guardar pide confirmación (la generada cuenta como cambio). Al guardar desde aquí se va a la Bóveda para ver la nueva.
- **«Generar» en Nueva/editar:** ya no está deshabilitado; abre el mismo generador en una **hoja inferior** (FLAG_SECURE de su ventana) con «Usar esta contraseña», que la devuelve al campo.
- **Seguridad de pantalla:** `SecureScreen()` en la pantalla y en la hoja; la contraseña se borra al salir de primer plano, al cerrar la hoja, tras «Usar» y al bloquear.
- **Accesibilidad:** la contraseña se anuncia carácter por carácter, el dado dice «Generar otra», el deslizador anuncia «N caracteres», las filas son interruptores de una pieza (TalkBack no lee los cuadros), botones de 48 dp (probado). **Animación:** al generar otra, opacidad ≤ 140 ms y el dado gira una vez; con «reducir movimiento» no se anima.

## Pruebas (salida real)
- **`testDebugUnitTest`: 479, 0 fallas.** Nuevas: `PasswordGeneratorTest` (10 000 generaciones por cada una de las 16 combinaciones: largo, clase por clase, nada de clases apagadas ni de parecidos; límites 8 y 64; chi-cuadrada dentro de cada clase; la primera posición toma las 4 clases), `GeneratorViewModelTest` (12: opciones recordadas, regenerar, copiar, entrega única, borrar al salir y al bloquear, sin `SavedStateHandle`), `SettingsStoreGeneratorTest` (4), `PasswordHandoffTest` (4), 2 de `ItemEditViewModelTest`, y las del Paso 1 en `SyncEngineTest` y `ConflictResolverTest`.
- **`connectedDebugAndroidTest`: 62 pruebas, 59 pasan y 3 fallan: las 3 de `ClipboardDeviceTest`.** Siguen fallando por el portapapeles compartido del emulador («host clipboard» pisa lo copiado). Desactivar «Enable clipboard sharing» es un ajuste del emulador que no cambié desde aquí; no toqué las pruebas, como indicaba la tarea. Quedan para repetirlas con eso desactivado (E3 de `revisiones-pendientes.md`). Nuevas que pasan: `GeneratorScreenTest` (6: textos y estructura del Figma, anuncio carácter por carácter, el dado/copiar/guardar/interruptores responden, deslizador, botones ≥ 48 dp, hoja con «Usar esta contraseña»).
- **`lintDebug`: 0 errores. `assembleRelease`: compila.**

## En el emulador
Hice una corrida punta a punta con la **app real** (Hilt, navegación, DataStore, Room, backend falso): Desbloquear → Bóveda → pestaña Generador → generar (16) → dado → apagar «Símbolos» → copiar (aviso con 30 s) → «Guardar en la bóveda» → Nueva contraseña con la máscara de 16 puntos. **La dejé fuera del repositorio** porque era inestable: a veces la prueba de Compose se queda esperando a que la app «esté en reposo» (hay animaciones sin fin en el arranque) y nunca terminaba; una corrida pasó, otras se colgaron en el primer paso. No dejé a medio camino ni una prueba que se cuelgue ni código de depuración para ella. **Sin verificar punta a punta en la app real:** guardar el elemento y verlo en la lista, y la hoja «Generar» dentro de Nueva (la hoja y «Usar esta contraseña» sí están probadas como componente, y la entrega por `PasswordHandoff` y el VM de Nueva por pruebas JVM). Conviene que la persona del equipo pruebe esas dos a mano en el Redmi.
- Las capturas de las pantallas (la secuencia con FLAG_SECURE da negro) salen de `GeneratorScreenTest`, que dibuja las pantallas sin `SecureScreen`; no se quitó ninguna protección. Para esa corrida puse las animaciones del emulador en 0 y **ya las devolví a 1**.

## Revisión con `security-reviewer`
Sin hallazgos críticos ni altos. El generador es correcto en lo criptográfico. **Corregí:**
- (Medio) `stop()` ahora ejecuta `clearAllTables()` **dentro** del mutex del motor y bloquea los disparadores mientras se cierra (antes el borrado corría fuera de la exclusión).
- (Medio) El rescate del DELETE: la lápida se escribe antes y sin poder cancelarse (antes una cancelación podía dejar al elemento eliminado reapareciendo).
- (Medio) «Usar esta contraseña» no borraba lo generado de la hoja y el ViewModel se quedaba con ella: ahora `onHide()` tras entregarla y al salir de composición.
- (Bajo) `PasswordHandoff.put` solo con la bóveda abierta y `ItemEditViewModel` la limpia en `onCleared` si nadie la tomó (doble toque); la descripción hablada se calcula una vez por contraseña y la opacidad va en `graphicsLayer` (menos copias); «Conservar la mía» ante lápida quita el elemento recién creado si no se pudo resolver (sin duplicados al reintentar).

**Quedan abiertos (anotados, no corregidos):**
- (Bajo) `session.lock()` va al final de `signOutLocally`: la llave sigue en memoria mientras espera el fin de una sincronización en curso. No lo moví a antes porque el `LockGuard` mandaría a Desbloquear en vez de Iniciar sesión (todavía hay cuenta local); si el equipo prefiere lo contrario, se cambia el orden.
- (Bajo) Un efecto `Use` pendiente en el canal del `GeneratorViewModel` podría repetirse si la hoja se cierra justo antes de recogerlo (ventana mínima; se limpia con `onHide`).
- Rotación en Nueva con la generada: se pierde (es de un solo uso por diseño); el formulario arranca vacío.
- Sesgo leve e inevitable a favor de las clases pequeñas por «al menos uno de cada clase» (lo pide DEC-24).

## Decisiones y desviaciones a revisar
- En la tarjeta oscura el medidor usa barras en **amarillo** y la etiqueta en claro, como en Figma `46:1022` (en los formularios el medidor sigue sin amarillo, DEC-50).
- El Generador se resuelve con **un solo interruptor «Evitar parecidos»** sobre las 4 clases; «Minúsculas» no se puede apagar.
- Interruptor (no está en Figma el estado «apagado»): encendido en `primary` con disco `on-primary`; apagado gris (`outline` al 60 %).
- Al guardar desde el Generador se va a la **Bóveda** (no vuelve al Generador).
- `LifecycleEventEffect(ON_STOP)`: al abrir Nueva desde el Generador, este se vacía (la contraseña ya se entregó) y al volver genera otra.

## Vistas nuevas (cómo llegar)
- **Generador (SCR-15):** barra inferior → «Generador», desde cualquier pantalla con barra.
- **Hoja del generador:** Nueva contraseña (o Editar) → botón del dado «Generar contraseña» junto al ojo.
- **Nueva con la contraseña puesta:** Generador → «Guardar en la bóveda».
