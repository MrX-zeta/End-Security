# Tarea 01 · Arquitectura base y sistema de diseño

**Estado:** hecha
**Escrita:** 2026-10-16 · sesión de diseño

## Objetivo
Dejar el proyecto listo para construir vistas: estructura de carpetas correcta y tema Carbón y miel en Compose.
Corresponde a la **Fase 0** y la **Fase 1** del prompt inicial. **La Fase 2 (onboarding) NO es parte de esta tarea**;
llegará en la tarea 02.

## Antes de empezar
- Lee `ai/context/estado-actual.md` y, en el grafo, `DEC-37`, `DEC-50`, `DEC-51`, `DEC-59` y `DEC-60`.
- Figma: https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security (página «Minimalista»).
  Variables: colecciones «Carbón y miel · claro» y «Carbón y miel · oscuro». Biblioteca de componentes: nodo `35:10`.
  Si tienes el MCP de Figma, usa `get_variable_defs` sobre el nodo `35:10` para sacar los colores exactos.

## Pasos
1. **Fase 0:** `ui/theme` → `core/designsystem/theme`. Si el movimiento rompe imports y no puedes hacerlo de forma segura,
   detente y pide que se haga con *Refactor → Move* en Android Studio.
2. **Fase 0:** aplica DEC-37 en el manifiesto y Gradle: `allowBackup="false"`, `usesCleartextTraffic="false"`,
   R8 (`isMinifyEnabled = true`) en release. No toques `local.properties` ni archivos de firma.
3. **Fase 1:** tokens de color claro y oscuro (los nombres de las variables de Figma: `background`, `surface`, `outline`,
   `on-surface`, `on-surface-variant`, `primary`, `on-primary`, `blob`, `danger`, `success` y sus contenedores),
   tipografía Montserrat y formas. El tema oscuro es solo para el onboarding (DEC-51).
4. **Fase 1:** componentes base de `core/designsystem/component` que usan las primeras pantallas:
   botón principal, botón de texto e indicador de páginas. Nada más por ahora.

## Terminado cuando
- `./gradlew assembleDebug` y `./gradlew testDebugUnitTest` pasan (reporta la salida real).
- Hay un `@Preview` del tema claro y otro del oscuro con los componentes base.
- Commits atómicos, uno por paso.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
