# Tarea 40 · Aviso de privacidad al día, APK de capturas y release v1.0.1

<<<<<<< Updated upstream
**Estado:** hecha
**Escrita:** 2026-11-19 · sesión de diseño
=======
**Estado:** pendiente
**Escrita:** 2026-11-20 · sesión de diseño
>>>>>>> Stashed changes

## Reglas
- Los commits NO llevan coautoría de IA (sin `Co-Authored-By`, `Claude-Session` ni «Generated with»). Verifica con `git log -1 --format=%B`.
- Parte de `origin/integracion/backend` (ahí está esta tarea; su contenido ya está en `main` hasta v1.0.0): crea la rama `integracion/v1.0.1` desde ahí y trabaja ahí. No leas ni imprimas `keystore.properties` ni el `.jks`. No toques el backend. No subas `releases/` ni `docs/bd/end_security_db.jpg`.
- El release (APK de producción) **conserva `FLAG_SECURE`**. No lo debilites.
- Los merges a `main` los hace la persona en GitHub.

## Trabajo
1. **Aviso de privacidad.** En `docs/legal/aviso-de-privacidad.md`, apartado 4 (tabla de encargados), sustituye `[proveedor de hosting y país del servidor]` por: **Railway (Railway Corp.), servidor en la región US East (Virginia, Estados Unidos)**. Revisa que el apartado 4 y el de transferencias/remisión mencionen que los datos se alojan en el extranjero (EE. UU.) y que la bóveda va siempre cifrada; ajusta lo mínimo si falta. Mira cómo la app obtiene este texto (`LegalDocument.kt`, strings) y actualiza TODAS las copias. Ajusta `LegalPlaceholdersTest` si vigila ese marcador (el aviso ya no debe tener ese placeholder; el domicilio y los respaldos de la BD siguen como estén). Actualiza `ai/handoff/revisiones-pendientes.md` (E4: hosting resuelto) y el grafo si cita el pendiente.
2. **Compilación de capturas.** Una propiedad de Gradle `-PsinFlagSecure=true` que apaga `FLAG_SECURE` (punto único: `SecureScreen`/`core/security`) SOLO en `debug`: en `release` se ignora y la protección siempre queda activa. Con una prueba que lo garantice (p. ej. un `BuildConfig.SIN_FLAG_SECURE` que en release sea `false` fijo). Para capturas la persona usará `prodDebug` con `-PapiBaseUrl=https://api.endsecurity.lat/api/` (https, sin pines en debug).
3. **Versión.** `versionName` 1.0.1, `versionCode` 2.
4. **Compilar.**
   - `./gradlew assembleProdRelease` → verifica con `apksigner verify --print-certs` (mismo certificado de v1.0.0, huella SHA-256 `b5ba83bb…`) y copia a `releases/EndSecurity-1.0.1.apk`; SHA-256.
   - `./gradlew assembleProdDebug -PsinFlagSecure=true -PapiBaseUrl=https://api.endsecurity.lat/api/` → `releases/capturas/EndSecurity-capturas-debug.apk`. Comprueba que `FLAG_SECURE` no se aplica en esa compilación y que en la release sí.
   - Pruebas: `testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug` en verde.
5. **Documentación.** README: «Instalar» con v1.0.1, SHA-256 y huella; `docs/release/v1.0.1.md` (cambios: aviso con hosting; nada más) y cómo generar la compilación de capturas (solo para evidencias, no se distribuye). `estado-actual.md` al día.
6. **Entrega.** Commit(s) y push de `integracion/v1.0.1`; abre PR a `main` con `gh api` (la persona lo mezcla). `ai/handoff/reporte.md` corto. Estado `hecha` y detente.
7. **Tag y release (solo cuando la persona confirme que `main` está mezclada):** tag `v1.0.1` sobre `main`, `gh release create v1.0.1` con `EndSecurity-1.0.1.apk`, su SHA-256 y las notas; marca «latest». NO adjuntes el APK de capturas. Conserva `v1.0.0` y `v1.0.0-demo`.
