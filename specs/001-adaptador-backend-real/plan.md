# Implementation Plan: Adaptador de la variante prod al backend real

**Branch**: `integracion/backend` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-adaptador-backend-real/spec.md`

## Summary

La variante `prod` cambia su `ApiTransport` de «sin conexión» por `BackendTransport`: recibe cada `HttpRequest` del contrato
de la app (rutas `/v1`, Base64url, *problem+json* con `code`) y la traduce al backend real (`/api`, Base64 estándar,
`{success,data}`, errores de Nest). `ApiClient` y las cuatro `*ApiImpl` no cambian, así que la traducción vive en un solo
lugar y la variante `demo` queda intacta. La traducción es una clase pura (`BackendTranslator`) que recibe una función
`send` para hablar con el servidor; las pruebas le pasan respuestas JSON copiadas del backend real. Fuera del adaptador
solo cambian dos cosas de la app: la activación de 2 pasos pide la contraseña maestra (BR-20) y el campo del código de
respaldo acepta el formato del servidor en `prod` (BR-10).

## Technical Context

**Language/Version**: Kotlin 2.2 (AGP 9.2), JVM 17 para las pruebas unitarias
**Primary Dependencies**: kotlinx.serialization (ya está), Hilt (ya está), **OkHttp 4.12** (nueva; ver research R1)
**Storage**: ninguno nuevo; el estado del adaptador (desafíos, llaves conocidas, cursor) vive en memoria
**Testing**: JUnit 4 + kotlinx-coroutines-test (ya están); el traductor se prueba sin red
**Target Platform**: Android minSdk 28, targetSdk 36; backend NestJS local en el puerto 3000
**Project Type**: app móvil + API (la API está en otro repositorio)
**Performance Goals**: una petición del contrato = una petición al servidor, salvo 4 casos documentados (contracts/translation.md)
**Constraints**: nada en claro sale ni se registra (INV-01, INV-06); release solo HTTPS (INV-18); demo sin cambios
**Scale/Scope**: 25 rutas del contrato, 4 interfaces de dominio, 1 transporte nuevo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Conocimiento cero (INV-01, INV-02) | El adaptador solo mueve bloques ya cifrados y la `auth_key`; cambia la codificación (Base64url ↔ Base64), nunca descifra. | Pasa |
| II. Criptografía (INV-03…05) | No hay código criptográfico nuevo; `core/crypto` no se toca. | Pasa |
| III. Secretos y logs (INV-06, INV-07) | Sin logs en el transporte; los desafíos y llaves en memoria no se imprimen (`toString` propio). Las llaves en caché son públicas para el servidor (kdf, pvk), no secretos del teléfono. | Pasa |
| IV. Bóveda y sincronización (INV-08, INV-09, INV-25, INV-27) | Lápidas y versión esperada se traducen 1:1 a `baseVersion`/`deleted`; la renovación sigue en `TokenManager` (single-flight). | Pasa |
| V. Cuenta y acceso (INV-10, INV-12, INV-16) | Reautenticación en todo lo sensible (ahora también al activar 2FA, BR-20); los errores de login no distinguen correo inexistente. | Pasa |
| VI. Contrato y capas (DEC-61, DEC-65) | Traducción en `prod/core/network/backend`; dominio y UI sin Retrofit ni HTTP. Base64url y `code` se conservan hacia dentro de la app. | Pasa |
| VII. Preguntar si no está decidido (INV-19) | Lo no decidido se resolvió con el grafo y quedó en research.md y en el reporte. | Pasa |
| Restricciones (INV-18) | Texto plano solo en `debug` y solo 127.0.0.1 / 10.0.2.2; pines SPKI pendientes (BR-26). | Pasa con pendiente BR-26 |

## Project Structure

### Documentation (this feature)

```text
specs/001-adaptador-backend-real/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/translation.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/prod/java/com/luis/end_security/core/
├── di/ProdNetworkModule.kt            # enlaza BackendTransport y la URL base
└── network/backend/
    ├── BackendDtos.kt                 # forma del backend real (solo prod)
    ├── BackendTranslator.kt           # contrato de la app ↔ backend (puro)
    └── BackendTransport.kt            # OkHttp: envía lo que pide el traductor
app/src/testProd/java/com/luis/end_security/core/network/backend/
└── BackendTranslatorTest.kt           # respuestas reales del backend
app/src/debug/res/xml/network_security_config.xml   # texto plano solo a 127.0.0.1 y 10.0.2.2
app/src/main/java/com/luis/end_security/
├── feature/account/domain/AccountApi.kt            # startTwoFactorSetup(reauth) (BR-20)
├── feature/account/ui/TwoFactorSetup*.kt           # hoja de contraseña maestra antes de la clave
└── feature/auth/domain/BackupCodeFormat.kt         # formato por variante (BR-10)
```

**Structure Decision**: todo lo del servidor real vive en el *source set* `prod`, como el backend falso vive en `demo`
(tarea 31). El APK `demo` no contiene el traductor y el `prod` no contiene el falso.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Dependencia nueva (OkHttp) | `PATCH /users/me` y `DELETE /users/me` con cuerpo | `HttpURLConnection` no admite `PATCH` y no garantiza cuerpo en `DELETE`; OkHttp ya figura en el stack de CLAUDE.md |
| Estado en memoria en el traductor | El contrato manda `email` y el backend pide `challengeToken` (BR-08); `/users/me` no trae llaves (BR-15) | Cambiar el contrato o el backend está fuera de DEC-65 |
