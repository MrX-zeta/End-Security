# Tarea 29 · Aviso al copiar dentro de las hojas (última del frontend)

**Estado:** hecha
**Escrita:** 2026-11-07 · sesión de diseño

## Revisión de la tarea 28
Aprobada:
- `DirectionalContentSwap` envuelve en `Column`, así que los pasos del 2FA ya no se enciman;
- el selector tiene celdas iguales, el texto centrado y en una línea, y el texto se reduce antes de partirse;
- «Mejoradas»;
- los errores de campo se despliegan.

Bien la medición de tiempo con el mejor de varias corridas.

## El problema
En la **hoja del Generador** que se abre desde Nueva/Editar contraseña («Usar esta contraseña»), al tocar **copiar** no aparece ningún aviso; el equipo tuvo que tocar varias veces para saber si se copió.
- **Causa probable:** `GeneratorScreen` pide `HostedMessage`, pero el anfitrión de avisos (tarea 26) se dibuja en la ventana principal, y la `ModalBottomSheet` es **otra ventana encima**: el aviso sale, pero detrás de la hoja.
- En la pestaña Generador sí se ve, porque ahí no hay hoja.

## Paso 1 · Avisos dentro de las hojas inferiores
- **Un anfitrión local por hoja:** cada `ModalBottomSheet` de la app provee su propio `MessageHostState` con un `CompositionLocal`. `HostedMessage` usa el **más cercano**, así que lo que se pide dentro de una hoja aparece **dentro de ella**.
  - **Posición:** encima de su botón inferior («Usar esta contraseña»), con 12 dp de separación.
  - **Animación:** la misma de la tarea 26: entra subiendo 16 dp en 200 ms y sale en 150 ms.
  - Al cerrarse la hoja, su anfitrión se limpia.
- Un componente común (`AppBottomSheet` o un envoltorio de `ModalBottomSheet`) para que ninguna hoja se quede sin él.
- **Revisa todas las hojas:**
  - Generador desde Nueva/Editar;
  - `ReauthSheet`;
  - las de cerrar sesión, dispositivos, conflictos y eliminar cuenta;
  - documentos legales y licencias en hoja;
  - bloqueo automático;
  - la de «¿Ya guardaste tus códigos?».

  Entrega en el reporte una tabla: hoja · ¿pide avisos? · ¿se ven ahora?

## Paso 2 · Confirmación en el propio botón
Además del aviso «Copiada · se borra del portapapeles en 30 s»:
- **el ícono de copiar** cambia a una **palomita** (fundido cruzado de 150 ms con `Motion.EaseOut`);
- se queda así **1,5 s** y vuelve al ícono de copiar;
- tocar otra vez mientras tanto vuelve a copiar y reinicia los 1,5 s.

Aplícalo a **todos** los botones de copiar:
- el Generador (pestaña y hoja);
- el detalle de una contraseña (usuario y contraseña);
- la clave y los códigos del 2FA.

**Accesibilidad:** el botón anuncia «Copiado» (`liveRegion` o `stateDescription`) y su descripción no cambia de nombre. Con «Quitar animaciones», el cambio de ícono es instantáneo.

## Paso 3 · Comprobar que la copia es sensible
El equipo vio que **el teclado sugiere la contraseña copiada**. Que Gboard ofrezca lo copiado es normal; lo que importa es que, con `EXTRA_IS_SENSITIVE` (Android 13+), la sugerencia y la vista previa del sistema la muestren **oculta** (puntos), no en claro.
- Confirma que **la hoja del Generador** copia por `SecretClipboard` (como la pestaña) y no por otro camino.
- Comprueba en el emulador (API 33+) que la vista previa del portapapeles del sistema no muestra el texto.
- Si en algún lugar se copia sin `SecretClipboard`, corrígelo.

Todo esto va al reporte, **sin capturas**: basta con decir qué se vio.

## Terminado cuando
- **Pruebas:**
  - un aviso pedido dentro de una hoja se muestra dentro de ella, encima de su botón, y no en el anfitrión principal;
  - al cerrar la hoja, su aviso desaparece;
  - el ícono pasa a palomita y vuelve a los 1,5 s, y reinicia con otro toque;
  - con «Quitar animaciones» no hay movimiento;
  - la hoja del Generador usa `SecretClipboard`.
- **Sin capturas.**
- La tabla de hojas.
- `testDebugUnitTest`, lint, `assembleRelease` y las pruebas instrumentadas de `feature.vault`, `feature.account` y del sistema de diseño pasan.
- Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
