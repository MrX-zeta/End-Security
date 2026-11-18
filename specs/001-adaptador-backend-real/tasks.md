# Tasks: Adaptador de la variante prod al backend real

**Input**: Design documents from `specs/001-adaptador-backend-real/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/translation.md, quickstart.md

**Tests**: sí; FR-017 pide pruebas unitarias del adaptador con respuestas reales del backend.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin depender de tareas sin terminar)
- **[Story]**: historia de spec.md (US1…US4)

## Path Conventions

- Código de prod: `app/src/prod/java/com/luis/end_security/core/network/backend/`
- Pruebas de prod: `app/src/testProd/java/com/luis/end_security/core/network/backend/`
- Código común: `app/src/main/java/com/luis/end_security/`

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Agregar `okhttp = "4.12.0"` y la librería `okhttp` a `gradle/libs.versions.toml`, y `prodImplementation(libs.okhttp)` en `app/build.gradle.kts` (research R1)
- [ ] T002 Agregar `buildConfigField("String", "API_BASE_URL", …)`: `http://127.0.0.1:3000/api/` en `prod` + `debug`, `""` en `release` con `TODO(BR-26)`, en `app/build.gradle.kts` (research R10)
- [ ] T003 [P] Crear `app/src/debug/res/xml/network_security_config.xml` con texto plano solo para `127.0.0.1` y `10.0.2.2`, conservando el bloque de HIBP; release sigue con `app/src/main/res/xml/network_security_config.xml` (FR-016)

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Crear los DTO del backend (envoltura `{success,data}`, error `{statusCode,error,message,details}`, `kdf`, `pvk`, tokens, usuario, sync, auditoría, sesiones, 2FA) en `app/src/prod/java/com/luis/end_security/core/network/backend/BackendDtos.kt`
- [ ] T005 Crear `BackendTranslator` con: tipo `BackendCall`/`BackendReply`, función `send` inyectada, conversión Base64url ↔ Base64, respuesta *problem+json* con `code` y la tabla de errores de `contracts/translation.md`, en `.../backend/BackendTranslator.kt`
- [ ] T006 Crear `BackendTransport` (OkHttp, `API_BASE_URL`, sin URL → `IOException`, sin logs) y enlazarlo en `app/src/prod/java/com/luis/end_security/core/di/ProdNetworkModule.kt`
- [ ] T007 [P] Prueba base del traductor (Base64, envoltura, errores 400/401/404/429/5xx) en `app/src/testProd/java/com/luis/end_security/core/network/backend/BackendTranslatorTest.kt`

**Checkpoint**: la app `prod` compila y cada ruta desconocida responde `server_error`.

---

## Phase 3: User Story 1 - Crear una cuenta y entrar (Priority: P1) 🎯 MVP

**Goal**: registro, verificación, login (tres respuestas), 2FA con TOTP y con código de respaldo, renovación y cierre.
**Independent Test**: quickstart «Sin teléfono» paso 1 y punta a punta pasos 1–3 y 6.

### Tests for User Story 1

- [ ] T008 [P] [US1] Pruebas: prelogin, registro (`userId`, `deviceName`, 202), verificación con el desafío guardado y sin él (`challenge_expired`), login → `authenticated` / `two_factor_required` / `email_not_verified`, 2FA TOTP y respaldo (`recoveryCodesRemaining`), refresh 401 → `session_revoked`, 401 con sesión → `token_expired`, en `BackendTranslatorTest.kt`

### Implementation for User Story 1

- [ ] T009 [US1] Rutas `/auth/*` del contrato y el mapa en memoria «correo normalizado (trim, minúsculas, NFC) → challengeToken» (data-model.md) en `BackendTranslator.kt`
- [ ] T010 [US1] Llaves conocidas (`userId`, `kdf`, `protectedVaultKey`, `keyVersion`) en memoria al recibir un acceso; se borran en logout (data-model.md) en `BackendTranslator.kt`
- [ ] T011 [US1] Formato de códigos de respaldo por variante (Crockford `XXXX-XXXX` en demo; `xxxxx-xxxxx` con `abcdefghjkmnpqrstuvwxyz23456789` en prod) en `app/src/main/java/com/luis/end_security/feature/auth/domain/BackupCodeFormat.kt` y sus usos en `feature/auth/ui/backupcode/` (BR-10)

**Checkpoint**: se puede crear una cuenta y entrar en `prod`.

---

## Phase 4: User Story 2 - Bóveda sincronizada (Priority: P1)

**Goal**: descargar, subir de a uno, borrar, conflictos y versión del servidor.
**Independent Test**: punta a punta pasos 4–5.

### Tests for User Story 2

- [ ] T012 [P] [US2] Pruebas: `since=0`, `fullResync` con `since>0` → 410, PUT nuevo/edición → `created`/`updated`, DELETE → `deleted`, 409 `version_mismatch` → `version_conflict`, `not_found` → 404, GET de un elemento borrado vía `since=1`, en `BackendTranslatorTest.kt`

### Implementation for User Story 2

- [ ] T013 [US2] Rutas `/vault/items*` (lotes de 1, BR-13; `getItem` por `sync`, BR-14) y cursor en memoria en `BackendTranslator.kt`

---

## Phase 5: User Story 3 - Administrar la cuenta (Priority: P2)

**Goal**: cuenta, marketing, correo, contraseña, eliminación, 2FA, sesiones y actividad.
**Independent Test**: punta a punta pasos 6–10.

### Tests for User Story 3

- [ ] T014 [P] [US3] Pruebas: `GET /account` con `keyVersion` de `/vault/sync`, cambio de contraseña (409 `KeyVersionMismatch` → `key_version_conflict`), 403 de reautenticación → `reauth_required`, setup de 2FA desde `otpauthUrl`, sesiones con `lastUsedAt` nulo, actividad con eventos ocultos y sin IP, en `BackendTranslatorTest.kt`

### Implementation for User Story 3

- [ ] T015 [US3] Rutas `/account*`, `/sessions*` y `/activity` (tabla de eventos de contracts/translation.md) en `BackendTranslator.kt`
- [ ] T016 [US3] `AccountApi.startTwoFactorSetup(reauth: Reauth)` en `feature/account/domain/AccountApi.kt`, `core/network/api/AccountApiImpl.kt` (cuerpo `ReauthRequestDto`), `feature/account/domain/TwoFactorRepository.kt` y `feature/account/data/TwoFactorRepositoryImpl.kt` (BR-20)
- [ ] T017 [US3] Hoja `ReauthSheet` antes de pedir la clave en `feature/account/ui/TwoFactorSetupViewModel.kt` y su pantalla, con textos en `res/values/strings.xml` (BR-20)

---

## Phase 6: User Story 4 - La demo no cambia (Priority: P1)

- [ ] T018 [US4] Ajustar las pruebas de demo que llamen a `startTwoFactorSetup()` o usen `BackupCodeFormat` directamente, sin cambiar el comportamiento del servidor falso, en `app/src/testDemo/` y `app/src/test/`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T019 Correr `./gradlew assembleProdDebug testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug` y anotar el resultado real
- [ ] T020 Revisión con el subagente `security-reviewer` (toca autenticación y red) y «REQUIERE REVISIÓN HUMANA» en el reporte

---

## Dependencies & Execution Order

### Phase Dependencies
- Setup (T001–T003) → Foundational (T004–T007) → historias → Polish.

### User Story Dependencies
- US1 primero: US2 y US3 necesitan sesión. US2 y US3 tocan el mismo `BackendTranslator.kt`, así que van en serie.
- US4 depende de T011 y T016 (son los únicos cambios visibles en demo).

### Within Each User Story
- Pruebas con JSON real del backend → implementación → correr pruebas.

### Parallel Opportunities
- T003 con T001–T002; T007, T008, T012 y T014 se escriben en paralelo si se separan por archivo; T011 y T016–T017 (UI) en paralelo con las rutas del traductor.

## Parallel Example: User Story 1

```text
T011 (BackupCodeFormat, código común) junto con T009 (rutas /auth en el traductor de prod)
```

## Implementation Strategy

### MVP First (User Story 1 Only)
Setup + Foundational + US1: ya se puede crear cuenta y entrar contra el servidor local.

### Incremental Delivery
US2 (bóveda) → US3 (cuenta) → US4 (verificación de la demo) → Polish. Un commit por fase.

### Parallel Team Strategy
No aplica: una sola sesión implementa.

## Notes

- Ningún secreto en logs ni en `toString()`.
- La traducción es la única fuente de verdad de las equivalencias: si el backend cambia, se cambia
  `contracts/translation.md` y el traductor juntos.
