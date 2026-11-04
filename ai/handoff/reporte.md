# Reporte · Tarea 20 · Eliminar la cuenta (SCR-36, 37, 38; OP-28; EP-18)

**Estado:** hecha
**Fecha:** 2026-11-04

**REQUIERE REVISIÓN HUMANA:** los Pasos 2 y 3 tocan autenticación y borrado de datos: `DeleteAccountImpl`, `AuthRepositoryImpl` (`signOutLocally`, `wipeAfterAccountDeletion`, `completePendingWipe`), `AccountDeletionGate`/`LockGuard`, `SettingsStore` (marca de limpieza pendiente) y `AccountDao`/`VaultItemDao` (consulta nueva). Pasaron por el subagente `security-reviewer` (ver abajo): **encontró un fallo grave del flujo y un riesgo de borrar una cuenta nueva; los dos quedaron corregidos, pero conviene que una persona los lea.**

Antes de empezar se subió en un commit aparte (`docs:`) el contrato 1.3.0, el grafo v1.6.0, `docs/backend` y `revisiones-pendientes.md`.

## Qué se hizo

### Paso 1 · Pantalla Eliminar (SCR-36; Figma `47:2290`)
- **`DeleteAccountScreen`** sobre `FlowScaffold` (título «Eliminar cuenta»): título en dos pesos «**Eliminar tu cuenta** / no se puede deshacer», tarjeta roja con las tres consecuencias (basura, cerrar sesión, usuario), «Contraseña maestra» (`PasswordField`) y «Código de tu app» **solo con la 2FA activa** (Figma lo muestra siempre), y el botón «Eliminar mi cuenta» (peligro), **siempre activo**.
- **Errores de la tarea 15:** contraseña vacía y código incompleto se marcan con el foco en el primero; **con todo bien no llama al servidor**: cuenta los elementos locales no borrados (`VaultItemDao.visibleCount`) y abre la hoja.
- `SecureScreen()` y `ExcludeFromAutofill()`; la contraseña vive solo en un `TextFieldState` (sin `rememberSaveable`), se vacía al salir y al pasar a segundo plano (si la eliminación está en curso, en cuanto termine).
- **Ajustes:** «Eliminar cuenta» abre la pantalla. Sin red (no se puede preguntar al servidor por la 2FA) sale el `MessageBar` «Necesitas conexión para eliminar tu cuenta.» y no navega. Se quitaron `FutureRow`, `FutureRowClicked` y «Disponible pronto»: ya no queda ninguna fila futura.

### Paso 2 · Confirmación (SCR-37; Figma `62:3233`)
- **Hoja** «¿Eliminar tu cuenta para siempre?» con «Se borrarán tus **N** contraseñas de nuestros servidores y se cerrará la sesión en todos tus dispositivos. No podrás recuperarlas.» (singular con 1; con 0: «Se borrará tu cuenta de nuestros servidores…»), «Sí, eliminar mi cuenta» (peligro) y «Cancelar». La hoja lleva `SecureOn`.
- **Al confirmar** (`DeleteAccountImpl`): reautentica con `ReauthRunner` (la `auth_key` se deriva en el teléfono y se borra al terminar) → **EP-18** → con el 204, la limpieza y el aviso a la navegación. **Desde que se manda EP-18 hasta terminar la limpieza todo corre en `NonCancellable`** (como A1 de la tarea 18). El botón carga, la hoja no se cierra (ni por gesto ni por Atrás) y Atrás de la pantalla está deshabilitado.
- **Errores:** `reauth_required` cierra la hoja y vuelve a la pantalla con «Contraseña o código incorrectos.» marcando los campos (y, con la regla de la tarea 19, el mensaje del 409 si la `key_version` del servidor es mayor); el límite de intentos usa la cuenta regresiva de siempre; sin red o 5xx el genérico queda **dentro de la hoja** y no se toca nada local.

### Paso 3 · Limpieza local y «Cuenta eliminada» (SCR-38; Figma `62:3270`)
- **`AuthRepository.wipeAfterAccountDeletion()`** reutiliza `signOutLocally` (que ahora devuelve si **todos** sus pasos salieron bien) y agrega lo que faltaba. Con el 204 queda borrado: tokens (y su llave del Keystore), Room completo (`clearAllTables`, con la sincronización en reposo), llaves biométricas y su bandera, la `vault_key` de la sesión, el portapapeles si tiene algo copiado por la app, los intentos de desbloqueo y las opciones de DataStore ligadas a la cuenta (generador, bloqueo automático, portapapeles; vuelven a su valor por defecto). **Se conserva «onboarding visto».**
- **Marca de limpieza pendiente:** si el servidor respondió 204 pero parte de la limpieza falla, igual termina en «Cuenta eliminada» (`Deleted(cleanupComplete = false)`) y queda una marca en DataStore con el `user_id` de la cuenta eliminada. **`StartDestinationViewModel` la reintenta antes de decidir la primera pantalla** (`completePendingWipe`).
- **«Cuenta eliminada»** (`AccountDeletedScreen`): fuera de `RequireUnlocked`, con la pila limpia; disco con la palomita, «Tu cuenta fue eliminada», «Borramos tus contraseñas de nuestros servidores. Gracias por haber usado End Security.» y «Ir al inicio» → Iniciar sesión. Atrás del sistema hace lo mismo.
- **Cómo se navega** (arreglo de la revisión): ver abajo. La navegación a SCR-38 **no** la hace la pantalla de Eliminar.

### Backend falso (solo el falso)
EP-18 en `FakeServer` ya reautenticaba, daba de baja (`deleted`), borraba elementos, 2FA y verificaciones y revocaba **todas** las sesiones; faltaba registrar **`account_deleted`** (ahora con el dispositivo que lo pidió). Después, `prelogin` da la sal falsa de DEC-07, `login` calcula el mismo Argon2 y responde `invalid_credentials` y `register` puede volver a crear una cuenta con ese correo, igual que con uno inexistente.

## Revisión con `security-reviewer` (Pasos 2 y 3)
Confirmó que la cripto y el manejo de la contraseña están bien (campo en `TextFieldState`, `SecureScreen`, `auth_key` borrada en `finally` y al cancelar, `toString()` sin secretos) y que todo lo ligado a la cuenta queda cubierto (revisó todas las `*PreferencesKey` del proyecto). Hallazgos y qué se hizo:
- **1 (alta) · pantalla en blanco, nunca llegaba a SCR-38.** `DeleteAccountRoute` está dentro de `RequireUnlocked`: la propia limpieza bloquea la bóveda, la pantalla sale de la composición y el efecto `Deleted` caía en un canal sin colector (y `LockGuard` lo ignoraba a propósito). **Corregido:** `AccountDeletionGate` ahora (a) se abre **dentro del `NonCancellable`, justo después del 204 y antes de la limpieza** (así un autobloqueo normal sigue mandando a Desbloquear y un fallo no deja la puerta abierta), y (b) al terminar la limpieza **avisa por un canal** que recoge `LockGuard` en `AppNavHost`, fuera de la pantalla de Eliminar; ese navega a «Cuenta eliminada» con la pila limpia y reabre la puerta. El ViewModel ya no tiene efectos de navegación. Nueva prueba de cableado (`LockGuardAccountDeletedTest`).
- **2 (alta/media) · la marca podía borrar una cuenta nueva.** **Corregido:** la marca lleva el `user_id` de la cuenta eliminada; al arrancar, si la cuenta local es **otra**, la marca se quita sin tocar nada. Con la marca sin `user_id` (`""`) o de la misma cuenta se reintenta.
- **3 (media) · ventana entre el 204 y la escritura de la marca** (el proceso muere o se pierde el 204 en la red). **No se cambió, se acepta como residual (para anotar como RSG):** una marca *antes* de EP-18 no se puede resolver sin saber si el servidor llegó a eliminar, y borrar el teléfono de una cuenta que tal vez sigue viva perdería cambios sin subir. En esos casos el primer `session_revoked` cierra la sesión local (Room vacío) y quedan solo preferencias no secretas.
- **4 (media/baja):** si la marca no se pudo escribir, ahora se intenta **dos veces**.
- **5 (baja):** `recordSuccess` se traga el fallo de escritura; **corregido:** nuevo `UnlockAttemptStore.reset()` que propaga el fallo y la limpieza lo cuenta.
- **6 (baja):** la contraseña podía quedar en el campo si la eliminación fallaba con la app detenida. **Corregido:** se vacía al terminar la carga.
- **7 (baja):** `LockGuardViewModel` ya no tiene `AccountDeletionGate()` por defecto (se inyecta siempre).
- **8 (baja, falso):** `FakeServer.checkReauth` no tiene límite de intentos, así que la rama `RateLimited` solo se prueba con el ViewModel y el caso de uso; **a completar cuando exista el backend real** (el contrato sí lo define).

## Pruebas (salida real)
- **`testDebugUnitTest`: 878, 0 fallas** (antes 830). **`connectedDebugAndroidTest` (sin E3, `ClipboardDeviceTest`): 170 pruebas; en la corrida completa falló 1, mi prueba nueva `LockGuardAccountDeletedTest` (una carrera de la propia prueba, la corregí y esa clase pasa 2 de 2 sola); no volví a correr las 170 juntas tras ese arreglo.** **`lintDebug`: sin errores. `assembleRelease`: compila.** Nuevas:
  - `DeleteAccountViewModelTest` (26): campos vacíos o incompletos, código solo con 2FA, la hoja con N, 0 y 1, `reauth_required` vuelve a la pantalla marcada, 409, límite, sin red, un solo envío mientras carga.
  - `DeleteAccountImplTest` (17, **Argon2 real contra el backend falso**): con y sin 2FA, la otra sesión y la propia quedan revocadas, `account_deleted`, el acceso posterior responde igual que un correo inexistente (prelogin, login, nuevo registro), contraseña incorrecta, 409, sin red, **cancelar a mitad no deja el servidor eliminado con el teléfono intacto**, la puerta se abre antes de limpiar y avisa aunque la limpieza falle.
  - `AccountWipeIntegrationTest` (7, en el emulador, Room/DataStore/Keystore reales): tras el 204 no queda nada de la lista y se conserva «onboarding visto»; si falla el Keystore queda la marca y se reintenta al arrancar; una marca de otra cuenta se quita sin borrar la nueva.
  - `LockGuardViewModelTest` (+3, y la puerta se reabre 0,5 s después de navegar, comprobado en `LockGuardAccountDeletedTest` con reloj real), `StartDestinationViewModelTest` (+1), `SettingsViewModelTest` (Eliminar con y sin red) y `LockGuardAccountDeletedTest` (2, el cableado de la navegación).
  - `DeleteAccountScreensTest` (15, Compose): textos de Figma de las tres vistas, el código solo con 2FA, errores, la hoja con plural/singular/0, bloqueada mientras elimina, y Atrás en «Cuenta eliminada».

## En el emulador
Capturas de `DeleteAccountScreensTest` comparadas con Figma: **Eliminar** (`47:2290`) y la **hoja** (`62:3233`) coinciden; en **Cuenta eliminada** (`62:3270`) las manchas salían oscuras (token del onboarding) y se pasaron al gris claro de Figma. **No recorrí por la interfaz de la app real** crear una cuenta con elementos, eliminarla y comprobar que vuelve a Iniciar sesión y no puede entrar (la prueba de Compose contra la app real se cuelga, ver tarea 13): **queda como prueba a mano**. El mismo flujo, sin la interfaz, está cubierto con el backend falso y Argon2 real (`DeleteAccountImplTest`) y con la limpieza real (`AccountWipeIntegrationTest`).

## Decisiones y desviaciones a revisar
- La puerta (`AccountDeletionGate`) y su canal son un cambio de diseño respecto a lo que pedía la tarea (que la pantalla de Eliminar navegara): era imposible por cómo `RequireUnlocked` y la limpieza interactúan.
- La marca de limpieza guarda un `user_id` (no es secreto) y no un booleano.
- `signOutLocally` devuelve `Boolean`; los demás llamadores lo ignoran como antes.
- N de la hoja: elementos locales con `deleted_at IS NULL` (incluye los que esperan subir).

## Vistas / cambios (cómo llegar)
- **Eliminar cuenta (SCR-36):** Ajustes → «Eliminar cuenta» (con red).
- **Confirmación (SCR-37):** en esa pantalla, escribir la contraseña (y el código si hay 2FA) y tocar «Eliminar mi cuenta».
- **Cuenta eliminada (SCR-38):** al confirmar con éxito.
