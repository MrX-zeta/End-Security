# Tarea 24 · Desbloquear con el teclado y transiciones entre pantallas

**Estado:** hecha
**Escrita:** 2026-11-05 · sesión de diseño

## Revisión de la tarea 23
Aprobada. Buen trabajo, sobre todo con:
- la auditoría del teclado con teclado real y dos tamaños;
- la medición del retraso (784 → 80 ms) y su causa: la transición por defecto de 700 ms del `NavHost`;
- `SECRET_ROUTES` y `clearSavedTabStates`;
- los tres hallazgos medios de `security-reviewer` (sobre todo M2: `HealthViewModel` vivo con `saveState`);
- `docs/api/brechas-backend.md`.

Aprobadas tus desviaciones (resumen amarillo sólido, secciones planas, «Ver», `internal` para la auditoría).

**El equipo lo probó en el Redmi y faltan dos cosas.** Las dos son esta tarea.

## Paso 1 · Desbloquear: la huella a la izquierda y los botones justo encima del teclado
**Qué pasa hoy:**
- la huella quedó **debajo** de «Desbloquear», centrada;
- con el teclado abierto **desaparece**;
- «Desbloquear» **sube demasiado**: queda muy lejos del teclado.

**Cómo debe quedar** (Figma `36:491`, contenedor «Acciones» `36:553`):
- **Una sola fila «Acciones»**, 56 dp de alto:
  - botón de huella cuadrado de **56 × 56** a la izquierda;
  - **12 dp** de separación;
  - «Desbloquear» ocupando el resto del ancho;
  - la fila va **28 dp** debajo del campo de contraseña.

  Sin huella activa o sin sensor, «Desbloquear» ocupa todo el ancho. Esto vale **con y sin teclado**: la huella **nunca** se oculta ni cambia de lugar.
- **Con el teclado abierto:**
  - la fila completa (huella + «Desbloquear») queda **entera y visible**, con su borde inferior a **16 dp ± 2 dp** del borde superior del teclado;
  - **no más arriba**: el contenido solo sube lo necesario para que la fila no quede tapada;
  - si la fila ya queda por encima del teclado sin moverse (pantallas altas), **no se mueve nada**.
- **Lo que no cabe se va primero, en este orden:**
  1. la cabecera decorativa (manchas y «End Security»);
  2. la línea «¿No eres …? Cerrar sesión», que queda detrás del teclado y vuelve al cerrarlo.

  El título, el texto, el campo y la fila «Acciones» se ven siempre.
- **El movimiento va sincronizado con el teclado, cuadro por cuadro:**
  - usa los insets animados del IME (`WindowInsets.ime` / `imePadding()` con `WindowCompat.setDecorFitsSystemWindows(false)` y `adjustResize`, que en Android 11+ siguen la animación del teclado);
  - nada de saltos al final ni de esperar a la altura final: la fila sube **a la par** del teclado y baja a la par al cerrarlo;
  - no hagas una animación propia encima.
- **Prueba de medida** (amplía `KeyboardAuditTest`) a **412 × 891** y **360 × 640**, con la huella visible:
  - con teclado: los dos botones completos; distancia entre el borde inferior de la fila y el teclado entre 14 y 18 dp; la huella a la izquierda de «Desbloquear» y a su misma altura;
  - sin teclado: la posición coincide con Figma (28 dp bajo el campo).
- Capturas antes y después en el reporte.
- Revisa **Iniciar sesión** con la misma regla: el botón principal a 16 dp sobre el teclado, sin subir de más. Si ya cumple, déjalo.

## Paso 2 · Transiciones entre pantallas
Al quitar los 700 ms, el cambio quedó **brusco**. Se quiere una transición corta que dé continuidad **sin volver a sentirse lenta**. Las reglas siguen el criterio de movimiento de Apple (*Designing Fluid Interfaces*) y el de Emil Kowalski:
- duraciones **por debajo de 300 ms**;
- curva **ease-out** (entra rápido y frena);
- **mismo camino al entrar y al salir**;
- interrumpible;
- en lo que se usa muchas veces al día, lo más discreto.

**Curva única para toda la app:** `EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)`. Ponla en `core/designsystem` como `Motion.EaseOut`, junto con las duraciones de abajo como constantes (`Motion.TabFade`, `Motion.Push`…); no las repitas en cada pantalla.

| Caso | Transición |
|---|---|
| **Cambio de pestaña** (Bóveda, Salud, Generador, Ajustes) | **Fundido cruzado de 150 ms** con `Motion.EaseOut`: la que entra hace `fadeIn` y la que sale `fadeOut` (90 ms). **Sin desplazamiento:** las pestañas son hermanas, no hay dirección. Es lo que más se usa: tiene que sentirse inmediato. |
| **Avanzar dentro de una pestaña** (Bóveda → detalle, Ajustes → Dispositivos, etc.) | **Eje horizontal compartido:** la nueva entra desde **+30 dp a 0** con `fadeIn`, y la anterior sale de **0 a −30 dp** con `fadeOut`. **240 ms** la que entra y **200 ms** la que sale, con `Motion.EaseOut`. No desplaces la pantalla completa de lado a lado: 30 dp bastan para indicar dirección. |
| **Regresar** (Atrás o el gesto) | El **mismo camino al revés**: la actual sale a **+30 dp** con `fadeOut` y la anterior vuelve desde **−30 dp**. Mismas duraciones y curva. |
| **Bloquear** (candado, autobloqueo) y **entrar/salir de sesión** (Iniciar sesión ↔ Bóveda, Cuenta eliminada) | **Fundido de 200 ms**, sin desplazamiento: es un cambio de estado, no de jerarquía. |
| **Hojas inferiores y diálogos** | Sin cambios (`ModalBottomSheet` ya trae su animación). |
| **Splash → primera pantalla** | Sin cambios. |

**Gesto Atrás predictivo (Android 14+):**
- activa `android:enableOnBackInvokedCallback="true"` en el manifiesto;
- usa la versión de Navigation Compose que lo soporta (2.8 o mayor), para que el gesto de regreso **siga el dedo** y se pueda cancelar a medias, con las mismas transiciones de «Regresar» (`popEnter`/`popExit`);
- si `BackHandler` en alguna pantalla (hojas, pasos de 2FA, eliminar cuenta) choca con el gesto, que gane el `BackHandler`;
- prueba el gesto cancelado (empezarlo y soltar antes): la pantalla vuelve a su lugar sin parpadeo.

**Reglas:**
- **Interrumpible:** si se toca otra pestaña o Atrás a mitad de una transición, la nueva parte del estado actual. No hay bloqueo de toques durante la transición.
- **«Quitar animaciones» del teléfono** (Accesibilidad, o `ANIMATOR_DURATION_SCALE = 0`): todo pasa sin transición. Compruébalo y agrega una prueba que lea la escala.
- **Rendimiento:** solo `alpha` y `translationX` (`graphicsLayer`); nada que cambie el layout durante la transición.
- **Pantallas con `SecureScreen`:** la transición no debe dejar ver ni un cuadro de la pantalla segura en la vista de recientes. Revisa que `FLAG_SECURE` se ponga antes de que entre la pantalla y se quite después de que salga.
- **Medida:** que `TabSwitchTimingTest` siga pasando con la nueva transición. Un cambio de pestaña debe terminar en **≤ 200 ms** (antes 80 ms; 784 ms con el fundido por defecto). Agrega una prueba equivalente para avanzar y regresar (≤ 260 ms).

**Prueba de sensación (en el reporte):**
- graba la pantalla del emulador con `adb shell screenrecord` y revísala cuadro por cuadro con la escala de animación del sistema en **5×** (Opciones de desarrollador);
- confirma que avanzar y regresar recorren el mismo camino y que no hay saltos al interrumpir.

Deja anotada como prueba a mano en el Redmi la sensación con el gesto real.

## Terminado cuando
- **Paso 1:**
  - la prueba de medida a los dos tamaños (con y sin teclado, con y sin huella);
  - las capturas;
  - Iniciar sesión revisado.
- **Paso 2:**
  - `Motion` en el sistema de diseño;
  - las transiciones de la tabla;
  - el gesto predictivo;
  - «Quitar animaciones»;
  - las pruebas de tiempo;
  - la grabación revisada.
- `testDebugUnitTest`, la suite completa de `connectedDebugAndroidTest` (salvo E3, en tandas), lint y `assembleRelease` pasan. Commits hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
