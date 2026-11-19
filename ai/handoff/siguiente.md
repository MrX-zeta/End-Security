# Tarea 39 · Pines SPKI, APK firmado de producción y release v1.0.0

**Estado:** pendiente
**Escrita:** 2026-11-19 · sesión de diseño

## Reglas
- Los commits NO llevan coautoría de IA (sin `Co-Authored-By`, `Claude-Session` ni «Generated with»). Verifica con `git log -1 --format=%B`.
- Parte de `origin/integracion/backend`. No leas ni imprimas `keystore.properties` ni el `.jks`; nunca los subas. No toques el backend. No subas `docs/bd/end_security_db.jpg`.
- Los merges a `development` y `main` los hace la persona en GitHub; tú los preparas y, cuando ella confirme, creas el tag y el release.

## Trabajo
1. **Pines SPKI (BR-26).** Servidor en línea: `https://api.endsecurity.lat`. Permitido UNA conexión TLS de solo lectura a ese host (`openssl s_client`) para obtener la cadena de certificados públicos y calcular los SHA-256 SPKI. Pin 1: la clave pública de la CA intermedia vigente (estable ante la renovación de 90 días de Let's Encrypt). Pin 2 (respaldo): otra intermedia de Let's Encrypt de la misma cadena o raíz (ISRG Root X1/X2) para sobrevivir a una rotación. NO fijes la hoja. Ponlos en `network_security_config.xml` solo para `api.endsecurity.lat`, con `expiration` razonable (≈ 2027-12-31) y comentario con cómo regenerarlos. Debug (`127.0.0.1`/`10.0.2.2`) no lleva pines. Prueba unitaria o de configuración mínima. Si la conexión falla o no se puede calcular con certeza, NO inventes pines: déjalo como estaba y repórtalo.
2. **Versión.** `versionName` 1.0.0 y `versionCode` coherente en `app/build.gradle.kts`.
3. **Compilar y firmar.** `./gradlew assembleProdRelease` (la firma sale de `keystore.properties` sin que lo leas). Verifica con `apksigner verify --print-certs` (solo huella del certificado, sin contraseñas) y que NO sea la firma debug. Copia el APK a `releases/EndSecurity-1.0.0.apk` FUERA del control de versiones (añade `releases/` a `.gitignore` si hace falta) y calcula su SHA-256. Pruebas en verde (`testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug`).
4. **Documentación.** README: sección «Instalar» con descarga del release, SHA-256 y huella del certificado de firma; versión 1.0.0. Grafos y `estado-actual.md`: BR-26 resuelto (o pendiente si el paso 1 falló), release 1.0.0. Notas del release en `docs/release/v1.0.0.md` (qué incluye, requisitos, cómo verificar el APK).
5. **Entrega y merge.** Commit(s) en `integracion/backend`, push, y deja el PR #2 listo (`integracion/backend` → `development`). Reporta en `ai/handoff/reporte.md` el estado de los PR y qué falta. Luego di a la persona que haga: merge del PR #2, y PR `development` → `main` (lo abres tú con `gh pr create`; lo mezcla ella).
6. **Tag y release** (solo cuando la persona confirme que `main` está mezclada): `git tag v1.0.0` sobre `main`, `gh release create v1.0.0` con el APK, su SHA-256 y las notas. Marca el release como «latest». Mantén el release `v1.0.0-demo` como está.

## Entrega
Estado `hecha` en este archivo tras el paso 5; el paso 6 se anota en un segundo commit. Detente.
