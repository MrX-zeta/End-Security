# Alcance del backend — End Security

> 2026-10-22 · Responsable: la persona del equipo a cargo del backend (DEC-61).
> Contrato con la app: [`docs/api/`](../api/README.md). Base de datos: [`docs/bd/`](../bd/modelo-datos.md).
> Este documento lista **todo lo que le toca al backend**, para que no quede nada entre las dos partes.

## 1. Lista de actividades
| # | Actividad | Referencia | Hecho |
|---|---|---|---|
| B1 | Proyecto NestJS + PostgreSQL 16 + Redis en Docker Compose; migración 001 (`docs/bd/schema.sql`) | DEC-23, DEC-48 | ☐ |
| B2 | **Migración 002:** subir `users_kdf_params_chk` al piso de 65 536 KiB / t ≥ 3 / p ≥ 1 | DEC-05 | ☐ |
| B3 | Los 26 endpoints del contrato (`EP-01…EP-26`) | `docs/api/openapi.yaml` | ☐ |
| B4 | **Validación de cada petición** contra el contrato (§2) | DEC-61 | ☐ |
| B5 | **Limpieza de entradas de texto** (§3) | — | ☐ |
| B6 | **Límite de intentos** en Redis (§4) | DEC-32 | ☐ |
| B7 | Autenticación: Argon2id de la `authKey`, JWT de acceso, refresh rotativo con `app.session_rotate()` | DEC-05, DEC-16 | ☐ |
| B8 | 2FA TOTP (secreto cifrado con llave del servidor, anti-replay) y códigos de respaldo con HMAC + pepper | DEC-11, DEC-12, DEC-43 | ☐ |
| B9 | Correos con Resend: verificación, alertas y aviso de registro duplicado | DEC-13 | ☐ |
| B10 | Bitácora encadenada `audit_log` (eventos de negocio que ve el usuario) | DEC-19 | ☐ |
| B11 | **Registro de operaciones con syslog (RFC 5424)** (§5) | DEC-62 | ☐ |
| B12 | **Proxy inverso HTTPS** (§6) | DEC-22, DEC-23 | ☐ |
| B13 | Tareas programadas: `app.run_maintenance()` diaria con el rol `endsec_jobs` | DEC-40, DEC-45 | ☐ |
| B14 | Secretos solo en variables de entorno (pepper, secreto del prelogin, llave del TOTP, llave del JWT), nunca en el repositorio | DEC-38 | ☐ |
| B15 | Pruebas: cada `code` de error del contrato, límites, anti-enumeración (mismo tiempo y cuerpo) y pruebas del esquema (`docs/bd/pruebas_esquema.sql`) | DEC-30 | ☐ |

## 2. Validación de las peticiones
- **DTO por endpoint.** Se usan `class-validator` y `ValidationPipe` con `whitelist: true` y `forbidNonWhitelisted: true`, para que un campo que no está en el contrato dé 400.
- **Reglas del contrato:**
  - UUID canónico (minúsculas y guiones);
  - Base64url sin relleno con el **largo exacto** de cada campo (`authKey` 43, sal 22, nonce 16, pvk 64);
  - bloques de 17 a 32 768 bytes;
  - `SixDigitCode`, `RecoveryCode` y parámetros KDF dentro del piso y los topes.
- **Límites de petición:** cuerpo máximo de 64 KiB y `Content-Type: application/json`.
- Todo error de validación responde `400 validation_failed` en `application/problem+json`, **sin repetir el valor recibido**.

## 3. Limpieza de entradas de texto
Solo hay tres campos de texto libre; todo lo demás son bytes cifrados o identificadores.

| Campo | Limpieza |
|---|---|
| `email` | Recortar espacios, pasar a minúsculas, NFC; máximo 254; formato de `users_email_format_chk` |
| `displayName` | Recortar, NFC, colapsar espacios repetidos; quitar caracteres de control (Cc) y marcas bidi; 1 a 50 |
| `deviceName` | Lo mismo que `displayName`; 1 a 100 |

- **Consultas siempre parametrizadas** (ORM o `$1, $2…`), nunca SQL armado con texto.
- **Los bloques cifrados no se limpian ni se interpretan:** solo se valida su tamaño. El servidor no puede leerlos (DEC-34).
- Al devolver texto (actividad, sesiones), la respuesta es JSON con su `Content-Type`, nunca HTML.

## 4. Límite de intentos (Redis)
Están en `docs/api/README.md` («Reglas de seguridad», punto 7). Responden `429 rate_limited` con `Retry-After`, y el
quinto fallo del login ya responde 429 (contrato 1.1.0). **Las claves de Redis no llevan correos en claro:** usan el
HMAC del correo.

## 5. Registro de operaciones con syslog (RFC 5424) — DEC-62
Lo pide el profesor: **cada operación que hace el usuario queda registrada** con el estándar syslog.

### 5.1 Dos registros distintos (no se reemplazan)
| | `audit_log` (DEC-19) | syslog (este apartado) |
|---|---|---|
| Para qué | Historia de seguridad **que ve el usuario** (Actividad reciente) | Registro **operativo** del servidor, para el equipo |
| Qué guarda | 18 tipos de evento de negocio | **Todas** las peticiones (una línea por petición) y los eventos del sistema |
| Dónde | PostgreSQL, encadenado por hash | Flujo syslog → archivo rotado |
| Cuánto dura | Hasta que se anonimiza la cuenta | 90 días (PEND-05) |

### 5.2 Niveles que se usan (5 de los 8)
Facility **`local0` (16)**. PRI = 16 × 8 + severidad.

| Severidad | PRI | Cuándo | Ejemplos |
|---|---|---|---|
| **2 · crit** | 130 | El sistema no es confiable o no puede funcionar | `app.verify_audit_chain()` detecta alteración; falta un secreto al arrancar; PostgreSQL o Redis caídos |
| **3 · err** | 131 | Falló algo que debía funcionar | Respuesta 5xx; no se pudo enviar un correo; falló `run_maintenance()` |
| **4 · warning** | 132 | Evento de seguridad sospechoso o fallido | `invalid_credentials`, `invalid_code`, `reauth_required`, `rate_limited`, `account_locked`, **`token_reuse_detected`**, `validation_failed` |
| **5 · notice** | 133 | Operación exitosa que **cambia la seguridad de la cuenta** | Registro, verificación de correo, login exitoso, logout, 2FA activado/desactivado, códigos regenerados, contraseña maestra cambiada, correo cambiado, sesión cerrada, cuenta eliminada |
| **6 · info** | 134 | Operación normal del usuario | Prelogin, renovación de token, sincronización (`GET /vault/items`), crear/editar/borrar un elemento, ver cuenta, sesiones o actividad |

`debug` (7) solo existe en desarrollo y nunca se activa en producción. `emerg` y `alert` no se usan.

### 5.3 Formato (RFC 5424 con datos estructurados)
```
<PRI>1 TIMESTAMP HOSTNAME endsec-api PROCID MSGID [endsec@32473 req="…" ep="…" op="…" status="…" code="…" ms="…" user="…" sess="…" ip="…"] MSG
```
Ejemplo de un login fallido:
```
<132>1 2026-10-22T07:58:03.412Z api-1 endsec-api 1 login [endsec@32473 req="5f0c…" ep="EP-05" op="OP-14" status="401" code="invalid_credentials" ms="512" ip="203.0.113.0/24"] Inicio de sesión rechazado
```
- **MSGID:** el nombre corto de la operación (`prelogin`, `register`, `login`, `vault_put`…).
- **`32473`:** el número de empresa reservado para documentación (RFC 5612); se deja así en un proyecto escolar.
- **`req`:** el `X-Request-Id` que manda la app.
- **`user`:** el `user_id` (UUID). Antes del login, en `prelogin`, `register` y `login` fallido, **no** se pone nada que identifique el correo.
- **`ip`:** truncada (/24 en IPv4, /48 en IPv6) para minimizar datos personales. La IP completa solo vive en `audit_log_pii`.

### 5.4 Qué nunca se registra
Correos, `authKey`, tokens (ni partes), códigos TOTP, de respaldo o de correo, sales, pvk, bloques, cuerpos de petición
o respuesta, encabezado `Authorization` ni el `title` de errores con valores. **Regla:** si un campo no está en la lista
de §5.3, no se registra.

### 5.5 Cómo se implementa en NestJS
1. **Un interceptor global** registra **una línea por petición** al terminar, con la severidad de la tabla según el resultado. Así se cumple «cada operación queda registrada».
2. **Eventos que no vienen de una petición** (arranque, tareas programadas, verificación de la cadena, caída de Redis) se registran desde su servicio.
3. **Salida:** el logger (por ejemplo `pino`, que no bloquea) escribe a `stdout`. Docker Compose la manda con el driver `syslog` a un contenedor **rsyslog** en la red interna, que escribe `/var/log/endsec/api.log` con rotación diaria y 90 días.
4. **Prueba:** una petición de cada tipo produce exactamente una línea con su severidad, y ninguna contiene los campos de §5.4.

### 5.6 ¿Y la app?
Las operaciones que no pasan por el servidor (desbloquear, ver, copiar, buscar, generar) no se registran en el servidor,
por diseño de conocimiento cero. La app **no escribe logs en release** (R8 los elimina, DEC-37), para no dejar rastros en
el teléfono. Si el profesor lo pide, se puede agregar un registro local cifrado, sin contenido, de esas operaciones.

## 6. Proxy inverso HTTPS (DEC-22, DEC-23)
- Es lo único expuesto a internet (puerto 443). La API, PostgreSQL, Redis y rsyslog van en la red interna de Compose.
- Requisitos:
  - **TLS 1.3** con certificado de Let's Encrypt renovado con `certbot --reuse-key` (la llave no cambia y el pinning SPKI de la app sigue sirviendo);
  - **pin de respaldo:** una segunda llave guardada fuera del servidor;
  - HSTS;
  - cuerpo máximo de 64 KiB;
  - límite básico de conexiones por IP, como primera barrera antes del de Redis;
  - pasa `X-Request-Id` y la IP real a la API (`X-Forwarded-For`, configurando `trust proxy` solo para el proxy).
- **Para la app:** al unir el backend, el equipo comparte los dos hashes SPKI (actual y de respaldo) para `network_security_config.xml`.
