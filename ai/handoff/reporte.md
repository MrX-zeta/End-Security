# Reporte · Tarea 37 · Auditoría básica (entradas e inyecciones)

**Estado:** hecha. Solo lectura: no se cambió código, no se tocó `api.endsecurity.lat`, no se ejecutó código del objetivo ni se leyó ningún `.env`/clave/keystore.
**Alcance:** app en `integracion/backend`; backend en `main` de `Diego-Zarate18/end-security-backend` (leído por la API de GitHub, sin clonar). Una sola pasada, sin subagentes ni la skill de auditoría.
**No verificado:** `npm audit` y `./gradlew lint` (requieren red o ejecutar código); se marcan `needs_validation`.

## Hallazgos
| id | título | componente | severidad | estado |
|---|---|---|---|---|
| F-01 | Faltan los pines SPKI de la API en release (BR-26) | app · `network_security_config.xml` | media | confirmado (ya conocido, pendiente del servidor en línea) |
| F-02 | Límite de peticiones en memoria del proceso (`express-rate-limit`), por IP y no por cuenta | backend · `main.ts` | baja | confirmado |
| F-03 | `enableImplicitConversion: true` en `ValidationPipe` (coerción de tipos implícita) | backend · `main.ts` | baja | confirmado; los DTO usan `@IsString`/`@Matches`/`@IsBase64Bytes`, sin impacto visto |
| F-04 | Sin `RESEND_API_KEY` el correo y su código se escriben en el log (modo desarrollo) | backend · `mail.service.ts` | baja | confirmado; en producción el arranque falla sin la clave (tarea 36) |
| F-05 | Campos de texto de la bóveda sin límite de longitud en la UI (el servidor sí limita el bloque cifrado) | app · `feature/vault/ui/edit` | baja | needs_validation |
| F-06 | Dependencias sin auditar (`npm audit`, lint de Android) | ambos | desconocida | needs_validation |
| F-07 | Bloqueo de login por cuenta tras N fallos no verificado (solo límite por IP y por código de correo, máx. 5 intentos) | backend · auth | baja | needs_validation |

Sin hallazgos altos ni críticos. Riesgos aceptados relacionados: RSG-01, RSG-03, RSG-05, RSG-08.

## Revisado y sin hallazgo
- **SQL:** solo 3 usos de `$queryRaw`/`$executeRaw` (`app.service.ts`, `audit.service.ts`), todos con plantilla etiquetada (parametrizada); sin `Prisma.raw` ni concatenación.
- **DTO:** `ValidationPipe` global con `whitelist` + `forbidNonWhitelisted`; todos los DTO de entrada con validadores; `ParseUUIDPipe` en parámetros; cuerpo JSON máx. 3 MB; lote de sincronización con `ArrayMaxSize`, `ValidateNested` y tamaño máximo de bloque.
- **Acceso/IDOR:** `JwtAuthGuard` global (`APP_GUARD`), `@Public` solo en registro/login/2FA/salud; las consultas de bóveda y sesiones filtran por `user_id` del token.
- **Sesiones:** refresh con rotación, detección de reutilización (revoca la sesión), comparación en tiempo constante, rotación atómica con `updateMany`, expiración.
- **Correo y logs:** las plantillas solo interpolan el código numérico y constantes (sin datos de usuario); syslog documentado sin correos, llaves, tokens ni cuerpos; el filtro de errores no devuelve stack ni nombres de columnas.
- **Config:** Helmet, CORS cerrado por defecto, `.env.example` sin valores reales, producción se niega a arrancar con secretos de ejemplo.
- **App:** Room solo con `@Query` y `:param` (sin `rawQuery`); sin WebView ni `@JavascriptInterface`; `allowBackup=false`, `usesCleartextTraffic=false`, texto plano solo en `src/debug`; única actividad exportada es el launcher y el extra `debug_route` solo actúa con `BuildConfig.DEBUG`; sitios abiertos solo `http/https` (`SiteUrl.toOpenable`); `FLAG_SECURE` vía `SecureScreen` (20 pantallas); portapapeles con `EXTRA_IS_SENSITIVE`; sin `Log.*`/`println` en `main` ni `prod`; `DEMO_MODE=false` en `prod`; sin llaves ni `.env` versionados.

## Qué defender ante el profesor
1. **Contraseña maestra nunca sale del teléfono:** se envía `auth_key` derivada con HKDF; el servidor guarda solo su hash Argon2id (`core/crypto`, `auth-hash.ts`).
2. **Cifrado de conocimiento cero:** AES-256-GCM, nonce aleatorio de 12 B y AAD `id|userId|formatVersion`; el servidor solo ve bloques (`core/crypto`, `sync-vault.dto.ts`).
3. **Argon2id (64 MiB, t=3)** contra fuerza bruta offline (RSG-03).
4. **SQL parametrizado:** Prisma y plantillas etiquetadas en las 3 consultas crudas; Room con `:param`.
5. **Validación estricta de entrada:** `ValidationPipe` con `whitelist` y `forbidNonWhitelisted`, DTO con `class-validator`, UUID y Base64 validados (`main.ts`, `common/validators`).
6. **Control de acceso:** guard JWT global y filtro por `user_id` en cada consulta; las respuestas de login/registro son genéricas.
7. **Sesiones:** refresh rotativo con detección de reutilización y revocación (`sessions.service.ts`); 2FA TOTP con códigos de respaldo.
8. **Fuerza bruta y abuso:** rate limit general y estricto en `/api/auth`, máx. 5 intentos por código de correo.
9. **Red y cabeceras:** HTTPS obligatorio sin texto plano en release, Helmet, CORS cerrado; pinning pendiente (F-01).
10. **Datos en el dispositivo:** `FLAG_SECURE`, portapapeles sensible que se limpia, sin logs de secretos, `allowBackup=false`, secretos en `ByteArray` que se sobrescriben al bloquear.
11. **Auditoría con integridad:** `audit_log` encadenado con HMAC (RSG-10 reconoce el límite de un superusuario).

## Siguiente (no hecho)
- Pines SPKI (BR-26) cuando el servidor esté en línea; requiere probar el host, fuera del alcance de esta tarea.
- Correr `npm audit` y `./gradlew lintDebug` y atender F-05 y F-07.
