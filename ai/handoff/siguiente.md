# Tarea 20 · Eliminar la cuenta (SCR-36, 37, 38; OP-28; EP-18)

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 19
Aprobada.
- **Dispositivos y Actividad** coinciden con Figma: «Este» sin acción, cerrar los demás en serie con éxito parcial, los 18 tipos de evento, agrupación por día local y paginación sin duplicados.
- **La recuperación en `login()`** quedó bien protegida:
  - las tres condiciones;
  - la cuenta se identifica por el correo tecleado (M1);
  - con la contraseña vigente no se manda nada (M2);
  - se vuelve a leer la fila dentro del candado (B2);
  - el paralelismo también se compara (B4).

Aprobadas tus desviaciones: el aviso de éxito total, el mismo panel de «sin conexión» para cualquier error, `hasMore` apagado con una página vacía y los enlaces con `LinkAnnotation`.

**Decisiones sobre lo abierto** (grafo v1.6.0):
- **M2, mitigaciones 2 y 3:** no se aplican. El riesgo queda documentado como **RSG-13**: no se cambia el contrato por tiempo.
- **B3:** de acuerdo, no se aplica (dejaría fuera a quien tenga la cuenta tras restaurar el servidor).
- **Alcance de la recuperación:** se acepta como **RSG-12**. Si la app muere en ese instante, este teléfono sigue con la contraseña vieja hasta cerrar sesión y entrar con la nueva. No hay que hacer nada más.

**Contrato 1.3.0** (ya en `docs/api`): límites del cambio de correo (DEC-63). No afecta esta tarea; es para la 21.

## Objetivo
Eliminar la cuenta desde Ajustes, con reautenticación y doble confirmación, y dejar el teléfono limpio.

**Figma:** Eliminar `47:2290`, confirmar (hoja) `62:3233`, cuenta eliminada `62:3270`.

## Paso 1 · Pantalla Eliminar (SCR-36)
- Título en dos pesos «**Eliminar tu cuenta** no se puede deshacer» y la tarjeta con las tres consecuencias de Figma:
  - «Se borrarán todas tus contraseñas de nuestros servidores.»
  - «Se cerrará la sesión en todos tus dispositivos.»
  - «Tu historial quedará anónimo.»
- «Contraseña maestra» (`PasswordField`) y «Código de tu app» **solo si la 2FA está activa** (Figma siempre lo muestra).
- Botón «Eliminar mi cuenta» (peligro), siempre activo, con los errores de la tarea 15 para los campos vacíos o incompletos. Al tocarlo con todo bien **no** llama al servidor todavía: abre la hoja (Paso 2).
- `SecureScreen()` y `ExcludeFromAutofill`. La contraseña vive solo en el `TextFieldState`, como en `ReauthSheet`.
- En Ajustes, «Eliminar cuenta» deja de decir «Disponible pronto». Sin red: `MessageBar` «Necesitas conexión para eliminar tu cuenta.» y no navega.

## Paso 2 · Confirmación (SCR-37)
- Hoja `62:3233`: «¿Eliminar tu cuenta para siempre?» y «Se borrarán tus N contraseñas de nuestros servidores y se cerrará la sesión en todos tus dispositivos. No podrás recuperarlas.» (N = elementos locales no borrados, con plural; con 0: «Se borrará tu cuenta de nuestros servidores…»). Botones «Sí, eliminar mi cuenta» (peligro) y «Cancelar».
- Al confirmar:
  - deriva la `auth_key` (reutiliza `ReauthRunner`) y llama **EP-18** con `Reauth`;
  - el botón queda cargando, la hoja no se cierra y Atrás está deshabilitado;
  - desde que se manda EP-18 hasta terminar la limpieza local, todo corre en `NonCancellable` (como A1 de la tarea 18).
- **Errores:**
  - `reauth_required`: se cierra la hoja y vuelve a la pantalla con «Contraseña o código incorrectos.» marcando los campos (con la regla de la tarea 19 si la `key_version` del servidor es mayor);
  - límite de intentos: la cuenta regresiva de siempre;
  - sin red o 5xx: el genérico en la hoja, sin tocar nada local.

## Paso 3 · Limpieza local y pantalla final (SCR-38) — **REQUIERE REVISIÓN HUMANA**
- **Con el 204, borra todo lo de la cuenta** (reutiliza `signOutLocally` y agrega lo que falte):
  - tokens;
  - Room completo;
  - llaves biométricas del Keystore y la bandera;
  - `vault_key` de la sesión;
  - portapapeles si contiene algo copiado por la app;
  - intentos de desbloqueo;
  - opciones de DataStore ligadas a la cuenta (generador, bloqueo automático, portapapeles), que vuelven a los valores por defecto. **Se conserva** «onboarding visto».
- **«Cuenta eliminada» (`62:3270`):**
  - fuera de `RequireUnlocked`;
  - con la pila de navegación limpia;
  - «Tu cuenta fue eliminada», «Borramos tus contraseñas de nuestros servidores. Gracias por haber usado End Security.» y «Ir al inicio» → Iniciar sesión;
  - Atrás del sistema hace lo mismo.
- **Si el servidor respondió 204 pero falla parte de la limpieza:** termina igual en «Cuenta eliminada» (la cuenta ya no existe). Lo que no se pudo borrar se reintenta al siguiente arranque: deja una marca en DataStore.

## Backend falso
EP-18 en `FakeServer`, **solo el backend falso**:
- reautenticación;
- baja lógica (`deleted_at`);
- borra elementos, 2FA, códigos y verificaciones;
- revoca **todas** las sesiones;
- registra `account_deleted`;
- después, `prelogin`, `login` y `register` con ese correo se comportan como en una cuenta inexistente (anti-enumeración: mismos tiempos y cuerpo).

## Terminado cuando
- **Pruebas de ViewModel:**
  - campos vacíos o incompletos;
  - código solo con 2FA;
  - la hoja con N y plural;
  - `reauth_required` vuelve a la pantalla marcada;
  - sin red no toca nada local;
  - cancelar a mitad no deja el servidor eliminado con el teléfono intacto (`NonCancellable`).
- **Pruebas de la limpieza:** tras el 204 no queda nada de lo de la lista y se conserva «onboarding visto»; la marca de limpieza pendiente se reintenta al arrancar.
- **Backend falso:** el flujo con y sin 2FA; la otra sesión queda revocada; un login posterior se comporta como una cuenta inexistente.
- **Pruebas de Compose:** textos de Figma de las tres vistas; Atrás en «Cuenta eliminada».
- **En el emulador:** crea una cuenta de prueba con elementos, elimínala y comprueba que vuelve a Iniciar sesión y que no puede entrar con ella. Lo que no se pueda, al reporte como prueba a mano.
- `security-reviewer` revisa los Pasos 2 y 3. **REQUIERE REVISIÓN HUMANA.**
- `testDebugUnitTest`, `connectedDebugAndroidTest` (salvo E3), lint y `assembleRelease` pasan. Commits hechos y subidos (incluye `docs/api` y `docs/backend` con el contrato 1.3.0 y el grafo v1.6.0, en un commit `docs:` aparte).

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
