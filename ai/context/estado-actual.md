# Estado actual — End Security

> Claude lo lee al empezar cada sesión y lo actualiza con `/registrar-sesion`.
> Última actualización: 2026-10-15 (vistas, flujos y prototipo)

## Hecho
- Análisis adversario de la propuesta; huecos documentados y resueltos (GAP-01…GAP-37).
- Alcance cerrado: modo híbrido, multi-dispositivo, 2FA TOTP + códigos de recuperación, Resend de alcance mínimo, sin administrador, lista plana.
- Grafo de conocimiento v1.3 (`ai/context/knowledge-graph.json`): 60 decisiones, 47 operaciones, 45 pantallas con su navegación.
- Arquitectura MVVM (`docs/arquitectura.md`) y carpetas creadas en `app/`.
- Modelo de datos v1.1: `docs/bd/schema.sql` (probado en PostgreSQL 16; 36 pruebas + concurrencia), Room (ahora con `health_snapshots`), DataStore y Redis (`docs/bd/modelo-datos.md`). Diagrama ER en FigJam.
- Configuración de Claude Code: `CLAUDE.md`, `.claude/settings.json`, reglas por ruta, subagente `security-reviewer`, skill `/registrar-sesion`.
- Identidad visual **Carbón y miel** + Montserrat (DEC-50): onboarding oscuro, resto de la app claro (DEC-51).
- Diseño completo en Figma con biblioteca de componentes y prototipo navegable (DEC-60): https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security
  - Onboarding, registro y acceso, Bóveda, Generador, Salud (Resumen | Progreso), Ajustes, 2FA, dispositivos, actividad, contraseña maestra y eliminar cuenta.
  - Estados: copiada, eliminar con «Deshacer», conflictos agrupados («Por resolver» → «Elegir versión»), 5 pantallas de carga y 2 sin conexión.
  - 4 puntos de inicio del prototipo: App completa, Bóveda con sesión, Sin conexión · Bóveda, Sin conexión · Dispositivos.

## Decisiones del 2026-10-15
- DEC-51 tema claro para la app · DEC-52 candado = bloquear ahora · DEC-53 2FA sin QR (se copia la clave) · DEC-54 estadísticas de la propia bóveda · DEC-55 historial local `health_snapshots` · DEC-56 navegación y confirmaciones · DEC-57 conflictos agrupados (sin historial de contraseñas) · DEC-58 carga y sin conexión · DEC-59 iconografía (dado para el generador) · DEC-60 Figma y prototipo.
- ZXing queda descartado (ya no hay QR).

## En curso
- Aprobación final del modelo de datos por el equipo.
- Ícono de la app: 4 propuestas de cofre con los colores de C en Figma (tablero «Ícono de la app · propuestas»). Falta elegir.
- Protección legal (PEND-12): lista de documentos en `docs/legal/README.md`; falta redactarlos y agregar la casilla en Crear cuenta y el grupo «Legal y ayuda» en Ajustes.

## Siguiente
1. Mover `ui/theme` a `core/designsystem/theme` con *Refactor → Move* en Android Studio y pasar los tokens de Carbón y miel a Compose.
2. Corregir DEC-37 en el proyecto: `allowBackup="false"`, R8 en release, `usesCleartextTraffic="false"`.
3. Elegir librería y parámetros de Argon2id (PEND-01).
4. Empezar la implementación por la Bóveda local (Room + cripto) siguiendo las pantallas SCR-10…SCR-17 del grafo.

## Pendientes abiertos del grafo
PEND-01 Argon2 · PEND-02 recuperación de contraseña maestra · PEND-03 política de contraseña · PEND-04 tiempos de tokens/autobloqueo · PEND-05 plazos de retención · PEND-06 ORM · PEND-08 HIBP desde la app · PEND-10 idioma del código
