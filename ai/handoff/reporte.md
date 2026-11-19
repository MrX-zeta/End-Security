# Reporte · Tarea 36

**Estado:** hecha. Nada desplegado (no hay credenciales ni se leyó ningún `.env`).
**Ramas:** backend `integracion/app` (PR #1, abierto), app `integracion/backend` (PR #2, abierto). Ninguno estaba mezclado, así que no hizo falta crear ramas nuevas. Sin merge.

## Carpeta original del backend
Estaba en `integracion/app` pero con un árbol viejo: idéntico a `d558bd0` (comprobado con `git diff d558bd0`, vacío), es decir, el estado anterior a la tarea 33, sin trabajo de la persona. Hice `git reset --hard HEAD` (quedó en `bd27953`) y `npm ci` (no había `node_modules`).

## Backend (commit `a3c1521`) · REQUIERE REVISIÓN HUMANA: arranque y configuración
- **Arranque seguro** (`src/config/env.validation.ts`): con `NODE_ENV=production` se niega a arrancar, listando solo nombres de variables (nunca valores), si falta `RESEND_API_KEY` o `EMAIL_FROM`; si un secreto JWT/auditoría tiene el valor de ejemplo o los dos JWT son iguales; o si `CORS_ORIGIN` es `*`. Lo que ya existía no se duplicó: las longitudes ≥ 32 y la presencia de `DATABASE_URL` y `TOTP_ENCRYPTION_KEYS` las exige la validación general (y la forma de la clave TOTP, `parseTotpKeys`).
- **CORS, la forma más simple:** la app es nativa, así que sin orígenes **no se habilita CORS** (`main.ts`). En producción, sin `CORS_ORIGIN` queda vacío (no el `localhost` por defecto). Se documenta en `.env.example` y en la guía.
- **`TRUST_PROXY=0` en producción:** solo avisa por syslog (nivel Warning), no falla.
- **Salud:** `GET /api/health` ya existía, pero devolvía 200 incluso con la base caída y exponía nombre del servicio y estado de la base. Ahora: 200 `{status:'ok'}` solo si `SELECT 1` contesta; 503 genérico si no. Sin versiones ni detalles.
- **Compilar y migrar:** `npm run build` = `prisma generate && nest build`; nuevo `npm run migrate:deploy`. `railway.json` (no es Dockerfile ni dependencia) con `buildCommand`, `preDeployCommand`, `startCommand` y `healthcheckPath: /api/health`.
- **`docs/despliegue.md`:** 8 pasos (proyecto y base, variables con solo nombres y cómo generar cada secreto, migraciones, dominio `api.endsecurity.lat` con CNAME en Spaceship, Resend, comprobaciones, costos y apagado, qué hacer si falla).
- **Resultados:** `npm run build` bien; `npm test` **54/54** (eran 38: 14 de validación de entorno y 2 de health); `eslint src` sin salida (0 errores).

## App (commit `d90e85a`)
- `release`: `API_BASE_URL` = `https://api.endsecurity.lat/api/`. `debug` sigue en `http://127.0.0.1:3000/api/` y acepta `-PapiBaseUrl=...` (opcional, 3 líneas).
- Red: el `network_security_config.xml` de `main` ya no permitía tráfico en claro y el texto plano a `127.0.0.1`/`10.0.2.2` ya vivía solo en `src/debug`; no hubo que arreglar nada. Agregué el comentario `TODO(BR-26)` con lo que falta (los dos pines SPKI, tarea 37). **No** hay pines.
- Grafos: app **1.13.0** (DEC-67 con aristas a DEC-61, DEC-65 y DEC-64; FDA-14 buzones con el dominio propio) y backend **1.2.0** (BSEC-10 ampliado, BR-24 «en curso», BR-26 «pendiente de servidor en línea (tarea 37)»). README y `estado-actual.md` al día. `docs/legal/aviso-de-privacidad.md` sin tocar.
- **Resultados:** `testProdDebugUnitTest` **954/0**, `testDemoDebugUnitTest` **1086/0**, `lintProdDebug` **0 errores** (31 avisos), `assembleProdDebug` y `assembleProdRelease` bien. No comprobé si el release salió firmado ni leí `keystore.properties`. La primera corrida del conjunto falló en `lintAnalyzeProdDebugUnitTest` y `...AndroidTest` sin que yo cambiara código entre medias; `lintProdDebug` sola y luego todo el conjunto pasaron. No encontré la causa (probable bloqueo de archivos al correr todo junto).

## `/ponytail-review` (inline)
Sin hallazgos que arreglar. Notas: el límite general de 100 peticiones/min por IP aplica también a `/api/health` (el healthcheck de Railway no se acerca); `AUDIT_HMAC_KEY` cambiada después invalida la verificación de lo ya registrado (anotado en la guía).

## Qué hace falta de la persona o de Diego, en orden
1. Decidir la rama a desplegar (`main` o `develop`) y **mezclar los PR #1 (backend) y #2 (app)** tras revisarlos.
2. Crear en Railway el proyecto, el servicio desde el repositorio y la base PostgreSQL (guía, paso 1).
3. Generar en su computadora `JWT_SECRET`, `JWT_REFRESH_SECRET`, `AUDIT_HMAC_KEY` y `TOTP_ENCRYPTION_KEYS` (nuevos, no los locales) y ponerlos en Railway con `NODE_ENV=production`, `TRUST_PROXY=1`, `DATABASE_URL` por referencia y `EMAIL_FROM` (paso 2).
4. En Resend: agregar `endsecurity.lat`, pegar sus registros en Spaceship, esperar «Verified», crear la clave y ponerla como `RESEND_API_KEY` (paso 5).
5. Custom domain `api.endsecurity.lat`: pegar el CNAME de Railway en Spaceship y esperar el certificado (paso 4).
6. Comprobar `/api/health` y que el código de registro **llegue por correo** (paso 6), y activar respaldos de la base antes de usar cuentas reales.
7. Avisarme para la tarea 37: sacar los pines SPKI del certificado y ponerlos en la app.
8. Completar el proveedor de hosting en el aviso de privacidad (E4).

## Pendiente
BR-26 (pines), BR-24 (dominio verificado), BR-19, y la prueba real del despliegue.
