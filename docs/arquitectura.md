# End Security — Arquitectura sugerida

> **Estado:** v1.1 · 2026-10-14 · carpetas creadas en el proyecto · desarrollo exclusivamente con Claude Code
> **Complementa a:** `ai/context/knowledge-graph.json` (fuente de verdad de decisiones). Si este documento y el grafo se contradicen, manda el grafo.
> **Proyecto base:** `com.luis.end_security` · minSdk 28 · targetSdk 36 · AGP 9.2.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01

---

## 1. Principios

1. **MVVM con flujo unidireccional (UDF).** La pantalla envía acciones al ViewModel y el ViewModel expone un único estado inmutable.
2. **Organización por funcionalidad (feature-first).** Cada funcionalidad tiene sus propias capas `ui / domain / data`. Una IA genera mejor código cuando el contexto de una tarea cabe en una sola carpeta.
3. **Room es la única fuente de verdad para la UI.** La red solo sincroniza; la UI nunca lee directamente de la API.
4. **La criptografía vive aislada** en `core/crypto`, detrás de interfaces pequeñas y con pruebas propias.
5. **El texto plano solo existe en memoria** y solo mientras el almacén está desbloqueado.
6. **Un solo módulo Gradle (`app`)**, con límites definidos por paquetes. Con 7 semanas no vale la pena la complejidad de varios módulos. Si sobra tiempo, `core/crypto` es el primer candidato a módulo propio.

---

## 2. Estructura del repositorio

La raíz del repo ya es el proyecto de Android Studio, así que todo lo que pongas aquí se ve en la vista **Project** del IDE.

```
proyecto-U4/                         ← raíz del repo = proyecto de Android Studio
├── CLAUDE.md                        ← Claude Code lo carga en CADA sesión (reglas, comandos, mapa)
├── CLAUDE.local.md                  ← opcional, personal, ignorado por git
│
├── .claude/                         ← configuración de Claude Code (se versiona)
│   ├── settings.json                ← permisos: bloquea la lectura de secretos (.env, *.jks, …)
│   ├── rules/                       ← reglas por ruta: solo se cargan al tocar esos archivos
│   │   ├── crypto.md                ← core/crypto, core/session
│   │   └── base-de-datos.md         ← docs/bd, core/database, backend SQL
│   ├── agents/
│   │   └── security-reviewer.md     ← subagente de solo lectura que revisa contra los invariantes
│   └── skills/
│       └── registrar-sesion/        ← /registrar-sesion: deja constancia de lo que generó Claude
│
├── ai/
│   ├── context/
│   │   ├── knowledge-graph.json     ← fuente de verdad (decisiones, tablas, operaciones, invariantes)
│   │   └── estado-actual.md         ← hecho / en curso / siguiente; se actualiza cada sesión
│   └── sessions/                    ← bitácora de sesiones con Claude (trazabilidad para el entregable)
│
├── docs/
│   ├── arquitectura.md              ← este documento
│   ├── bd/                          ← modelo-datos.md, schema.sql, pruebas_esquema.sql
│   ├── adr/                         ← una decisión importante = un archivo (opcional)
│   ├── seguridad/                   ← modelo de amenazas STRIDE, checklist MASVS, evidencias de pruebas
│   └── entregables/                 ← documentos que se entregan en clase
│
├── app/                             ← módulo Android (paquetes en §4)
├── backend/                         ← NestJS (cuando se cree; o repo aparte)
├── infra/                           ← docker-compose.yml, proxy inverso (cuando se cree)
├── gradle/ · build.gradle.kts · settings.gradle.kts
```

**¿Por qué así con Claude Code?**
- `CLAUDE.md` se carga completo en cada sesión, así que debe ser corto (la documentación recomienda menos de 200 líneas). Por eso el grafo **no** se importa: se consulta por id cuando hace falta.
- Las **reglas por ruta** (`.claude/rules/*.md` con `paths:`) reemplazan a los "agentes por área": las reglas de criptografía solo entran al contexto cuando Claude toca `core/crypto`.
- Los **subagentes** se reservan para lo que necesita una mirada independiente: el `security-reviewer` revisa el código sin haberlo escrito.
- Los **skills** son procedimientos repetibles que se invocan con `/nombre`.

**Nota sobre `backend/`.** Si lo ponen en este mismo repo, márquenlo como excluido en Android Studio (clic derecho → *Mark Directory as → Excluded*) para que no indexe `node_modules`. Gradle lo ignora porque no está en `settings.gradle.kts`.

**¿Por qué `ai/` y no `.ai/`?** Las carpetas ocultas a veces no se muestran en el IDE ni en el explorador de Windows, y aquí conviene que siempre estén a la vista.

---

## 3. Capas y regla de dependencias

```
   ui  ──────►  domain  ◄──────  data
   │                               │
   └──► core/designsystem          └──► core/crypto · core/database · core/network · core/session
```

| Capa | Contiene | Puede usar | Nunca |
|---|---|---|---|
| `ui` | Screens (Compose), ViewModels, UiState, acciones | `domain`, `core/designsystem`, `core/session` (solo lectura del estado) | Retrofit, Room, criptografía |
| `domain` | Modelos de dominio, interfaces de repositorio, casos de uso | Kotlin puro | Android SDK, Retrofit, Room |
| `data` | Implementación de repositorios, DTOs, mappers, DAOs, APIs | `domain`, `core/*` | Clases de UI |
| `core` | Infraestructura compartida | Android SDK y librerías | Clases de una funcionalidad específica |

**Casos de uso solo cuando hay lógica real** (por ejemplo `SaveItemUseCase` cifra, versiona y encola). Si un caso de uso solo llama al repositorio, el ViewModel llama directamente al repositorio. Las IA tienden a generar casos de uso vacíos: no los acepten.

---

## 4. Paquetes de `app`

```
com.luis.end_security/
├── EndSecurityApp.kt                ← Application (@HiltAndroidApp)
├── MainActivity.kt
├── navigation/
│   ├── AppNavHost.kt
│   ├── Routes.kt
│   └── LockGuard.kt                 ← redirige a Unlock si el almacén está bloqueado
│
├── core/
│   ├── crypto/                      ← SOLO aquí hay código criptográfico
│   │   ├── KeyDerivation.kt         ← interfaz Argon2id (implementación según PEND-01)
│   │   ├── Hkdf.kt
│   │   ├── AeadCipher.kt            ← AES-256-GCM: nonce aleatorio + AAD
│   │   ├── KeyHierarchy.kt          ← master_key → enc_key / auth_key; envolver vault_key
│   │   ├── SecureBytes.kt           ← envoltorio de ByteArray con wipe() y toString() = "***"
│   │   └── keystore/
│   │       ├── BiometricKeyManager.kt
│   │       └── TokenKeyManager.kt
│   ├── session/
│   │   └── VaultSession.kt          ← estado Bloqueado/Desbloqueado; guarda vault_key en memoria
│   ├── security/
│   │   ├── SecureScreen.kt          ← FLAG_SECURE para composables
│   │   ├── SecureClipboard.kt       ← EXTRA_IS_SENSITIVE + borrado programado
│   │   └── AutoLockObserver.kt      ← ProcessLifecycleOwner + temporizador
│   ├── network/
│   │   ├── NetworkModule.kt         ← OkHttp + CertificatePinner + Retrofit
│   │   ├── AuthInterceptor.kt
│   │   └── TokenAuthenticator.kt    ← renueva el access token ante un 401
│   ├── database/
│   │   ├── EndSecurityDatabase.kt
│   │   ├── dao/  ·  entity/
│   ├── datastore/
│   │   ├── SettingsStore.kt
│   │   └── TokenStore.kt            ← tokens cifrados con TokenKeyManager
│   ├── sync/
│   │   ├── SyncWorker.kt
│   │   ├── SyncScheduler.kt
│   │   └── ConflictResolver.kt
│   ├── common/                      ← Result/AppError, DispatcherProvider, Clock
│   ├── di/                          ← módulos de Hilt de alcance global (red, BD, DataStore, crypto)
│   └── designsystem/                ← mover aquí ui/theme actual + componentes reutilizables
│       ├── theme/
│       └── component/               ← PasswordField, StrengthMeter, SecretText, EmptyState…
│
└── feature/
    ├── auth/        ← registro, verificación de correo, login, TOTP en login
    ├── unlock/      ← desbloqueo con contraseña o biometría
    ├── vault/       ← lista, detalle, crear/editar, generador
    ├── health/      ← análisis compuesto + HIBP
    ├── twofactor/   ← configurar 2FA, códigos de recuperación
    ├── account/     ← cambiar correo, cambiar contraseña maestra, eliminar cuenta
    ├── activity/    ← sesiones/dispositivos, cierre remoto, mi actividad
    └── settings/    ← autobloqueo, portapapeles, biometría
```

**Ya creado en el proyecto:** todas las carpetas de arriba (con un `.gitkeep` para que git las conserve; bórrenlo cuando la carpeta tenga su primer archivo), más `app/src/test/java/com/luis/end_security/core/crypto/` para las pruebas con vectores oficiales.

**Pendiente manual:** mover `ui/theme` a `core/designsystem/theme` desde Android Studio con *Refactor → Move* (clic derecho sobre el paquete). Así el IDE actualiza los `package` y los `import` de `MainActivity`. No lo muevan a mano desde el explorador de Windows.

Cada `feature/*` sigue la misma forma (ejemplo con `vault`):

```
feature/vault/
├── data/
│   ├── VaultRepositoryImpl.kt
│   ├── VaultItemMapper.kt          ← entidad cifrada ⇄ modelo de dominio (descifra con VaultSession)
│   └── remote/VaultApi.kt · dto/
├── domain/
│   ├── model/VaultItem.kt          ← toString() sobrescrito, nunca imprime la contraseña
│   ├── VaultRepository.kt
│   └── SaveItemUseCase.kt
└── ui/
    ├── list/VaultListScreen.kt · VaultListViewModel.kt · VaultListUiState.kt
    ├── detail/…
    ├── edit/…
    └── generator/…
```

---

## 5. Patrón MVVM + UDF

- **Estado:** una `data class` inmutable expuesta como `StateFlow`.
- **Acciones:** la pantalla llama a `onAction(Action)`, con las acciones modeladas como `sealed interface`.
- **Efectos de una sola vez** (navegar, mostrar snackbar): se emiten por un `Channel`.

```kotlin
data class LoginUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data class Submit(val masterPassword: CharArray) : LoginAction
}

sealed interface LoginEffect { data object GoToTotp : LoginEffect; data object GoToVault : LoginEffect }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAction(action: LoginAction) { /* … */ }
}
```

**Reglas para secretos en la UI:**
- La contraseña maestra **no** forma parte del `UiState`. Se pasa en la acción y el caso de uso la sobrescribe en cuanto deriva la llave.
- **Nunca** guardar secretos en `SavedStateHandle` ni en `rememberSaveable`: ambos se escriben a disco si el sistema mata el proceso.
- La contraseña revelada en el detalle se descifra bajo demanda y se descarta al salir de la pantalla.

---

## 6. Estado global: `VaultSession`

Es un singleton (`@Singleton`) que controla si el almacén está abierto.

```
SinCuenta ──registro/login──► Bloqueado ◄──bloquear / autobloqueo / logout──┐
                                 │                                          │
                                 └──contraseña maestra o biometría──► Desbloqueado
```

- Mientras está `Desbloqueado`, guarda la `vault_key` en un `SecureBytes`. Al bloquear, la sobrescribe con ceros.
- Expone `state: StateFlow<VaultState>`. `LockGuard` lo observa y manda a la pantalla de desbloqueo cualquier ruta protegida.
- `AutoLockObserver` bloquea por inactividad o cuando la app pasa a segundo plano, según el ajuste del usuario.
- **El estado de autenticación con el servidor es independiente.** Puedes tener el almacén desbloqueado sin conexión y con el token vencido; la sincronización simplemente espera a que haya red.

---

## 7. Flujo de datos y sincronización

**Guardar un elemento (funciona sin red):**

1. `VaultEditViewModel` → `SaveItemUseCase(item)`.
2. El caso de uso serializa el JSON (`v: 1`), lo cifra con la `vault_key` (nonce nuevo y AAD), y si es una edición incrementa la versión local esperada.
3. `VaultRepositoryImpl` hace un *upsert* en Room con `sync_state = PENDIENTE`.
4. `SyncScheduler` encola un `SyncWorker` único, con la restricción de red `CONNECTED`.
5. La UI observa `Flow<List<ItemEntity>>` de Room, lo descifra en `Dispatchers.Default` y lo muestra.

**`SyncWorker`:**

1. **Subir:** por cada elemento `PENDIENTE` envía `{id, blob, nonce, expectedVersion}`.
   - Respuesta `200` → marca el elemento como `SINCRONIZADO` con la versión nueva.
   - Respuesta `409` → el elemento queda en `conflict`; al desbloquear, la Bóveda muestra un aviso agrupado y el usuario elige qué versión conservar en «Elegir versión» (DEC-57). No se crean copias "(conflicto)".
2. **Bajar:** pide `GET /vault/changes?cursor=…`, aplica los cambios (incluidas las lápidas) y guarda el cursor nuevo.
3. Ante un `401`, `TokenAuthenticator` renueva el token. Si la renovación falla, el worker termina sin reintentar y la app pide iniciar sesión de nuevo.

**Descifrado de la lista.** Con cientos de elementos, descifrar todo en memoria al desbloquear es rápido. No hace falta paginar ni guardar caché en claro.

---

## 8. Seguridad transversal (checklist de la app)

| Medida | Dónde | Grafo |
|---|---|---|
| `android:allowBackup="false"` y `data_extraction_rules.xml` excluyendo todo (**hoy está en `true`**) | Manifest / `res/xml` | DEC-37 |
| R8 y minificación en release (**hoy `optimization.enable = false`**) | `app/build.gradle.kts` | DEC-37 |
| Tráfico en claro deshabilitado + `network_security_config` | Manifest / `res/xml` | DEC-22 |
| Pinning SPKI con pin de respaldo | `core/network` | DEC-22 |
| `FLAG_SECURE` en pantallas con secretos | `core/security/SecureScreen` | DEC-26 |
| Portapapeles sensible + borrado automático | `core/security/SecureClipboard` | DEC-25 |
| Autobloqueo | `core/security/AutoLockObserver` | DEC-26 |
| Sin logs de secretos; logs desactivados en release | Global | INV-06 |
| `toString()` sobrescrito en modelos con secretos | `domain/model` | INV-06 |
| Componentes con `exported="false"` salvo el launcher | Manifest | — |
| Solo `SecureRandom` | `core/crypto` | INV-04 |

---

## 9. Librerías sugeridas

Verifiquen la compatibilidad de cada librería con **AGP 9** antes de agregarla. AGP 9 trae Kotlin integrado, y Hilt y Room necesitan KSP.

| Propósito | Librería | Estado |
|---|---|---|
| UI | Compose BOM + Material 3 + Navigation Compose | Ya en el proyecto (falta Navigation) |
| Inyección de dependencias | Hilt (+ hilt-navigation-compose, hilt-work) | Propuesto |
| BD local | Room (KSP) | Propuesto |
| Ajustes y tokens | DataStore | Propuesto |
| Red | Retrofit + OkHttp + kotlinx.serialization | Propuesto |
| Segundo plano | WorkManager | Propuesto |
| Biometría | androidx.biometric | Decidido |
| Fuerza de contraseñas | zxcvbn4j | Propuesto |
| Argon2id | argon2kt / lazysodium-android / Bouncy Castle | **Pendiente (PEND-01)** |
| ~~QR del 2FA~~ | ~~ZXing core~~ | Descartado (DEC-53: el 2FA se configura copiando la clave) |
| Pruebas | JUnit, kotlinx-coroutines-test, Turbine, MockK, MockWebServer | Propuesto |

**Detalle de UX en el 2FA (DEC-53).** Si Google Authenticator está en el mismo teléfono, el usuario no puede escanear un QR de su propia pantalla. Por eso no hay QR: la pantalla muestra una sola vez la clave en Base32 (grupos de 4) con botón de copiar e instrucciones para agregarla a mano, con FLAG_SECURE y portapapeles marcado como sensible.

---

## 10. Backend (resumen; la base de datos está en docs/bd/)

```
backend/src/
├── main.ts                    ← helmet, ValidationPipe(whitelist, forbidNonWhitelisted), CORS cerrado
├── config/                    ← variables de entorno validadas (llave de cifrado TOTP, secretos JWT, HMAC prelogin)
├── common/                    ← guards (JwtAuthGuard, ReauthGuard), throttler con Redis, filtros de error
├── auth/                      ← prelogin, registro, login, refresh, logout
├── two-factor/                ← setup, confirm, disable, recovery codes
├── vault/                     ← CRUD de bloques con control de versión (409) y cambios por cursor
├── sessions/                  ← dispositivos y cierre remoto
├── audit/                     ← bitácora encadenada (solo inserción)
├── mail/                      ← Resend: verificación + alertas
├── jobs/                      ← purgas y anonimización programadas
└── health/                    ← endpoint de salud para Docker
```

El ORM (Prisma o TypeORM) queda pendiente (PEND-06).

---

## 11. Estrategia de pruebas

| Qué | Cómo |
|---|---|
| `core/crypto` | Vectores oficiales (RFC 9106 para Argon2id, NIST para AES-GCM, RFC 5869 para HKDF); ida y vuelta; **alteración**: cambiar 1 bit del bloque o del AAD debe fallar; nonces nunca repetidos en 10 000 cifrados |
| ViewModels | Repositorios falsos + Turbine para el `StateFlow` |
| Sincronización | Escenarios: sin red, 409 con conflicto, lápida remota, token vencido |
| Red | MockWebServer con un certificado distinto → la conexión debe fallar (pinning) |
| Seguridad (entregable) | MobSF (APK), Semgrep (código), Burp/mitmproxy (MITM), Frida (bypass en dispositivo rooteado, documentado), consulta directa a Postgres para demostrar que no hay texto plano |

---

## 12. Desarrollo con Claude Code

El proyecto se desarrolla **solo con Claude**, principalmente con Claude Code. Su configuración ya está creada en el repo:

| Archivo | Qué hace | Cuándo se carga |
|---|---|---|
| `CLAUDE.md` | Contexto, comandos, mapa de carpetas y reglas no negociables | En cada sesión |
| `.claude/settings.json` | Permisos: Claude **no puede leer** `.env`, llaves de firma (`*.jks`, `*.keystore`) ni `keystore.properties` | Siempre |
| `.claude/rules/crypto.md` | Reglas de criptografía y checklist de revisión | Solo al tocar `core/crypto`, `core/session` o el crypto del backend |
| `.claude/rules/base-de-datos.md` | Reglas transaccionales, orden de candados, funciones obligatorias | Solo al tocar `docs/bd`, `core/database` o SQL del backend |
| `.claude/agents/security-reviewer.md` | Subagente de **solo lectura** que revisa cambios contra los invariantes | Cuando se le pide ("revisa con security-reviewer…") |
| `.claude/skills/registrar-sesion/` | `/registrar-sesion`: crea la entrada en `ai/sessions/` y actualiza `estado-actual.md` | Al final de cada sesión de trabajo |
| `ai/context/knowledge-graph.json` | Fuente de verdad; Claude lo consulta por id con Grep | Bajo demanda |
| `ai/context/estado-actual.md` | Qué está hecho, en curso y siguiente | Claude lo lee al empezar |

**Flujo recomendado por tarea:**
1. Abrir Claude Code en la raíz del proyecto. Con `/context` pueden confirmar que `CLAUDE.md` cargó.
2. Pedir **una** funcionalidad a la vez, citando los ids del grafo que aplican (p. ej. "implementa OP-19 según DEC-08").
3. Para código delicado, pedir primero un plan y luego el código.
4. Compilar y correr pruebas (Claude puede hacerlo: `./gradlew testDebugUnitTest`).
5. Si se tocó `core/crypto`, las funciones de BD o la autenticación, pedir la revisión del subagente `security-reviewer` **y** revisarlo ustedes.
6. Cerrar con `/registrar-sesion`.

**Más adelante** (cuando empiecen las vistas) conviene agregar `.claude/rules/android-ui.md` y un skill `/nueva-pantalla` con la plantilla de Screen + ViewModel + UiState.

**Trazabilidad.** En una materia de seguridad, `ai/sessions/` vale doble: demuestra qué generó la IA y **quién lo revisó**. Es material directo para los entregables y para responder "¿cómo garantizan que el código generado es seguro?".
