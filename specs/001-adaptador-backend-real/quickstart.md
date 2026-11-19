# Quickstart: validar el adaptador

## Requisitos
- Backend `integracion/app` corriendo en local (`npm run start:dev`, puerto 3000) con su `.env` y la base migrada.
- Android SDK; para el teléfono, `adb`.

## Sin teléfono
1. `./gradlew testProdDebugUnitTest` — el traductor contra respuestas reales del backend (contracts/translation.md).
2. `./gradlew testDemoDebugUnitTest` — la demo sigue igual.
3. `./gradlew assembleProdDebug lintProdDebug`.
4. Punta a punta por HTTP: `node scripts/e2e/flujo-completo.mjs` en el backend (fase 6 de la tarea 32).

## Con teléfono
1. `adb reverse tcp:3000 tcp:3000`
2. Instalar `prodDebug`, registrarse y leer el código en la consola del backend (`[MailDev]`).
3. Recorrer las historias de spec.md. Esperado: ningún mensaje del servidor en pantalla; conflictos y límites con los
   textos de la app.
