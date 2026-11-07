# Tarea 25 · Transiciones más notorias y barra inferior animada

**Estado:** hecha
**Escrita:** 2026-11-06 · sesión de diseño

## Revisión de la tarea 24
Aprobada:
- `KeyboardAwareLayout` lee el IME en la fase de colocación, así que sube con el teclado cuadro por cuadro;
- la fila de Desbloquear coincide con Figma y queda a 16 dp del teclado;
- Iniciar sesión cumple la misma regla;
- `Motion` y `NavMotion` aplican una sola política;
- regreso predictivo;
- «Quitar animaciones»;
- tiempos medidos;
- diff limpio, sin CRLF.

Aprobadas tus desviaciones, incluida la salida instantánea (`fadeOut(snap())`) al bloquear. Tenías razón: la escala 5× no afecta a Compose.

El equipo ya vio las transiciones en el teléfono y las quiere **un poco más notorias, sin que se sientan lentas**. También quiere animar la barra inferior.

## Paso 0 · `security-reviewer` de la tarea 24
Pásale lo que no se revisó:
- `UnlockScreen` con `KeyboardAwareLayout`: el campo de la contraseña maestra, `ExcludeFromAutofill` y que nada se mida o registre con el contenido;
- el orden de `FLAG_SECURE` respecto a las transiciones. Al entrar a una pantalla con `SecureScreen`, la bandera debe estar puesta **antes** del primer cuadro de la transición. Al salir hacia una pantalla sin ella, debe quitarse **después** del último cuadro, para que ni la vista de recientes ni una captura alcance un cuadro intermedio con secretos.

Corrige lo que encuentre.

## Paso 1 · Transiciones al avanzar y regresar, un poco más notorias
Solo cambian valores en `Motion.kt`; la política (`NavMotion`) y las pantallas no se tocan.

| Constante | Antes | Ahora |
|---|---|---|
| `Slide` | 30 dp | **60 dp** |
| `Push` (la que entra) | 240 ms | **260 ms** |
| `PushExit` (la que sale) | 200 ms | **220 ms** |
| `PushLimitMillis` (prueba) | 260 | **280** |
| `TabFade` / `TabFadeOut` / `StateFade` | 150 / 90 / 200 | **sin cambio** |

Misma curva `EaseOut`. Actualiza los comentarios y las pruebas de tiempo. Revisa a ojo que regresar con el gesto predictivo siga viéndose bien con 60 dp.

## Paso 2 · Barra inferior: la píldora se desliza
Hoy la píldora negra de la pestaña activa aparece de golpe en la nueva. Debe **deslizarse** desde la pestaña anterior hasta la nueva.
- **Una sola píldora** (44 × 36, `ActiveIconShape`, `colorScheme.onSurface`) dibujada **detrás** de la fila de pestañas, en lugar de un fondo por pestaña. Su posición horizontal se anima al centro del ícono de la pestaña seleccionada. Mide la posición de cada ícono con `onGloballyPositioned` o con un `Layout` propio; no la calcules con números fijos.
- **Animación:**
  - resorte **sin rebote**: `spring(dampingRatio = 1f, stiffness = 440f)`, que llega en unos 250 ms (Apple usa *damping 1.0, response 0.3* para mover algo de un lugar a otro sin un gesto que lo lance);
  - con `Animatable` y `animateTo`: si se toca otra pestaña a la mitad, cambia de rumbo **desde donde va**, sin saltos y conservando la velocidad;
  - solo `translationX` (`graphicsLayer` u `offset { }` en la fase de colocación), sin cambiar el layout.
- **Color del ícono:**
  - con `animateColorAsState`, de `onSurfaceVariant` a `background` (blanco sobre la píldora) en **150 ms** con `Motion.EaseOut`;
  - el de la pestaña que se deja hace lo inverso.

  Así el ícono «se enciende» cuando la píldora llega, en lugar de verse blanco sobre fondo claro mientras la píldora aún viene.
- **La etiqueta** de abajo no se mueve. Su peso y color cambian como ahora; el color también con 150 ms.
- **Primer dibujo y regreso desde otra pantalla:** la píldora aparece **ya en su lugar**, sin deslizarse (por ejemplo, al abrir la app o al volver de Desbloquear). Solo se desliza cuando cambia la pestaña con la barra visible.

## Paso 3 · Barra inferior: «pulso» del ícono seleccionado
- Cuando una pestaña **pasa a estar seleccionada**, su ícono hace un pulso de escala **0,92 → 1,0** en **200 ms** con `Motion.EaseOut` (`graphicsLayer { scaleX; scaleY }`), a la par de la llegada de la píldora.
- No pulsa en el primer dibujo ni al volver a la misma pestaña. Es igual para las cuatro pestañas; sin giros.

## Reglas
- **«Quitar animaciones»** (`rememberReducedMotion()`): la píldora salta a su lugar, el color cambia al instante y no hay pulso.
- **Accesibilidad:** no cambian `Role.Tab`, el estado seleccionado, el área táctil de 76 × 54, ni que no haya onda al tocar.
- **Rendimiento:** la barra no debe recomponer las cuatro pestañas en cada cuadro de la animación. La posición se lee en la fase de colocación o de dibujo, no en la composición.
- La navegación sigue igual: tocar una pestaña navega en el mismo momento. La animación de la barra corre en paralelo con el fundido de 150 ms del contenido; no se espera una a la otra.

## Terminado cuando
- **Paso 0** con los hallazgos corregidos.
- **Pruebas** (reloj de Compose controlado):
  - las transiciones con los nuevos valores (≤ 280 ms al avanzar y regresar; ≤ 200 ms al cambiar de pestaña);
  - **la píldora:** termina centrada en la pestaña nueva; a la mitad está entre las dos; al tocar otra a la mitad, sigue sin saltar (comprobar que la posición es continua entre dos cuadros); en el primer dibujo ya está en su lugar;
  - **el pulso:** ocurre al seleccionar y no en el primer dibujo;
  - con «Quitar animaciones» no hay movimiento;
  - las pruebas existentes de `BottomNavBarTest` siguen pasando.
- **Grabación** con `adb shell screenrecord` de Bóveda → Salud → Generador → Ajustes y de toques rápidos seguidos. Revísala cuadro por cuadro y descríbela en el reporte.
- `testDebugUnitTest`, la suite completa de `connectedDebugAndroidTest` (salvo E3, en tandas), lint y `assembleRelease` pasan. Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
