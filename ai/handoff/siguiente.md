# Tarea 36 · Preparar la producción: dominio, correo real y despliegue

**Estado:** hecha
**Escrita:** 2026-11-18 · sesión de diseño

## Revisión de la tarea 35
Aprobada. Probada en el teléfono: «Apareció más de 10,000 veces en filtraciones» se ve completo.

## Contexto (decisiones de la persona)
- **Dominio:** `endsecurity.lat` (Spaceship). El servidor irá en **`api.endsecurity.lat`**.
- **Correo:** Resend, con remitente `End Security <no-reply@endsecurity.lat>`. La persona verifica el dominio en Resend y pega los registros DNS en Spaceship; la clave de Resend la pone ella en las variables del servidor.
- **Despliegue:** probablemente Railway. Lo hace la persona o su compañero Diego; **tú no despliegas ni tienes credenciales**. Tú dejas el código listo y una guía paso a paso.
- **Correos de contacto:** se mantienen `endsecurity.privacidad@gmail.com` y `endsecurity.soporte@gmail.com`. Buzones con el dominio quedan como mejora futura (ver Paso 4).
- **Pines SPKI (BR-26):** no en esta tarea. Se ponen cuando el servidor esté en línea (tarea 37).

**Ramas:**
- Backend: `integracion/app` (PR #1). Si ya la mezclaron en `develop`, crea `integracion/despliegue` desde `develop`.
- App: `integracion/backend` (PR #2). Si ya la mezclaron en `development`, crea `integracion/despliegue-app` desde `development`.
- Sin merge. Nada directo en `develop`, `development` ni `main`.

**Reglas:**
- sin capturas; no leas `.env*`, claves ni `keystore.properties`; ponytail en `full`;
- trabaja en las carpetas originales; no te detengas entre pasos salvo un bloqueo real;
- cambios en autenticación o arranque llevan **REQUIERE REVISIÓN HUMANA**.

---

## Paso 1 · Backend: arranque seguro en producción (`integracion/app`)
Revisa `src/config/env.validation.ts`, `src/main.ts` y `src/modules/mail`. Con `NODE_ENV=production` el servidor **debe negarse a arrancar** (mensaje claro, sin imprimir valores) si:
- falta `RESEND_API_KEY` o `EMAIL_FROM` (hoy sin clave solo se escribe en consola, y eso solo debe pasar fuera de producción);
- `JWT_SECRET`, `JWT_REFRESH_SECRET` o `AUDIT_HMAC_KEY` miden menos de 32 caracteres, o contienen `change_in_production` (ya existe parte de esto: no lo dupliques);
- falta `TOTP_ENCRYPTION_KEYS`, o `DATABASE_URL`;
- `CORS_ORIGIN` es `*` (la app es nativa y no usa CORS: en producción debe poder ser una lista vacía o un valor fijo; decide la forma más simple y documéntala).

`TRUST_PROXY`: en producción detrás de Railway debe ser `1`; si es `0` en producción, **avisa por log** (no falla), porque rompería el límite por IP.

**Salud:** revisa lo que expone `AppController`. Debe haber un `GET /api/health` público, sin autenticación, que responda `200` solo si la base contesta (una consulta mínima), y que **no devuelva versiones ni detalles**. Si ya existe, déjalo; si no, créalo. Si Railway lo necesita, añade su `healthcheckPath` en el archivo de configuración que corresponda.

**Migraciones y arranque:**
- `package.json`: que `npm run build` genere el cliente de Prisma (`prisma generate`) sin pasos manuales, y que haya un script para aplicar migraciones en producción (`prisma migrate deploy`).
- No agregues Dockerfile, ni dependencias, ni planificadores. Solo lo que haga falta para que Railway (Nixpacks/Railpack) compile y arranque con `npm run build` y `npm run start:prod`.

**Pruebas (Jest):** la validación de entorno en producción (cada caso de arriba falla; uno bueno pasa) y el `health`. `npm run build`, `npm test` y lint, con el resultado real.

## Paso 2 · Backend: guía de despliegue
Crea `docs/despliegue.md` (en español, pasos numerados y cortos, para alguien que nunca ha usado Railway):
1. Crear el proyecto, el servicio desde el repositorio (rama `develop` o `main`, la que acuerden) y la base PostgreSQL.
2. **Variables**, solo los nombres y cómo obtener cada una. Los secretos se generan en la computadora de la persona con el comando de Node del `.env.example`, y **nunca** se copian los del entorno local. Incluye `DATABASE_URL` (la referencia que da Railway), `NODE_ENV=production`, `TRUST_PROXY=1`, `PORT` (si Railway lo inyecta, no lo fijes), `RESEND_API_KEY`, `EMAIL_FROM=End Security <no-reply@endsecurity.lat>`.
3. Comando previo al despliegue: `prisma migrate deploy`.
4. Dominio personalizado `api.endsecurity.lat`: el registro **CNAME** que indica Railway, pegado en Spaceship → DNS. Esperar a que el certificado HTTPS quede emitido.
5. Resend: agregar el dominio, pegar los registros en Spaceship, esperar «Verified». La app **no** envía nada de esto: es del servidor.
6. Comprobaciones: `https://api.endsecurity.lat/api/health` → 200; registrar una cuenta de prueba desde un `curl`/script o desde la app y comprobar que **llega el código por correo** (no por consola); revisar en los logs de Railway las líneas syslog RFC 5424.
7. Costos y apagado: nota de que Railway cobra por uso; qué hacer para pausar el servicio después de la entrega.
8. **Qué hacer si algo falla:** el log, `migrate status` y la restauración de la base (respaldos del plan).

## Paso 3 · App: URL de producción y red (`integracion/backend`)
- `app/build.gradle.kts`: `API_BASE_URL` de `release` = `"https://api.endsecurity.lat/api/"`. La de `debug` sigue en `http://127.0.0.1:3000/api/`.
- Opcional si es corto: que `-PapiBaseUrl=...` sobrescriba la URL de `debug`, para probar contra el servidor real con la app de debug sin recompilar el código.
- Comprueba que `network_security_config.xml` del `main` sigue sin permitir tráfico en claro, y que el texto plano a `127.0.0.1` vive **solo** en `src/debug`. Si no es así, arréglalo.
- **No** pongas pines SPKI todavía. Deja en `network_security_config.xml` un comentario con `TODO(BR-26)` que explique qué falta.
- Pruebas: `./gradlew testProdDebugUnitTest testDemoDebugUnitTest lintProdDebug assembleProdDebug assembleProdRelease`. `assembleProdRelease` puede salir sin firmar si no hay `keystore.properties`; no lo leas ni lo imprimas.

## Paso 4 · Grafos y documentos
Parchea los JSON directamente; sube la versión y agrega una línea a `historial`.
- **`knowledge-graph.json`:**
  - **DEC-67** «Dominio, correo y despliegue»: dominio `endsecurity.lat`, servidor en `api.endsecurity.lat`, remitente `no-reply@endsecurity.lat`, contacto legal con los dos Gmail, despliegue por la persona o por Diego con la guía `docs/despliegue.md`. Con aristas hacia DEC-61, DEC-65 y DEC-64.
  - Un nodo `fuera_de_alcance` nuevo: «Buzones de contacto con el dominio propio (`privacidad@`, `soporte@`)», como mejora futura. Una línea: hoy no afecta el envío de correos de verificación.
- **`backend-graph.json`:** BR-24 pasa a «en curso (la persona verifica el dominio en Resend)» y BR-26 a «pendiente de servidor en línea (tarea 37)». Anota el arranque seguro como parte de `BSEC-*` si corresponde.
- **`README.md` de la app:** una línea que enlace a `docs/despliegue.md` del backend y diga que `release` usa `https://api.endsecurity.lat/api/`.
- **`ai/context/estado-actual.md`:** al día.
- **`docs/legal/aviso-de-privacidad.md`:** **no lo cambies.** Falta el proveedor de hosting (E4); lo hará la persona cuando sepa dónde queda.

## Cierre
1. Verifica y reporta los resultados reales de ambos proyectos.
2. `/ponytail-review` en las dos ramas.
3. Commits y push en ambas; los PR #1 y #2 (o los nuevos) se actualizan.
4. Reporte en `ai/handoff/reporte.md`: qué cambió, resultados, y **qué hace falta de la persona o de Diego** para desplegar (en una lista corta y ordenada).
5. Estado `hecha`, commit y **detente**.
