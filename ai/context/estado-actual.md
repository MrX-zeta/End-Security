# Estado actual — End Security

> Claude lo lee al empezar cada sesión y lo actualiza con `/registrar-sesion`.
> Última actualización: 2026-10-14

## Hecho
- Análisis adversario de la propuesta; huecos documentados y resueltos (GAP-01…GAP-37).
- Alcance cerrado: modo híbrido, multi-dispositivo, 2FA TOTP + códigos de recuperación, Resend de alcance mínimo, sin administrador, lista plana.
- Grafo de conocimiento v1.1 (`ai/context/knowledge-graph.json`).
- Arquitectura MVVM (`docs/arquitectura.md`) y carpetas creadas en `app/`.
- Modelo de datos: `docs/bd/schema.sql` (probado en PostgreSQL 16; 36 pruebas + concurrencia), Room, DataStore y Redis (`docs/bd/modelo-datos.md`). Diagrama ER en FigJam.
- Configuración de Claude Code: `CLAUDE.md`, `.claude/settings.json`, reglas por ruta, subagente `security-reviewer`, skill `/registrar-sesion`.

## En curso
- Aprobación final del modelo de datos por el equipo.

## Siguiente
1. Diseño de vistas (pantallas y flujo de navegación). **Esperar indicación del equipo.**
2. Mover `ui/theme` a `core/designsystem/theme` con *Refactor → Move* en Android Studio.
3. Corregir DEC-37 en el proyecto: `allowBackup="false"`, R8 en release, `usesCleartextTraffic="false"`.
4. Elegir librería y parámetros de Argon2id (PEND-01).

## Pendientes abiertos del grafo
PEND-01 Argon2 · PEND-02 recuperación de contraseña maestra · PEND-03 política de contraseña · PEND-04 tiempos de tokens/autobloqueo · PEND-05 plazos de retención · PEND-06 ORM · PEND-08 HIBP desde la app · PEND-10 idioma del código
