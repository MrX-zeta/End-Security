# Reporte · Tarea 40 · Aviso con hosting, APK de capturas y v1.0.1

**Estado:** hecha hasta el paso 5 en el árbol de trabajo (rama `integracion/v1.0.1`); commit, push y PR los hace la persona. Falta el paso 7 (tag y release) cuando `main` esté mezclada.

- **Aviso:** apartado 4 con Railway (US East, EE. UU.) y nota de alojamiento en el extranjero. Única copia: `docs/legal/aviso-de-privacidad.md` (la app la copia como asset al compilar). `LegalPlaceholdersTest` ahora prohíbe `[proveedor de hosting`. E4 y grafo actualizados; domicilio y respaldos siguen pendientes.
- **Capturas:** `BuildConfig.SIN_FLAG_SECURE` (debug: `-PsinFlagSecure=true`; release: `false` fijo); `SecureScreen` lo respeta. Verificado en los BuildConfig generados (debug `true`, release `false`) y con `SinFlagSecureTest`. APK en `releases/capturas/EndSecurity-capturas-debug.apk`.
- **Versión:** 1.0.1 / código 2 (ajusté `DemoFlavorTest`).
- **APK:** `releases/EndSecurity-1.0.1.apk`, SHA-256 `71d3a9f1cd7ab72ae574078636752469058c690f5024fb4f7c9a0774cd0dbddb`; certificado `b5ba83bb…a8f9dd` (igual que 1.0.0). Nota: el worktree no tenía `keystore.properties`; copié el archivo (sin leerlo) desde el repo principal, compilé y lo borré del worktree.
- **Pruebas:** `testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug`, `assembleProdRelease` en verde.
- **Nota:** `ai/handoff/siguiente.md` tenía marcadores de conflicto de un stash; lo dejo con estado `hecha` y sin ellos.
