# Tarea 38 · Corregir los hallazgos de la auditoría (tarea 37)

**Estado:** hecha
**Escrita:** 2026-11-19 · sesión de diseño

## Objetivo
Cerrar F-03, F-05, F-06 y F-07 del reporte de la tarea 37 con el cambio mínimo. F-01 (pines SPKI) NO entra aquí. F-02 y F-04 son riesgos aceptados: solo se documentan.

## Reglas
- Los commits NO llevan coautoría de IA (sin `Co-Authored-By`, `Claude-Session` ni «Generated with»). Verifica con `git log -1 --format=%B`.
- Rama de la app: parte de `origin/integracion/backend`. Backend: solo en la rama `integracion/app`, con PR a `develop`; nunca toques `develop` ni `main` (DEC-61). No leas `.env*`, claves ni keystores. No pruebes `api.endsecurity.lat`.
- Cambio mínimo; cada arreglo con una prueba pequeña. Sin pantallas nuevas ni texto de usuario nuevo salvo el mensaje de F-07 (cópialo del estilo de los errores existentes).

## Trabajo
1. **F-07 · bloqueo de login por cuenta.** Verifica en el backend si ya existe bloqueo tras N fallos. Si no: contador de fallos por cuenta (5 fallos → bloqueo de 15 min, se reinicia con un login correcto), respuesta genérica igual a la de credenciales inválidas (sin revelar que la cuenta existe ni que está bloqueada) y evento de auditoría. Reutiliza las tablas y utilidades existentes; si hace falta una migración Prisma, hazla mínima y avísame en el reporte. Si ya existe, documenta dónde y no cambies nada.
2. **F-05 · límites de longitud en la app.** `maxLength` en los campos de texto de la bóveda (título, usuario, URL, notas, etc.) usando los límites del servidor/DTO; que el tope no corte contraseñas generadas. Una prueba por límite.
3. **F-03 · `enableImplicitConversion`.** Desactívalo en el `ValidationPipe` (`main.ts`) y corre `npm test`. Si algo se rompe, añade `@Type()` solo donde haga falta; si cuesta más de lo razonable, déjalo y anótalo como riesgo aceptado.
4. **F-06 · dependencias.** Corre `npm audit --omit=dev` en el backend y `./gradlew lintProdDebug` en la app. Actualiza solo vulnerabilidades altas/críticas con arreglo de versión menor; el resto se anota.
5. **F-02 y F-04:** una línea en `docs/despliegue.md` (backend) y en el grafo como riesgos aceptados: límite en memoria válido con una sola instancia; log de correo solo en desarrollo.

## Pruebas y grafos
`npm test`, `testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug`, `assembleProdRelease` en verde. Actualiza grafos (BSEC/RSG según corresponda) y `estado-actual.md`.

## Entrega
- Backend: commit(s) en `integracion/app` y PR a `develop` (si el PR #1 ya está abierto, súmalos a él).
- App: commit(s) en `integracion/backend` (PR #2).
- `ai/handoff/reporte.md` corto: qué se cerró, qué quedó aceptado y si hubo migración. Sin detalles de explotación.
- Estado `hecha`, push y detente.
