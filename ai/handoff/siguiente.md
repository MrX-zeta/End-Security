# Tarea 37 · Auditoría básica de seguridad: entradas de texto e inyecciones

**Estado:** pendiente
**Escrita:** 2026-11-19 · sesión de diseño

## Objetivo
Revisión ligera y SOLO LECTURA de app y backend. Enfoque: entradas de texto, inyecciones y lo básico que un profesor puede preguntar. Una sola pasada, pocos tokens.

## Reglas
- Los commits NO llevan coautoría de IA: sin `Co-Authored-By`, `Claude-Session` ni «Generated with». Pon `{"attribution": {"commit": "", "pr": ""}}` en `.claude/settings.local.json` y añade a `CLAUDE.md` la línea «Los commits no llevan coautoría de IA». Verifica con `git log -1 --format=%B`.
- No cambies código. No pruebes `api.endsecurity.lat` ni servicios externos. No ejecutes código del objetivo. No leas `.env*`, claves ni keystores.
- NO uses la skill cloudflare/security-audit en modo full ni subagentes en paralelo (consume demasiado). Si quieres, consulta solo su lista de clases de ataque como referencia.
- App: rama `integracion/backend`. Backend: `main` de https://github.com/Diego-Zarate18/end-security-backend (solo lectura).

## Qué revisar
Backend:
1. Inyección SQL: busca `$queryRaw`, `$executeRaw`, `Prisma.raw` y concatenación de strings en consultas.
2. DTOs: `class-validator` en TODOS los endpoints, `ValidationPipe` con `whitelist` y `forbidNonWhitelisted`, longitudes máximas, tamaño del body, tipos (evita objetos donde se espera string → inyección NoSQL/operadores Prisma).
3. Inyección en logs/cabeceras/correo: datos de usuario en syslog, en asuntos o cuerpos de correo (saltos de línea, HTML).
4. Control de acceso/IDOR: cada consulta de vault/sesiones/cuenta filtra por el usuario autenticado; guard global JWT.
5. Sesiones y tokens: expiración, rotación del refresh, revocación, rate limits en login/códigos.
6. Secretos y config: nada en el repo ni en logs, CORS, helmet, errores sin stack, dependencias con `npm audit`.

App:
1. Room: consultas con parámetros (`@Query` con `:param`), sin `rawQuery` con concatenación.
2. Entradas de texto: validación y límites en registro, login, campos de bóveda, búsqueda, importación; manejo de deep links/intents.
3. WebView/`@JavascriptInterface` (si existen), `exported` en el manifest, `allowBackup`, `FLAG_SECURE`, portapapeles, logs con datos sensibles.
4. Red: `network_security_config` sin cleartext en release, `DEMO_MODE` fuera de prod, secretos en el repo.

## Entrega
- `ai/handoff/reporte.md` corto: tabla id / título / componente / severidad / estado (confirmado o `needs_validation`). Sin detalles de explotación (el repo puede ser público). Cruza con BR-*/RSG-*.
- Añade una sección «Qué defender ante el profesor»: 8–10 puntos con la medida que ya existe (p. ej. Prisma parametrizado, DTO whitelist, AES-GCM, Argon2id) y dónde está en el código.
- Estado `hecha`, commit en `integracion/backend` sin coautoría, y detente.
