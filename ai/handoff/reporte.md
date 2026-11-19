# Reporte · Tarea 38 · Corregir los hallazgos de la auditoría

**Estado:** hecha. **Migración Prisma:** no hubo.

## Cerrado
| id | qué se hizo | dónde |
|---|---|---|
| F-07 | Ya existía: 5 fallos en 15 min bloquean la cuenta, respuesta idéntica a credenciales inválidas, evento `account_locked` en auditoría (BR-22). No se cambió nada. | backend `auth-hash.ts`, `auth.service.ts` |
| F-03 | `ValidationPipe` sin `enableImplicitConversion`; opciones extraídas con prueba (un string no pasa por entero; el query con `@Type` sí). Los query DTO ya usaban `@Type`, no hizo falta añadir más. | backend `src/common/validation-pipe.options.ts`, `main.ts` |
| F-05 | `maxLength` en nombre (128), usuario (256), contraseña (256), URL (2048) y notas (5000); el peor caso cabe en el bloque de 32 KiB y la contraseña cubre el máximo del generador (64). Pruebas de ambos supuestos. | app `ItemLimits.kt`, `ItemEditScreen.kt` |
| F-06 | `npm audit fix` (solo lockfile): multer. | backend `package-lock.json` |

## Aceptado (documentado en `docs/despliegue.md` y en el grafo)
- F-02 → RSG-18 · F-04 → RSG-19.
- F-06 restante → RSG-20: `deepmerge-ts` vía el CLI de Prisma; el arreglo exige un salto mayor de Prisma.
- F-01 (pines SPKI, BR-26) no entra aquí.

## Pruebas
`npm test` (56) verde; `testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug` y `assembleProdRelease` en verde. Lint de `prodDebug` reporta avisos (38 entradas), ningún error. No se corrió nada en un dispositivo.

## Entrega
- Backend: commit `9fea672` empujado a `integracion/app` (se suma al PR #1, hacia `develop`).
- App: cambios en la rama `claude/tarea-38` (parte de `integracion/backend`); falta el push a `integracion/backend` (PR #2).
- El tope de `maxLength` solo aplica a lo que teclea la persona, no al texto que pone el generador.
