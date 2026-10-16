---
paths:
  - "docs/bd/**"
  - "app/src/**/core/database/**"
  - "app/src/**/core/sync/**"
  - "backend/**/*.sql"
  - "backend/src/**/*.repository.ts"
  - "backend/src/**/vault/**"
  - "backend/src/**/auth/**"
---

# Reglas de base de datos (End Security)

Referencias: `docs/bd/modelo-datos.md` (§6 transacciones, §8 Room) y `docs/bd/schema.sql`. En el grafo: DEC-39…DEC-47, TX-01…TX-15, INV-20…INV-27.

## Servidor (PostgreSQL)
- Ninguna columna puede contener nombre de cuenta, usuario, contraseña, URL ni notas en claro.
- Crear/editar/borrar elementos: **solo** `app.vault_write_item()`. Rotar refresh tokens: **solo** `app.session_rotate()`.
- `READ COMMITTED` + `FOR UPDATE`. Orden de candados: users → vault_items → sessions → two_factor → recovery_codes → email_verifications → audit_log.
- Argon2 y cualquier operación lenta van **fuera** de la transacción.
- El INSERT en `audit_log` va **al final** de la transacción; nunca envíes `id`, `prev_hash` ni `hash` (los pone el trigger).
- IP y user-agent van en `audit_log_pii`, nunca en `metadata`.
- Correos (Resend) y escrituras en Redis van **después** del COMMIT.
- La API usa un rol heredero de `endsec_api`; los jobs, `endsec_jobs`. Nunca superusuario.
- Cambios al esquema = nueva migración + actualizar `pruebas_esquema.sql` + correr las pruebas.

## Teléfono (Room)
- Tablas: `account` (una fila), `vault_items` (espejo), `breach_checks` (caché HIBP cifrada).
- `sync_state` ∈ `synced | pending | conflict` como enum de Kotlin con TypeConverter.
- Al subir: marcar `synced` solo si `local_seq` no cambió durante la subida.
- Conflictos: el `SyncWorker` solo marca `conflict`; `ConflictResolver` los resuelve al desbloquear (necesita la `vault_key`).
- Respuesta 410 = descarga completa y reconciliación.
- Las escrituras que tocan varias filas van en `@Transaction`.
