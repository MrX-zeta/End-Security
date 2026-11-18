# Tarea 32 · Integración completa con el backend real (salvo el correo)

**Estado:** pendiente
**Escrita:** 2026-11-18 · sesión de diseño

Es una tarea **grande**: el equipo la revisa cuando esté todo integrado.
- **No te detengas entre fases.** Detente solo al terminar o si algo te bloquea de verdad.
- Si una parte se bloquea, anótala y sigue con las demás.
- Haz commits pequeños y frecuentes; así se revisa por partes.

**Fuera de esta tarea:**
- el correo real (Resend, BR-24): sin `RESEND_API_KEY`, el backend escribe los correos en su consola, y así se prueba;
- el despliegue, la URL pública y los pines SPKI (BR-26): todo es local por ahora.

**Dos carpetas:**
- la app (`proyecto-U4`, grafo `ai/context/knowledge-graph.json` v1.9.0);
- el backend (`end-security-backend`, grafo `ai/context/backend-graph.json`, que está en la carpeta de la app).

Lee primero DEC-61, DEC-65 y DEC-66, y las brechas `BR-*`.

**Ramas (no negociable):**
- Backend: `integracion/app`, que sale de `develop`. Ni un commit en `develop` ni en `main`.
- App: `integracion/backend`, que sale de `development`.
- PR al final. Tú no haces merge.

**Seguridad:**
- No leas ni imprimas `.env*`, `keystore.properties`, llaves ni contraseñas.
- Lo que toque crypto, auth o SQL lleva un plan breve antes del código, escrito en el reporte (no esperes aprobación). Además, **REQUIERE REVISIÓN HUMANA**.
- Sin capturas de pantalla.

---

## Fase 0 · Pendientes de diseño (app, en `development`)
Haz un commit con:
- `ai/context/knowledge-graph.json`, `ai/context/backend-graph.json`, `ai/context/build_backend_graph.py`;
- `CLAUDE.md`, `docs/legal/aviso-de-privacidad.md` y este archivo.

**No incluyas** `docs/bd/end_security_db.jpg`; pregunta por él en el reporte.

## Fase 1 · Base de datos local (en paralelo, la prepara la persona)
La persona instala PostgreSQL y crea un rol `end_security` con `LOGIN CREATEDB`, más la base `end_security`. Escribe ella misma el `DATABASE_URL` en `.env` del backend.

Tú:
1. Completa el resto de `.env` desde `.env.example` con un script que **genere** los secretos y no los imprima: `JWT_SECRET`, `JWT_REFRESH_SECRET`, `AUDIT_HMAC_KEY` y `TOTP_ENCRYPTION_KEYS`.
   - Nunca sobrescribas `DATABASE_URL`.
   - `RESEND_API_KEY` va vacía.
   - `NODE_ENV=development` y `TRUST_PROXY=0`.
   - Comprueba que `.env` esté en `.gitignore`.
2. `npm install`, `npx prisma migrate deploy` y `npm run start:dev`. Comprueba que arranca.
3. Si la base no está lista cuando llegues aquí, sigue con las fases 2 y 3, y vuelve después.

## Fase 2 · Herramientas (DEC-66)
**ponytail:** ya lo instala la persona.

**Spec Kit**, solo en la app, en `integracion/backend`:
1. Comprueba Python 3.11+ y `uv`; si falta, `uv tool install specify-cli`.
2. Corre `specify init --here --force --integration claude`. **Sin** `--no-model-invocation`: esa opción impide que tú invoques los skills `/speckit-*` y la persona no estará para teclearlos.
3. No debe pisar `CLAUDE.md` ni lo que ya existe en `.claude/`. Si lo pisa, restáuralo.
4. Arma la constitución con `/speckit-constitution` **solo** con lo que ya existe: `INV-*`, `CLAUDE.md` y `.claude/rules/`. Cita el id de cada regla.
   No inventes principios; si algo choca, gana el grafo.
5. Haz un commit aparte.

**Trabajo sin supervisión:** la persona no estará y no se detiene por ponytail.
- Ponytail se queda en `full` todo el tiempo.
- Constitución, especificación, plan y lista de tareas de Spec Kit son documentos, no código: escríbelos completos y claros. La regla de ponytail aplica al código que implementas.
- Si un skill `/speckit-*` no se deja invocar, lee su `SKILL.md` en `.claude/skills/` y sigue sus pasos tú mismo.
- Cuando un skill te pida preguntar a la persona, decide con el grafo y `CLAUDE.md`, y anota la decisión en el reporte. Solo te detienes si es irreversible y puede ir de dos maneras.

Agrega una sección corta en `ai/handoff/README.md`:
- Spec Kit es para funcionalidades grandes;
- `/ponytail-review` va antes de cada PR.

## Fase 3 · Auditoría del backend
Corre `/ponytail-audit` en `integracion/app`. En el reporte pon **solo** lo nuevo, lo que no esté ya en `BR-*`.
Arregla lo crítico de seguridad que encuentres; lo demás, solo anótalo.

## Fase 4 · Brechas del backend (`integracion/app`)
| Brecha | Qué hacer |
|---|---|
| BR-06 | `RegisterDto.userId` (`@IsUUID`) y crear el usuario con ese id. Si ya existe, error genérico. |
| BR-23 | `DEFAULT_KDF_PARAMS.parallelism = 1`. Piso: `memoryKib ≥ 65536`, `iterations ≥ 3`, `parallelism = 1` (DEC-05). |
| BR-07 | `displayName` opcional en el registro. |
| BR-16 | Columna `marketing_opt_in` (default `false`), con su migración Prisma. Va en register, `GET /users/me` y el `PATCH`. |
| BR-17 | Cambio de correo (EP-15/16, DEC-63): 3 por hora, 5 por día, 24 h entre cambios. El correo nuevo debe ser distinto tras normalizar (400). Los conteos van en la BD. Con su aviso y su evento de auditoría. |
| BR-18 | `expectedKeyVersion` opcional al cambiar la contraseña. Si no coincide, 409 `KeyVersionMismatch`, como en `rotateKey`. |
| BR-22 | 5 fallos de login en 15 min por cuenta, contados en la BD. Mismo mensaje genérico. Se suma al límite por IP. |
| BR-25 | Syslog RFC 5424 según `docs/backend/README.md` §5 de la app (DEC-62). Sin secretos ni `authHash`. |
| BR-27 | Jest para cada cambio. Como mínimo: registro con `userId`, piso KDF, límites del cambio de correo y bloqueo por cuenta. |

**No cambies:**
- las rutas;
- la envoltura `{success,data}`;
- los errores;
- los códigos de recuperación (BR-10);
- el catálogo de auditoría (BR-21).

Todo eso lo adapta la app. Al terminar: `npm run build`, pruebas y lint, con el resultado real. Un commit por brecha.

## Fase 5 · Adaptador de la app (`integracion/backend`, con Spec Kit)
Usa `/speckit-specify`, `/speckit-plan` y `/speckit-tasks` con la guía de desarrollo por contrato de Spec Kit. El contrato real es el código del backend en `integracion/app`.

Luego `/speckit-implement`.

**Objetivo:** la variante `prod` habla con el backend real a través de las mismas interfaces (`AuthApi`, `VaultApi`, `AccountApi`, `SessionApi`). La variante `demo` no cambia.

**Lo que traduce el adaptador** (las brechas BR-01…05, 08…15, 20 y 21):
- prefijo `/api`, envoltura y errores, que se mapean a los `code` que ya usa la app;
- base64 estándar y nombres de campos;
- `challengeToken` y las tres respuestas del login;
- sesión revocada;
- sync por lotes, con D-4 resuelto en lotes de 1 (BR-13), y conflictos con `sync?since` (BR-14);
- `/users/me`;
- códigos de recuperación en formato 5-5: la app los muestra y los acepta tal cual llegan;
- catálogo de actividad: mapea los eventos conocidos y oculta los demás.

**URL base:** va en `buildConfigField` de `prod`.
- `debug`: `http://127.0.0.1:<PORT>/api/`, y en el teléfono `adb reverse tcp:<PORT> tcp:<PORT>`.
- `release`: queda vacío y con un `TODO(BR-26)`.
- `network_security_config`: texto plano **solo** en `debug` y solo para `127.0.0.1` y `10.0.2.2`. Release sigue solo con HTTPS.

**Si un flujo necesita un dato que la UI no pide** (por ejemplo, el 2FA pide `authHash`, BR-20):
- reutiliza un componente que ya exista (hoja de contraseña maestra);
- textos en `strings.xml`;
- anótalo en el reporte para pasarlo a Figma.

**Pruebas:**
- unitarias del adaptador con respuestas reales del backend;
- `./gradlew assembleProdDebug testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug`.

## Fase 6 · Prueba de punta a punta (sin teléfono)
Con el backend local corriendo, prueba el flujo completo por HTTP con un script que use el mismo cifrado de la app o sus pruebas JVM. El script va en el backend, en `scripts/e2e/`, y no se versiona con secretos.

El flujo:
1. registro;
2. verificación, con el código leído de la consola;
3. login;
4. crear, editar y borrar elementos;
5. un conflicto;
6. activar 2FA y entrar con TOTP y con un código de recuperación;
7. cambiar la contraseña;
8. cambiar el correo;
9. cerrar sesiones;
10. eliminar la cuenta.

Reporta cada paso. La prueba en el teléfono la hace la persona.

## Fase 7 · Cierre
1. `/ponytail-review` en ambas ramas; corrige lo importante.
2. `/ponytail-debt`: la lista de atajos va al reporte.
3. Push de las dos ramas y dos PR:
   - backend `integracion/app` → `develop`;
   - app `integracion/backend` → `development`.
   Cada uno con su lista de brechas.
4. En `ai/handoff/reporte.md` (app):
   - cada fase, con sus commits;
   - el resultado real de build, pruebas, lint y punta a punta;
   - lo que quedó pendiente y por qué;
   - los cambios de UI para Figma;
   - los enlaces a los PR;
   - cómo correr todo en local, en 5 líneas o menos.
5. Pon el estado `hecha`, haz commit en `development` y **detente**.
