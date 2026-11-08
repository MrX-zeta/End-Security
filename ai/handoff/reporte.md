# Reporte · Tarea 31 · Parte A · Variantes demo/prod, firma por configuración y README

**Estado:** esperando firma (Parte B pendiente). No toqué `keystore.properties`, llaves ni contraseñas; la firma solo la lee Gradle.
Tocó configuración de build e inyección del transporte (no `core/crypto`, sesión, autenticación ni SQL). No hace falta «REQUIERE REVISIÓN HUMANA» por código sensible, pero conviene revisar el Paso 1 (qué queda fuera de `prod`).

## Paso 1 · Variantes
- `flavorDimensions += "backend"`.
  - `demo`: `USE_FAKE_BACKEND=true`, `DEMO_MODE=true`, `applicationIdSuffix=.demo`, `versionNameSuffix=-demo`, nombre «End Security Demo» (`src/demo/res/values/strings.xml`).
  - `prod`: ambas banderas en `false`.
  - `versionName="1.0.0"`, `versionCode=1` (el APK demo queda `1.0.0-demo`).
- Movido a `src/demo`: `core/network/fake/*` y el módulo Hilt (`DemoNetworkModule`: `FakeTransport` como `ApiTransport` y `FakeVerificationCodes` como `DebugVerificationCodes`).
- `src/prod/.../ProdNetworkModule`: `ApiTransport` que lanza `IOException("Sin conexión…")` con `TODO(T-integración)` y `DebugVerificationCodes` que siempre devuelve `null`.
- `OtherDeviceReceiver` y su manifiesto pasaron de `debug` a `src/demoDebug`.
- «Modo demostración» y `+2fa` ahora dependen de `BuildConfig.DEMO_MODE` (antes `DEBUG`/`USE_FAKE_BACKEND`): se ven en `demoRelease`, nunca en `prod`.
- `NetworkModule` (main) ya no conoce el backend falso.
- Pruebas que usan el backend falso movidas a `src/testDemo` (11 archivos) y `src/androidTestDemo` (3). Nuevas: `DemoFlavorTest` (banderas true, id y versión) y `ProdFlavorTest` (banderas false, `FakeTransport`/`FakeServer`/`FakeVerificationCodes` ausentes del classpath, sin sufijo demo).
- Comprobado en los APK sin firmar: el `classes.dex` de `prod` no contiene `network/fake` ni `fake_server`; el de `demo` sí. El `BuildConfig` generado de `prodRelease` tiene las dos banderas en `false`.
- E1 en `revisiones-pendientes.md`: marcado como resuelto por diseño.
- `docs/api/backend-falso.md` actualizado (`+2fa` y el receptor de «otro dispositivo» viven en `demo`/`demoDebug`; el comando `adb` usa `com.luis.end_security.demo`).
- Nota: `./gradlew testReleaseUnitTest` no existe en este proyecto (no hay pruebas unitarias de release); la comprobación de `prodRelease` es por el `BuildConfig` generado y el APK.

## Paso 2 · Firma
- `signingConfigs.release` lee `keystore.properties` de la raíz con `java.util.Properties` solo si existe; sin él, `release` queda sin firmar y compila (verificado: `app-demo-release-unsigned.apk`, `app-prod-release-unsigned.apk`). No se imprime ningún valor.
- `.gitignore`: añadido `release-keys/` (el resto ya estaba).

## Paso 3 y 4 · Documentos
- `README.md` en la raíz, con las 15 secciones en el orden pedido, Mermaid, imágenes de `docs/readme/` y enlaces relativos a archivos que existen (`docs/api/*`, `docs/bd/modelo-datos.md`, `docs/legal/*`, `docs/arquitectura.md`, `ai/handoff/revisiones-pendientes.md`).
- Dos enlaces apuntan a algo que solo existirá tras la Parte B: la release `v1.0.0-demo` y, dentro de las notas, `docs/api/backend-falso.md` en `main`.
- `docs/releases/v1.0.0-demo.md`, con la huella SHA-256 como `PENDIENTE` hasta la Parte B.

## Comprobaciones reales
- `testDemoDebugUnitTest`: **1064 / 0 fallas**. `testProdDebugUnitTest`: **920 / 0 fallas** (incluye las 3 de `ProdFlavorTest`).
- `lintDemoDebug`: 0 errores, 28 avisos, 2 sugerencias.
- `assembleDemoRelease` y `assembleProdRelease`: OK (sin firmar).
- `compileDemoDebugAndroidTestKotlin` y `compileProdDebugAndroidTestKotlin`: OK. Las 3 clases movidas a `androidTestDemo` (`AccountWipeIntegrationTest`, `LoginRecoveryIntegrationTest`, `SyncConflictDeviceTest`) corridas en `emulator-5554` con `connectedDemoDebugAndroidTest`: **16 pruebas, 0 fallas**.
- 365 métodos `@Test` instrumentados (conteo del código, README lo cita así).
- `git ls-files`: sin `local.properties`, `.env`, `keystore.properties`, `.jks`, `.p12` (solo coinciden archivos fuente de `core/crypto/keystore/`).

## Para el equipo
1. **Firmar:** crear la llave y el `keystore.properties` (cuatro claves) y correr `.\gradlew :app:assembleDemoRelease`; avisarme para la Parte B.
2. **Licencia:** ¿cuál prefieren (MIT, Apache-2.0, GPL-3.0…)? Mientras tanto el README dice «Todos los derechos reservados (proyecto académico)».
3. **Commits:** hechos y subidos a `development` (`60496fe` código, `2a26499` documentos); `keystore.properties` no está versionado y está ignorado por `.gitignore`.
4. **About (descripción, 214 caracteres):** «Gestor de contraseñas para Android con cifrado de conocimiento cero: el servidor solo ve bloques cifrados. Bóveda sin conexión, 2FA TOTP y detección de filtraciones con Have I Been Pwned (k-anonimato).»
5. **Tópicos:** `android`, `kotlin`, `jetpack-compose`, `material3`, `password-manager`, `zero-knowledge`, `end-to-end-encryption`, `argon2`, `aes-gcm`, `offline-first`, `haveibeenpwned`, `totp`.

## Bloqueo de la Parte B
- En la raíz **no existe `keystore.properties`**, así que `assembleDemoRelease` solo produce `app-demo-release-unsigned.apk`; `apksigner verify` responde «DOES NOT VERIFY: Missing META-INF/MANIFEST.MF». Repetir con `--rerun-tasks` daría el mismo resultado.
- Para seguir: crear `keystore.properties` en la raíz (cuatro claves) y correr `.\gradlew :app:assembleDemoRelease`; avisar y continúo con PR, merge, etiqueta, release y About.
