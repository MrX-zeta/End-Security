# Tarea 13 · Cabos de la sincronización y Generador (SCR-15, OP-08)

**Estado:** hecha
**Escrita:** 2026-10-22 · sesión de diseño

## Revisión de la tarea 12
Aprobada, con la revisión humana de `SyncEngine`, `ConflictResolver` y el SQL. Muy bien:
- reconocer el eco de una subida cuya respuesta se perdió, sin marcarla como conflicto;
- no cancelar una sincronización empezada;
- comparar `local_seq` antes de «Conservar la del servidor»;
- no quitar FLAG_SECURE para hacer capturas.

También están aprobadas tus desviaciones: «En otro dispositivo», las negritas en lo que difiere, los botones cuando hay lápida, `ACCESS_NETWORK_STATE`, WorkManager 2.10.2 sin `hilt-work` y que un 5xx corte la ejecución.
- **`ClipboardDeviceTest`:** es el portapapeles compartido del emulador. Desactiva «Enable clipboard sharing» en la configuración del emulador y vuelve a correrlas. No cambies las pruebas.
- **La secuencia por interfaz con el comando de depuración** la va a probar la persona del equipo.

## Paso 1 · Cabos abiertos de la tarea 12
1. **Descarga completa tras `410`:** el cursor no avanza hasta la última página. Si se interrumpe a la mitad, la siguiente ejecución empieza la descarga completa desde cero. Agrega una prueba que corte a la mitad y no deje «fantasmas».
2. **DELETE de rescate:** si falla, la fila **se queda como lápida `pending`** y se reintenta en la siguiente ejecución. Ya no se ignora el resultado.
3. **«Conservar la mía» ante una lápida remota:** conserva `createdAt` y `password_changed_at` originales. Agrega ese parámetro de fechas a `VaultRepository.create`.
4. **Cerrar sesión:** llama a `SyncWorker.cancel()` y a la cancelación del `SyncScheduler` antes de `clearAllTables()`.

## Paso 2 · Generador (SCR-15, DEC-24 actualizado en el grafo v1.5.6)
**Figma:** `46:996`. El título es en dos pesos («Generador» / «crea una contraseña segura»). Debajo van:
- la contraseña generada en monoespaciada, con el **dado** para generar otra y el botón de copiar;
- el medidor;
- «Longitud · 16 caracteres» con un deslizador;
- los interruptores con el carácter que agregan (DEC-59): «Mayúsculas (A–Z)», «Números (0–9)», «Símbolos (!@#)» y «Evitar parecidos · Como l, 1, O y 0»;
- «Guardar en la bóveda»;
- la barra inferior con **Generador** activo.

**Reglas (DEC-24):**
- `PasswordGenerator` en `core/crypto`:
  - solo `SecureRandom`, sin sesgo de módulo (`nextInt(bound)`);
  - **al menos un carácter de cada clase activa**;
  - mezcla final con Fisher-Yates.
- **Longitud** de 8 a 64, 16 por defecto. **Minúsculas siempre.** Mayúsculas, números y símbolos activados por defecto.
- No se puede apagar la última clase opcional junto con todo lo demás: siempre quedan las minúsculas, así que nunca hay alfabeto vacío.
- **«Evitar parecidos»** quita `l 1 I O 0 |`.
- **Símbolos:** `!@#$%^&*()-_=+[]{};:,.?/` (sin comillas, barra invertida ni espacio, para que no fallen en formularios).
- **Las opciones se recuerdan** en DataStore (`generator_length`, `generator_upper`, `generator_digits`, `generator_symbols`, `generator_avoid_ambiguous`). **La contraseña generada nunca se guarda en disco** y se vuelve a generar al abrir.
- **Copiar** usa `SecretClipboard` (DEC-25).
- **El medidor** usa el mismo estimador.
- **«Guardar en la bóveda»** abre Nueva contraseña con el campo de contraseña lleno. Pasa el valor en memoria (un `SecureBytes`/`CharArray` de un solo uso), **no** por argumentos de navegación ni `SavedStateHandle`.
- **Desde Nueva/editar,** «Generar» (hoy deshabilitado) abre el mismo generador en una **hoja inferior** con «Usar esta contraseña», que la devuelve al campo.
- `SecureScreen()` en el generador y en la hoja. La contraseña generada se borra de la pantalla al salir de primer plano.
- La pestaña Generador de la barra inferior ya navega aquí. **Salud y Ajustes** siguen como marcadores.
- **Accesibilidad:**
  - la contraseña se anuncia carácter por carácter cuando se enfoca (`contentDescription` con los caracteres separados);
  - el dado dice «Generar otra»;
  - el deslizador anuncia la longitud.
- **Animación:** al generar otra, un cambio breve de opacidad en la contraseña (≤ 150 ms), con respeto a «reducir movimiento». El dado puede girar una vez. Usa `/animate` si lo necesitas.

## Terminado cuando
- **Pruebas de `PasswordGenerator`:**
  - 10 000 generaciones con cada combinación: largo exacto, al menos una de cada clase activa y nunca caracteres de clases apagadas ni parecidos con la opción activa;
  - distribución aproximadamente uniforme por carácter (chi-cuadrada con tolerancia amplia), sin sesgo evidente;
  - 8 y 64 en los límites.
- **Pruebas del ViewModel:** opciones recordadas; la contraseña no va al estado guardable; «Guardar en la bóveda» entrega el valor una sola vez.
- **Pruebas del Paso 1** (410 interrumpido, rescate fallido, fechas conservadas, cancelar al cerrar sesión).
- **En el emulador:** generar, cambiar opciones, copiar, guardar en la bóveda, usar «Generar» desde Editar.
- `security-reviewer` revisa el generador y el Paso 1. **REQUIERE REVISIÓN HUMANA.**
- `assembleRelease` y lint pasan. Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
