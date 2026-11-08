# Tarea 31 · README, variantes demo/prod, release v1.0.0-demo y merge a `main`

**Estado:** hecha
**Escrita:** 2026-11-07 · sesión de diseño

## Revisión de la tarea 30
Aprobada: los textos de Eliminar cuenta y `settings_sign_out_body` («bóveda cifrada»). Bien justificados los que se dejaron igual.

**Esta tarea tiene dos partes con una pausa en medio.** La firma la hace el equipo con su propia llave.
- **No leas, no abras, no imprimas ni copies** `keystore.properties`, ningún `.jks`/`.keystore`/`.p12` ni contraseñas.
- Gradle puede usarlos para firmar, pero tú no.

---

## Parte A (ahora)

### Paso 1 · Variantes `demo` y `prod` (cumple E1)
- **`flavorDimensions += "backend"`.**
- **`demo`:**
  - `USE_FAKE_BACKEND = true`;
  - nuevo `DEMO_MODE = true`, que muestra los códigos de «Modo demostración»;
  - `versionNameSuffix = "-demo"`;
  - `applicationIdSuffix = ".demo"`, para que conviva con la de producción;
  - nombre visible «End Security Demo» (`resValue` o `strings.xml` del source set `demo`).
- **`prod`:** `USE_FAKE_BACKEND = false` y `DEMO_MODE = false`.
- **El backend falso solo existe en `demo`:** mueve `core/network/fake` y lo que solo use la demo (el receptor de «otro dispositivo» ya está en `debug`) al source set **`src/demo`**, y el enlace de Hilt que elige el transporte, a `src/demo` y `src/prod`. Así el APK `prod` **no contiene** el backend falso. Si el transporte real todavía no existe, `prod` usa un `ApiTransport` que responde «sin conexión» y un `TODO(T-integración)`; el APK de `prod` no se publica hasta la integración.
- **«Modo demostración»:** hoy depende de `debug`. Que dependa de `BuildConfig.DEMO_MODE`, para que se vea en `demoRelease` y nunca en `prodRelease`.
- **Versión:** `versionName = "1.0.0"` y `versionCode = 1`.
- **Pruebas:**
  - una de que `prodRelease` tiene las dos banderas en `false`;
  - una de que la clase del backend falso no está en el classpath de `prod` (o un `lint`/`dependency` que lo compruebe);
  - las pruebas existentes siguen pasando con la variante `demoDebug`.
- Actualiza **E1** en `revisiones-pendientes.md`: queda resuelto por diseño.

### Paso 2 · Firma por configuración, sin tocar secretos
- En `app/build.gradle.kts`, un `signingConfigs.create("release")` que lee **`keystore.properties`** de la raíz del proyecto (ya está en `.gitignore`), con las claves `storeFile`, `storePassword`, `keyAlias` y `keyPassword`, usando `java.util.Properties`.
- **Si el archivo no existe:** el `release` queda **sin firmar**, sin fallar la compilación, para que Diego o cualquiera pueda compilar.
- **Nunca** registres ni imprimas sus valores (sin `println` ni `logger`).
- Agrega a `.gitignore`, por si acaso: `*.jks` y `*.keystore` (ya están), `keystore.properties` (ya está) y `release-keys/`.

### Paso 3 · README (`README.md` en la raíz, en español)
Breve pero sustancioso: listas antes que párrafos, separadores `---`, **sin emojis en los títulos** y tablas solo donde aclaren. **Explica solo el rol de este repo** (la app); el backend está en https://github.com/Diego-Zarate18/end-security-backend.

**Imágenes:** ya están en `docs/readme/`, exportadas de Figma por la sesión de diseño:
- `portada.png`: tira de 5 pantallas, va arriba;
- `boveda.png`, `detalle.png`, `generador.png`, `salud-resumen.png`, `salud-progreso.png`, `desbloquear.png`, `ajustes.png`, `dos-pasos.png`, `conflicto.png`.

Úsalas donde aporten (por ejemplo, `conflicto.png` en «Decisiones técnicas», junto a la de conflictos). Para ponerlas lado a lado usa `<img width="…">` dentro de una tabla de una fila o de un `<p>`.

**Estructura (en este orden):**
1. **Título** «End Security» y una frase: *Gestor de contraseñas para Android en el que el servidor nunca puede leer tus contraseñas.*
   - **Una sola fila** de insignias de shields.io: Kotlin, Jetpack Compose, Android (minSdk 28), AES-256-GCM, Argon2id;
   - `portada.png`.
2. **Problema que resuelve:** 3–4 viñetas. Reutilizar contraseñas, no saber cuáles están filtradas, y gestores que obligan a confiar en el servidor.
3. **Responsabilidades principales de la app:** cifrado y llaves en el teléfono, bóveda sin conexión con sincronización, salud de contraseñas, acceso (2FA, huella, bloqueo), cuenta y legal.
4. **Lo que el servidor nunca ve:** un diagrama **Mermaid** (aquí sí aporta):
   - contraseña maestra → NFKC → Argon2id (64 MiB, t = 3) → `master_key` → HKDF → `enc_key` (se queda en el teléfono) y `auth_key` (va al servidor, que guarda solo su hash Argon2id);
   - `vault_key` aleatoria envuelta con `enc_key` (pvk, AAD con `userId` y `keyVersion`);
   - cada elemento cifrado con AES-256-GCM y AAD `id|userId|formatVersion`.

   Debajo, en lista, qué recibe el servidor (bloques cifrados, sal, parámetros, hash de la `auth_key`, metadatos) y qué no (contraseñas, nombres de sitios, análisis de salud).
5. **Flujo general** en lista: registro → verificación → desbloqueo → bóveda y sincronización → salud → cierre o eliminación.
6. **Estructura interna:**
   - mapa corto de carpetas (`core/crypto`, `core/session`, `core/sync`, `core/network`, `core/security`, `core/designsystem`, `feature/*`), una línea cada una;
   - las tablas de Room (`account`, `vault_items`, `breach_checks`, `health_snapshots`), una línea cada una, más el enlace al diagrama de `docs/bd`.
7. **Comunicación con otros servicios:**
   - el contrato `docs/api/openapi.yaml` (versión actual), con enlace a `docs/api/brechas-backend.md`;
   - HIBP por k-anonimato (5 caracteres del SHA-1, con relleno);
   - Resend solo a través del backend;
   - sin analítica ni publicidad.
8. **Decisiones técnicas:** 5 o 6, cada una en 1–2 líneas **con su porqué**:
   - conflictos con versión del servidor y elección del usuario, no por la hora (con `conflicto.png`);
   - sin recuperación de la contraseña maestra;
   - índice de salud = % de sanas con tope por filtradas;
   - contraseñas parecidas solo en memoria;
   - la huella nunca se abre sola;
   - variantes `demo`/`prod`.
9. **Modelo de amenazas:** qué protege (servidor comprometido, intercepción de red, robo de la BD, capturas de pantalla, portapapeles) y qué **no** (teléfono con root, olvido de la contraseña, los metadatos que ve el servidor, la contraseña del generador en accesibilidad). Usa los RSG de `ai/context/knowledge-graph.json`.
10. **Pruébalo en 2 minutos:**
    - descarga el APK de la release `v1.0.0-demo` y crea una cuenta: el código de verificación aparece en pantalla («Modo demostración»);
    - cuentas con `+2fa` en el correo y su TOTP o códigos de respaldo de prueba, sacados de `docs/api/backend-falso.md`. **Sin poner secretos reales**, solo los de prueba que ya están documentados.
11. **Desarrollo local:** requisitos (versión de Android Studio, JDK, Android SDK 37), clonar, rama, `./gradlew :app:assembleDemoDebug` y cómo firmar con `keystore.properties` (las cuatro claves, sin valores).
12. **Pruebas:**
    - **números reales**: cuéntalos con las tareas de Gradle, no los inventes;
    - cómo correrlas;
    - que lo sensible pasó por revisión de seguridad;
    - **E3** como límite conocido.
13. **Cómo se construyó:** diseño en Figma; una sesión de diseño (Claude) que mantiene el grafo de decisiones (`ai/context`) y escribe cada tarea (`ai/handoff`); Claude Code que la implementa con pruebas; revisión de seguridad por subagente; **revisión humana** de lo marcado (`ai/handoff/revisiones-pendientes.md`).
    - Dilo **claro y sin exagerar**: la IA asistió en el diseño y el código; las decisiones, la revisión y las pruebas en el dispositivo son del equipo.
14. **Relación con el sistema y estado:** qué está terminado, que la integración con el backend real está pendiente (`brechas-backend.md`) y que llegará como `v1.1.0` (variante `prod`).
15. **Equipo:** Brian Luis Ruiz Pérez y Diego Alberto Zárate Vázquez.
    - aviso académico: Universidad Politécnica de Chiapas, sin fines comerciales;
    - enlaces al aviso de privacidad y los términos (`docs/legal`);
    - crédito de Have I Been Pwned (CC BY 4.0), Montserrat (OFL);
    - **licencia:** pregunta en el reporte cuál quiere el equipo; mientras tanto, «Todos los derechos reservados (proyecto académico)», coherente con los términos.

Revisa que **todos** los enlaces relativos funcionen en GitHub (imágenes y documentos).

### Paso 4 · Notas de la release y datos de About
- **`docs/releases/v1.0.0-demo.md`:** qué es (demo con backend simulado, todo local), cómo instalarla, el «Modo demostración», qué no incluye (servidor real) y la huella SHA-256 del certificado de firma. Esta la llenas en la Parte B.
- **Al final del reporte, para el equipo:**
  - la **descripción** de About (una línea, máximo 350 caracteres);
  - **8–12 tópicos** (`android`, `kotlin`, `jetpack-compose`, `password-manager`, `zero-knowledge`, `end-to-end-encryption`, `argon2`, `aes-gcm`, `offline-first`, `haveibeenpwned`, `material3`…).

### Paso 5 · Comprobar y detenerse
- `testDebugUnitTest` (ahora `testDemoDebugUnitTest`), lint, `assembleDemoRelease` (sin firmar si no hay `keystore.properties`) y `assembleProdRelease` pasan. La suite instrumentada solo de los paquetes tocados.
- Commits en `development` y subidos.
- Revisa que nada sensible esté versionado: `git ls-files` sin `local.properties`, llaves, `.env` ni `keystore.properties`.
- **Llena el reporte de la Parte A, deja el estado en `esperando firma` y detente.** El equipo va a crear la llave y compilar el APK firmado.

---

## Parte B (cuando el equipo diga que ya firmó)
El equipo habrá corrido `.\gradlew :app:assembleDemoRelease` con su `keystore.properties`.
1. **Comprueba el APK** `app/build/outputs/apk/demo/release/app-demo-release.apk`:
   - `apksigner verify --print-certs` (con `build-tools`): solo imprime el certificado **público**, y es lo único que se anota;
   - que `versionName` sea `1.0.0-demo` (`aapt2 dump badging`).

   Copia el APK como `End-Security-1.0.0-demo.apk` (**no lo versiones**; ya está en `.gitignore`).
2. **Merge a `main`:**
   - abre un **Pull Request** `development → main` con `gh pr create`, con un resumen de lo que entra (enlaza el README);
   - `main` es ancestro de `development`, así que no debería haber conflictos;
   - haz el merge con **merge commit** (no squash: se conserva el historial de las 31 tareas).
3. **Etiqueta y release:**
   - etiqueta `v1.0.0-demo` en el commit de `main`;
   - `gh release create v1.0.0-demo End-Security-1.0.0-demo.apk --prerelease --title "End Security 1.0.0 (demo)" --notes-file docs/releases/v1.0.0-demo.md`, con la huella del certificado ya puesta en las notas.
4. **About:** `gh repo edit --description "…" --add-topic …` con lo del Paso 4, si el equipo lo aprueba en el reporte de la Parte A. Si no tienes permiso con `gh`, déjalo en el reporte para que lo pongan a mano.
5. Cambia el estado a `hecha` y completa el reporte, con los enlaces del PR y de la release.
