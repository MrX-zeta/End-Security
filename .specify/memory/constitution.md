# Constitución de End Security

Esta constitución no crea reglas: reúne las que ya existen en `ai/context/knowledge-graph.json`
(invariantes `INV-*` y decisiones `DEC-*`), `CLAUDE.md` (reglas no negociables, citadas como
`CLAUDE#n`) y `.claude/rules/` (`crypto.md`, `base-de-datos.md`). Si algo de aquí choca con el
grafo, **gana el grafo** y esta constitución se corrige.

## Core Principles

### I. Conocimiento cero
- El servidor nunca recibe ni guarda la contraseña maestra, `master_key`, `enc_key`, la `vault_key`
  en claro ni ningún campo de un elemento en claro (INV-01, CLAUDE#1).
- La contraseña maestra nunca se envía; se envía `auth_key` derivada (DEC-05, CLAUDE#2).
- Ningún dato sensible en claro en el disco del teléfono: Room guarda solo bloques cifrados
  (INV-02).
- Los resultados y el historial de salud se calculan y guardan solo en el teléfono, cifrados con la
  `vault_key` (INV-13, INV-29).
- A HIBP solo van los primeros 5 caracteres hexadecimales del SHA-1, con `Add-Padding` (INV-14).

Razón: el servidor comprometido es parte del modelo de amenazas; lo que no recibe, no lo puede
filtrar.

### II. Criptografía reconocida y probada (NO NEGOCIABLE)
- AES-256-GCM, nonce aleatorio de 12 B por cifrado y AAD = `id|user_id|format_version`
  (INV-03, DEC-29, CLAUDE#3).
- Aleatoriedad solo con `SecureRandom` (Android) o `crypto.randomBytes` (Node); prohibidos
  `Random`, `kotlin.random` y `Math.random` (INV-04, CLAUDE#4).
- Sin primitivas propias; todo código cripto lleva vectores oficiales, ida y vuelta, alteración y
  unicidad de nonces, y revisión humana (INV-05, `rules/crypto.md`).
- El código criptográfico de la app vive solo en `core/crypto` (CLAUDE, mapa de la app).

### III. Secretos en memoria, nunca en logs
- Llaves solo en memoria mientras el almacén está desbloqueado; se sobrescriben al bloquear; nunca
  en `SavedStateHandle` ni `rememberSaveable` (INV-07, CLAUDE#6).
- Fuera de las primitivas, los secretos van en `SecureBytes` (DEC-27, `rules/crypto.md`).
- Nunca registrar secretos, contraseñas, llaves, tokens ni bloques; las clases con secretos
  sobrescriben `toString()` (INV-06, CLAUDE#5). El syslog del servidor tampoco lleva correos,
  códigos, sales ni `authHash` (DEC-62).
- Los códigos cortos se guardan como HMAC con pepper (INV-26).

### IV. Integridad de la bóveda y sincronización
- Borrar un elemento es una lápida hasta la purga (INV-08, CLAUDE#8).
- Toda escritura de elemento lleva la versión esperada; si no coincide, 409 (INV-09, CLAUDE#8).
- `sync_state` ∈ `synced | pending | conflict`; solo pasa a `synced` si `local_seq` no cambió
  durante la subida (INV-27).
- Los conflictos se resuelven con la versión del servidor y la elección del usuario, no por la hora
  (DEC-57).
- Las renovaciones de token van una a la vez (INV-25, CLAUDE#9).

### V. Cuenta y acceso
- Cambiar correo o contraseña maestra, desactivar 2FA, regenerar códigos y eliminar la cuenta
  requieren reautenticación (INV-10).
- Prelogin y registro nunca revelan si un correo existe (INV-12).
- El correo nunca restablece el 2FA ni la contraseña maestra (INV-16).
- La clave TOTP y los códigos de respaldo se muestran una sola vez y con FLAG_SECURE; la app nunca
  genera QR del secreto (INV-31).

### VI. Contrato y capas
- El límite entre app y servidor es el contrato `docs/api/`; la app habla con el servidor solo por
  `AuthApi`, `VaultApi`, `AccountApi` y `SessionApi` (DEC-61).
- Las diferencias con el backend real se traducen en un adaptador de la variante `prod`; la variante
  `demo` usa el transporte falso (DEC-65).
- Bytes en Base64url sin relleno, UUID canónicos y errores por `code`; nunca el `title` del error
  como texto de la UI (DEC-61).
- Dependencias `ui → domain ← data`; `ui` nunca usa Retrofit, Room ni crypto; `domain` es Kotlin
  puro. Casos de uso solo con lógica real (CLAUDE, mapa y CLAUDE#10).

### VII. Si no está decidido, se pregunta
- Si una decisión no está en el grafo, o está `propuesta`/`pendiente`, se pregunta antes de
  inventarla (INV-19, CLAUDE «Fuentes de verdad»).

## Restricciones de seguridad y plataforma

- Pantallas con secretos: FLAG_SECURE; portapapeles marcado sensible y limpiado solo
  (INV-15, CLAUDE#7).
- Tráfico con la API propia: HTTPS con pinning SPKI y pin de respaldo; texto plano solo en `debug`
  hacia el servidor local (INV-18, DEC-65).
- Servidor: PostgreSQL y Redis nunca expuestos a internet (INV-17); `audit_log` nunca recibe
  UPDATE, DELETE ni TRUNCATE (INV-11); orden de candados fijo (INV-20); Argon2 fuera de la
  transacción (INV-21); auditoría al final de la transacción y correos/Redis después del COMMIT
  (INV-22); rol heredero de `endsec_api`, nunca superusuario (INV-24).
- UI: solo tokens de Carbón y miel y Montserrat (INV-28); acciones irreversibles con confirmación en
  hoja inferior (INV-30); esqueletos solo donde se espera al servidor (INV-32); textos en
  `strings.xml` (CLAUDE «Estilo»).
- Stack: Kotlin 2.2, Compose + Material 3, AGP 9.2, minSdk 28, targetSdk 36, Hilt, Room, DataStore,
  Retrofit/OkHttp, WorkManager. Backend NestJS + PostgreSQL (CLAUDE «Stack»).

## Flujo de trabajo y calidad

- Las tareas llegan por `ai/handoff/siguiente.md` y se cierran con `reporte.md` (CLAUDE «Relevo»).
- Ramas de la integración: app `integracion/backend` desde `development`; backend
  `integracion/app` desde `develop`; nunca commits en `develop` ni `main`; PR que revisa el equipo
  (DEC-61, DEC-65).
- Código delicado (crypto, auth, sincronización, SQL): plan breve antes del código y
  «REQUIERE REVISIÓN HUMANA», con el subagente `security-reviewer` (CLAUDE «Cómo trabajar»).
- Al terminar: compilar, correr pruebas y lint, y reportar el resultado real (CLAUDE «Cómo
  trabajar»).
- Spec Kit para funcionalidades grandes; ponytail en `full` y `/ponytail-review` antes de cada PR
  (DEC-66).
- Identificadores en inglés; comentarios, documentación y textos de la UI en español (CLAUDE
  «Estilo»).

## Governance

- Fuente de verdad: `ai/context/knowledge-graph.json`. Esta constitución lo resume y se subordina a
  él; `CLAUDE.md` y `.claude/rules/` la complementan.
- Enmiendas: primero cambia el grafo (nodo, relaciones y versión en `meta`) con aprobación del
  equipo; luego se actualiza esta constitución citando el id nuevo.
- Versionado: MAJOR si se quita o redefine un principio; MINOR si se agrega uno o se amplía;
  PATCH para redacción.
- Cumplimiento: cada plan de Spec Kit revisa su «Constitution Check» contra estos principios y cada
  PR pasa por `/ponytail-review`; lo sensible, además, por revisión humana.

**Version**: 1.0.0 | **Ratified**: 2026-11-18 | **Last Amended**: 2026-11-18
