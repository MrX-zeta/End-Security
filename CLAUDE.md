# End Security

Gestor de contraseñas Android con cifrado de conocimiento cero, 2FA TOTP y detección de filtraciones (HIBP con k-anonimato).
Proyecto de Seguridad Informática (UP Chiapas), 2 personas, entrega a fines de noviembre de 2026.
Todo el código lo escribe Claude y lo revisa una persona del equipo.

## Fuentes de verdad (léelas en este orden cuando las necesites)
1. `ai/context/estado-actual.md`: qué está hecho, en curso y siguiente. **Léelo al empezar.**
2. `ai/context/knowledge-graph.json`: decisiones (DEC-*), invariantes (INV-*), operaciones (OP-*), tablas (TBL-*), transacciones (TX-*), pantallas (SCR-*, con su nodo de Figma y navegación).
   Búscalo con Grep por id (p. ej. `"id": "DEC-05"`); no lo leas completo si no hace falta.
3. `docs/arquitectura.md`: arquitectura MVVM de la app.
4. `docs/bd/modelo-datos.md` y `docs/bd/schema.sql`: base de datos del servidor, Room, DataStore y Redis.

Si algo no está decidido (estado `propuesto`, `pendiente`) o no aparece en el grafo: **pregunta antes de inventarlo**.
Si una decisión cambia, propón actualizar el grafo (nodo + relaciones + versión en `meta`) y espera aprobación.

## Stack
- Android: Kotlin 2.2, Jetpack Compose + Material 3, AGP 9.2 (Kotlin integrado), minSdk 28, targetSdk 36.
  Namespace `com.luis.end_security`. MVVM + UDF, Hilt, Room, DataStore, Retrofit/OkHttp, WorkManager.
- Backend (aún no creado): NestJS + PostgreSQL 16 + Redis en Docker Compose.

## Comandos
- Compilar: `./gradlew assembleDebug`
- Pruebas unitarias: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Antes de agregar una dependencia, verifica que sea compatible con AGP 9 y agrégala a `gradle/libs.versions.toml`.

## Mapa de la app (`app/src/main/java/com/luis/end_security/`)
- `navigation/`: NavHost, rutas y `LockGuard` (manda a desbloqueo si el almacén está bloqueado).
- `core/crypto/`: ÚNICO lugar con código criptográfico. `core/crypto/keystore/` para Android Keystore.
- `core/session/`: `VaultSession` (bloqueado/desbloqueado, vault_key en memoria).
- `core/security/`: FLAG_SECURE, portapapeles seguro, autobloqueo.
- `core/network/`, `core/database/{dao,entity}/`, `core/datastore/`, `core/sync/`, `core/common/`, `core/di/`.
- `core/designsystem/{theme,component}/`: tema y componentes reutilizables.
- `feature/<funcionalidad>/{data,domain,ui}/`: auth, unlock, vault, health, twofactor, account, activity, settings.

Regla de dependencias: `ui → domain ← data`. `ui` nunca usa Retrofit, Room ni crypto. `domain` es Kotlin puro.

## Reglas no negociables
1. Nada en claro sale del teléfono ni se guarda en disco: el servidor y Room solo ven bloques cifrados.
2. La contraseña maestra nunca se envía; se envía `auth_key` derivada (DEC-05).
3. AES-256-GCM con nonce aleatorio de 12 B por cifrado y AAD = `id|user_id|format_version`. Sin criptografía casera.
4. Aleatoriedad solo con `SecureRandom`. Prohibido `Random`, `kotlin.random` o `Math.random`.
5. Nunca registres en logs contraseñas, llaves, tokens ni bloques. Las clases con secretos sobrescriben `toString()`.
6. Secretos como `ByteArray`/`CharArray`; se sobrescriben al bloquear. Nunca en `SavedStateHandle` ni `rememberSaveable`.
7. Pantallas con secretos usan FLAG_SECURE. El portapapeles se marca sensible y se limpia solo.
8. Borrar un elemento = lápida. Toda escritura de elemento lleva la versión esperada.
9. Las renovaciones de token se hacen una a la vez (single-flight).
10. Casos de uso solo cuando tienen lógica real; si no, el ViewModel llama al repositorio.

## Cómo trabajar
- Una funcionalidad por tarea; modifica solo los archivos que la tarea necesita.
- Para código delicado (crypto, auth, sincronización), presenta un plan antes de escribir código.
- Al terminar: compila, corre las pruebas y reporta el resultado real.
- Si tocaste `core/crypto`, `core/session`, autenticación o SQL: escribe **"REQUIERE REVISIÓN HUMANA"** en tu resumen
  y sugiere revisar con el subagente `security-reviewer`.
- Al final de la sesión, el equipo ejecuta `/registrar-sesion`.

## Estilo
- Identificadores y nombres de archivo en inglés; comentarios, documentación y textos de la UI en español.
- Textos de la UI en `res/values/strings.xml`, nunca escritos directamente en los composables.
