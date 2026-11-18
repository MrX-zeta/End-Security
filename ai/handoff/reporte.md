# Reporte · Tarea 33

**Estado:** hecha, con un pendiente de la carpeta original de la app (Paso 0).
**Ramas:** app `integracion/backend` (PR MrX-zeta/End-Security#2), backend `integracion/app` (PR Diego-Zarate18/end-security-backend#1). Sin merge, nada en `develop`, `development` ni `main`.

## Paso 0 · Estado del árbol (app)
La carpeta original de la app estaba en `integracion/backend` (`HEAD` 2eff6d2) y `app/` estaba limpio. Lo que mostraba `git status`:
- `ai/handoff/siguiente.md` y `ai/handoff/reporte.md`: la tarea 33 y su versión anterior; son los archivos de esta tarea.
- `ai/handoff/README.md`: sin la sección «Herramientas (DEC-66)» (versión vieja).
- `gradle/libs.versions.toml`: sin `okhttp` (versión vieja; con ese archivo `prod` no compila).
- `docs/bd/end_security_db.jpg`: modificado; **no lo toqué ni lo versioné**.

**Pendiente:** intenté `git restore -- gradle/libs.versions.toml` y el clasificador de permisos lo bloqueó, así que no restauré nada en esa carpeta. Hice todo el trabajo de la app en el worktree (misma rama, limpio y con el toml correcto). Para dejar la carpeta original limpia, ejecuta ahí: `git restore -- gradle/libs.versions.toml ai/handoff/README.md`.

## Plan del Paso 2 (escrito antes del código, REQUIERE REVISIÓN HUMANA: auth y SQL)

**a) Tope de reenvíos del código de registro.** En `EmailCodeService.issue`, para `verify_email`, se cuentan en la BD las filas de `email_verifications` del usuario de las últimas 24 h: máximo 5 por hora y 10 por día (misma lógica que DEC-63, una función pura `resendCapAllowedAt`). Pasado el tope: 429 `TooManyRequests` con `details.retryAfterSeconds`. Cada envío real crea una fila, así que contar filas es contar envíos; no hay tabla ni columna nuevas. El desafío señuelo (correo con cuenta ajena) lleva el mismo conteo en memoria, para que el 429 no revele si el correo existe.

**b) Cuentas sin verificar que no vencen.** Lo más simple: reemplazo al registrar, sin tarea periódica. En `register`, si el correo ya existe, se intenta anonimizar esa fila con un solo `UPDATE ... WHERE` condicionado (sin verificar, no eliminada, creada hace más de 24 h, sin elementos de bóveda); si afecta una fila, se borran sus hijas (sesiones, códigos, PII de auditoría), se registra el evento en la cadena y el registro sigue como un correo libre. Una cuenta verificada nunca cumple el `WHERE`. Un verificado en paralelo tampoco: el `UPDATE` toma el candado de `users` primero (INV-20). Los datos de anonimizado se comparten con `deleteAccount` (una sola función). Una purga periódica exigiría un planificador que el proyecto no tiene.

## Pasos y commits

| Paso | Commit | Qué |
|---|---|---|
| 1 | `988e2d1` (app) | «Más débiles» excluye `StrengthClass.Strong` antes de ordenar y tomar 10; estado vacío `health_ranking_weakest_empty`; 3 pruebas nuevas del ViewModel (fuerte fuera; flujo `admin123` → cambiada → «Más débiles» vacío y «Mejoradas» con ella; 12 débiles → 10) y 2 ajustadas (usaban puntajes 0–4 y 12 elementos: solo 8 no eran fuertes). |
| 2 | `bd27953` (backend) | Tope de reenvíos (BR-28) y reemplazo de cuentas sin verificar de más de 24 h (BR-29). Función `anonymizedUserData` compartida con `deleteAccount`. |
| 3 | `320ef92` (app) | Grafos 1.1.0 (backend) y 1.10.0 (app), `docs/api/brechas-backend.md`, sección «Probar con el backend local» del README y `estado-actual.md`. |
| 4 | este commit | Conteos del README, `siguiente.md` hecha y este reporte. |

**Paso 3, lo que cambió en los grafos:** BR-06, 07, 16, 17, 18, 22, 23, 25 y 27 «resuelta» con su commit; BR-10, 20 y 21 «adaptada en la app»; **también marqué como «adaptada en la app» (e9a7e9c) BR-01…05, 08, 09 y 11…15**, que eran del adaptador y estaban «abierta» aunque ya están hechas (no lo pedía la tarea; es reversible). BR-19, 24 y 26 siguen abiertas. Nuevas: BR-28 y BR-29. En la app: RSG-16 (bloqueo de cuenta provocable, BR-22) y RSG-17 (cambio de correo por tiempo), la regla de «Más débiles» en SCR-25, y **SCR-47 y SCR-48** (cambio de correo: correo nuevo y código; no existían como pantallas, y no tienen nodo en Figma, queda anotado). Aristas validadas: ninguna huérfana.

## Resultados reales
- Backend: `npm run build` bien; `npm test` **38/38** (eran 25); `eslint src` sin salida (0 errores).
- Backend, prueba de punta a punta contra PostgreSQL local: **12/12** pasos (`node scripts/e2e/flujo-completo.mjs`). Los pasos 11 y 12 envejecen la fila con Prisma y prueban en la base real que una cuenta sin verificar de más de 24 h se reemplaza, que una vigente no se toca y que una verificada nunca se toca. El script carga `.env` con `process.loadEnvFile` (no lo leí ni lo imprimí).
- App: `testProdDebugUnitTest` **941/0**, `testDemoDebugUnitTest` **1069/0**, `lintProdDebug` **0 errores** (30 avisos, 2 sugerencias, los de antes), `assembleProdDebug` bien.
- Push de ambas ramas.

## `/ponytail-review`
Lo hice sobre el diff de las dos ramas, sin subagente. Sin hallazgos que arreglar. Lo que revisé y por qué no lo toqué:
- El tope cuenta con «leer y luego escribir»: dos reenvíos en paralelo podrían pasarse por uno. Es el mismo margen que ya tiene la espera de 60 s; aceptable.
- Con el tope alcanzado en login con cuenta sin verificar, `emailVerificationRequired` traga el error y responde `codeSent: false` con `resendAvailableInSeconds: 0`; la app ofrece reenviar y entonces recibe el 429. Correcto, solo poco elegante.
- Reemplazar una cuenta sin verificar tras 24 h hace perder el registro pendiente a quien tardó en verificar; es el comportamiento pedido.

## Pendiente
- Paso 0: restaurar `gradle/libs.versions.toml` y `ai/handoff/README.md` en la carpeta original (arriba).
- El backend de integración que dejé corriendo en el puerto 3001 sigue con el código anterior en memoria; reinícialo para probar el tope y el reemplazo en el teléfono.
- Figma: SCR-47 y SCR-48, la hoja de contraseña antes de la clave de 2FA y el código de respaldo `xxxxx-xxxxx` en `prod`.
- Siguen abiertas BR-19, BR-24 y BR-26.

**REQUIERE REVISIÓN HUMANA:** autenticación y SQL (tope de reenvíos y reemplazo de cuentas sin verificar).
