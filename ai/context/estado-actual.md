# Estado actual — End Security

> Claude lo lee al empezar cada sesión y lo actualiza con `/registrar-sesion`.
> Última actualización: 2026-10-19 (contrato de la API)

## Hecho
- Análisis adversario de la propuesta; huecos documentados y resueltos (GAP-01…GAP-37).
- Alcance cerrado: modo híbrido, multi-dispositivo, 2FA TOTP + códigos de recuperación, Resend de alcance mínimo, sin administrador, lista plana.
- Grafo de conocimiento v1.5.4 (`ai/context/knowledge-graph.json`): 61 decisiones, 47 operaciones, 46 pantallas con su navegación y 25 endpoints.
- Arquitectura MVVM (`docs/arquitectura.md`) y carpetas creadas en `app/`.
- Modelo de datos v1.1: `docs/bd/schema.sql` (probado en PostgreSQL 16; 36 pruebas + concurrencia), Room (ahora con `health_snapshots`), DataStore y Redis (`docs/bd/modelo-datos.md`). Diagrama ER en FigJam.
- Configuración de Claude Code: `CLAUDE.md`, `.claude/settings.json`, reglas por ruta, subagente `security-reviewer`, skill `/registrar-sesion`.
- Identidad visual **Carbón y miel** + Montserrat (DEC-50): onboarding oscuro, resto de la app claro (DEC-51).
- Diseño completo en Figma con biblioteca de componentes y prototipo navegable (DEC-60): https://www.figma.com/design/K8GenUmR7GcNZAtJRjagER/End-security
  - Onboarding, registro y acceso, Bóveda, Generador, Salud (Resumen | Progreso), Ajustes, 2FA, dispositivos, actividad, contraseña maestra y eliminar cuenta.
  - Estados: copiada, eliminar con «Deshacer», conflictos agrupados («Por resolver» → «Elegir versión»), 5 pantallas de carga y 2 sin conexión.
  - 4 puntos de inicio del prototipo: App completa, Bóveda con sesión, Sin conexión · Bóveda, Sin conexión · Dispositivos.
- Implementación (Claude Code, rama `development`): tareas 01 a 10.1 aprobadas (registro, acceso, 2FA y desbloqueo reales con backend falso; splash). Tema Carbón y miel en Compose, componentes base, DEC-37 aplicado, onboarding SCR-01…03 con DataStore Iniciar sesión SCR-04 Crear cuenta SCR-05 (medidor zxcvbn4j, NFKC) Verificar correo SCR-06, 2FA SCR-07, código de respaldo SCR-08, Desbloquear SCR-09 y Bóveda · cargando SCR-39 con barra inferior, todo solo interfaz, con `SecureScreen` y `ExcludeFromAutofill` en `core/security`.
- Relevo entre sesiones por `ai/handoff/` (`siguiente.md` ↔ `reporte.md`).
- **Contrato de la API v1.1.0** (`docs/api/openapi.yaml` + `docs/api/README.md`, DEC-61): 25 endpoints. El backend lo hace otra persona del equipo; la app usa implementaciones falsas del contrato hasta unirlos.

## Decisiones del 2026-10-15
- DEC-51 tema claro para la app · DEC-52 candado = bloquear ahora · DEC-53 2FA sin QR (se copia la clave) · DEC-54 estadísticas de la propia bóveda · DEC-55 historial local `health_snapshots` · DEC-56 navegación y confirmaciones · DEC-57 conflictos agrupados (sin historial de contraseñas) · DEC-58 carga y sin conexión · DEC-59 iconografía (dado para el generador) · DEC-60 Figma y prototipo.
- ZXing queda descartado (ya no hay QR).

## En curso
- Aprobación final del modelo de datos por el equipo.
- `core/crypto`: Argon2id + HKDF (Bouncy Castle 1.86) con vectores de RFC 9106/5869 y vector propio; AES-256-GCM (JCA) con vectores de NIST, `SecureBytes` y envoltura de la `vault_key`; bloqueo local de Desbloquear en DataStore. Ícono instalado.
- Base local: Room (solo bloques cifrados), `VaultSession` + `AutoLocker` (5 min por defecto), `LockGuard` e `ItemCipher` (JSON v1).
- Alcance del backend en `docs/backend/README.md` (B1…B15), con syslog RFC 5424 de 5 niveles (DEC-62).
- Tarea 11 en `ai/handoff/siguiente.md`: cabos sueltos de la tarea 10 y Bóveda real (lista, detalle, nueva/editar, eliminar con Deshacer).
- Protección legal (PEND-12): lista de documentos en `docs/legal/README.md`; falta redactarlos y agregar la casilla en Crear cuenta y el grupo «Legal y ayuda» en Ajustes.

## Siguiente
1. Tarea 12: sincronización (OP-33/34) y conflictos (SCR-18…21). Tarea 13: Generador y Salud.
2. Backend (compañero): implementar `docs/api/` y la migración 002 (piso de KDF). Después, cambiar las implementaciones falsas por Retrofit.

## Pendientes abiertos del grafo
PEND-02 recuperación de contraseña maestra · PEND-04 tiempos de tokens/autobloqueo · PEND-05 plazos de retención · PEND-06 ORM · PEND-08 HIBP desde la app · PEND-10 idioma del código
