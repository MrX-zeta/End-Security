"""Grafo de conocimiento del backend real (end-security-backend, rama develop, f2b5732).
Uso: python3 build_backend_graph.py salida.json
"""
import json, sys

N, E = [], []
def node(id, tipo, nombre, descripcion, **detalles):
    N.append({"id": id, "tipo": tipo, "nombre": nombre, "descripcion": descripcion,
              "estado": detalles.pop("estado", "implementado"), "detalles": detalles})
def edge(de, a, relacion):
    E.append({"de": de, "a": a, "relacion": relacion})

# ---------------------------------------------------------------- proyecto
node("BE-00", "proyecto", "end-security-backend",
     "API NestJS + PostgreSQL (Prisma) del gestor End Security, escrita por Diego Alberto Zárate Vázquez (DEC-61). "
     "Conocimiento cero: solo guarda bloques cifrados, sal, parámetros KDF, la pvk y un hash Argon2id del auth key.",
     repo="https://github.com/Diego-Zarate18/end-security-backend", rama="develop", commit="f2b5732",
     fecha_commit="2026-11-06", stack=["NestJS 11", "Prisma", "PostgreSQL", "argon2 (node)", "passport-jwt",
     "express-rate-limit", "helmet", "Resend", "otplib/qrcode"],
     despliegue="Railway (según .env.example y TRUST_PROXY); URL pública: pendiente de confirmar",
     prefijo="/api", archivos=133, lineas_ts=4926,
     sin_pruebas="No hay pruebas automatizadas (solo la colección de Postman End-Security-Backend.postman_collection.json).")

# ---------------------------------------------------------------- capa transversal
transversal = [
 ("BX-01", "Prefijo global /api", "app.setGlobalPrefix('api'). El contrato de la app usa /v1.", "src/main.ts"),
 ("BX-02", "Envoltura de respuestas", "TransformInterceptor envuelve TODO éxito en {success:true, data, timestamp}. Las respuestas 204 no llevan cuerpo.", "src/common/interceptors/transform.interceptor.ts"),
 ("BX-03", "Formato de errores", "HttpExceptionFilter responde {statusCode, error, message, details?, path, timestamp}. `error` es un nombre (Unauthorized, InvalidCode, InvalidSecondFactor, SecondFactorRequired, VaultSyncConflict, KeyVersionMismatch, VaultChanged, TooManyRequests, ConflictError…); `details` solo en conflictos de bóveda, rotación y 429 de reenvío ({retryAfterSeconds}). Sin `code` estable ni application/problem+json. Prisma P2002→409, P2025→404, sin filtrar columnas.", "src/common/filters/http-exception.filter.ts"),
 ("BX-04", "Base64 estándar con relleno", "BYTEA se expone como base64 estándar ('+/' y '='); IsBase64Bytes valida el formato estricto y el largo decodificado.", "src/common/utils/encoding.ts"),
 ("BX-05", "Validación global", "ValidationPipe whitelist + forbidNonWhitelisted + transform: un campo de más da 400.", "src/main.ts"),
 ("BX-06", "Límite de peticiones por IP", "express-rate-limit: 100/min global y 10/min en /api/auth (THROTTLE_*, AUTH_THROTTLE_LIMIT). 429 con cabeceras RateLimit-* y Retry-After. No hay límite por cuenta ni Redis.", "src/main.ts"),
 ("BX-07", "Guard JWT global", "APP_GUARD JwtAuthGuard: todo requiere Bearer salvo @Public. JwtStrategy comprueba además que la sesión siga activa (401 'La sesión ya no es válida.').", "src/app.module.ts"),
 ("BX-08", "Cabeceras y CORS", "helmet(); CORS desde CORS_ORIGIN (irrelevante para la app nativa). Cuerpo JSON hasta 3 MB.", "src/main.ts"),
 ("BX-09", "ClientInfo", "Decorador que toma IP (respeta TRUST_PROXY) y User-Agent para la auditoría.", "src/common/decorators/client-info.decorator.ts"),
]
for id, n, d, f in transversal:
    node(id, "transversal", n, d, archivo=f); edge("BE-00", id, "aplica")

# ---------------------------------------------------------------- módulos
modulos = [
 ("BMOD-AUTH", "auth", "Registro, verificación de correo, login, refresh rotativo, logout y sesiones.", ["AuthService", "SessionsService", "TokenService"]),
 ("BMOD-2FA", "two-factor", "TOTP (secreto cifrado con AES-256 y llave del servidor con id), anti-replay por last_used_step, 10 códigos de recuperación HMAC, bloqueo 5 fallos/15 min.", ["TwoFactorService", "TotpSecretService"]),
 ("BMOD-ACCOUNT", "account", "Cambio de contraseña maestra, eliminación de cuenta y ReauthService (authHash + segundo factor).", ["AccountService", "ReauthService"]),
 ("BMOD-USERS", "users", "Perfil (GET/PATCH /users/me) y acceso a la tabla users.", ["UsersService"]),
 ("BMOD-VAULT", "vault", "Sincronización por revisión con control optimista por versión; lote atómico; rotación de llave de bóveda.", ["VaultService"]),
 ("BMOD-AUDIT", "audit", "Bitácora encadenada con HMAC (AUDIT_HMAC_KEY), IP y user-agent en audit_log_pii; listado paginado y verificación de la cadena.", ["AuditService"]),
 ("BMOD-EMAIL", "email-verification", "Códigos de 6 dígitos: 15 min, 5 intentos, reenvío cada 60 s; mismo mensaje para incorrecto/vencido/agotado.", ["EmailCodeService"]),
 ("BMOD-MAIL", "mail", "Envío con Resend y plantillas; sin RESEND_API_KEY en desarrollo escribe en consola. EMAIL_FROM por defecto onboarding@resend.dev (falta dominio).", ["MailService"]),
]
for id, n, d, s in modulos:
    node(id, "modulo", n, d, servicios=s, carpeta=f"src/modules/{n}"); edge("BE-00", id, "contiene")

# ---------------------------------------------------------------- tablas
tablas = [
 ("BTBL-USERS", "users", "id (uuid generado por el servidor), email único, display_name, auth_hash (PHC Argon2id), kdf_salt/memory/iterations/parallelism, pvk_ciphertext/nonce, key_version, vault_revision, purged_through_revision, email_verified_at, deleted_at, anonymized_at. Sin columnas de consentimiento (marketing_opt_in)."),
 ("BTBL-SESSIONS", "sessions", "device_name, refresh_token_hash (único), previous_refresh_token_hash, expires_at, revoked_at, revoke_reason, last_used_at."),
 ("BTBL-EMAILV", "email_verifications", "purpose (solo verificación de registro), target_email, code_hash, attempts, expires_at, consumed_at."),
 ("BTBL-AUDIT", "audit_log", "event_type (catálogo AuditEvent), outcome, occurred_at, metadata JSON, prev_hash, hash (HMAC encadenado)."),
 ("BTBL-AUDITPII", "audit_log_pii", "ip (inet) y user_agent separados para poder borrarlos."),
 ("BTBL-VAULT", "vault_items", "id (uuid del cliente), version, revision, nonce, ciphertext, deleted_at (lápida sin contenido), format_version, updated_at."),
 ("BTBL-2FA", "two_factor", "status pending/active, secret_ciphertext/nonce, server_key_id, last_used_step, activated_at."),
 ("BTBL-RECOVERY", "recovery_codes", "code_hash (HMAC con pepper), used_at."),
]
for id, n, d in tablas:
    node(id, "tabla", n, d, archivo="prisma/schema.prisma"); edge("BE-00", id, "persiste_en")
for m, t in [("BMOD-USERS","BTBL-USERS"),("BMOD-AUTH","BTBL-SESSIONS"),("BMOD-EMAIL","BTBL-EMAILV"),("BMOD-AUDIT","BTBL-AUDIT"),
             ("BMOD-AUDIT","BTBL-AUDITPII"),("BMOD-VAULT","BTBL-VAULT"),("BMOD-2FA","BTBL-2FA"),("BMOD-2FA","BTBL-RECOVERY")]:
    edge(m, t, "escribe")

# ---------------------------------------------------------------- endpoints
# (id, método, ruta, público, módulo, entrada, salida, errores, equivale_a_EP_app)
eps = [
 ("BEP-01","GET","/api/health",True,"BE-00","—","{status}", "—", None),
 ("BEP-02","POST","/api/auth/prelogin",True,"BMOD-AUTH","{email}","{kdf:{salt,memoryKib,iterations,parallelism}}; correo inexistente: sal determinista y DEFAULT_KDF_PARAMS (p=4)","400","EP-01"),
 ("BEP-03","POST","/api/auth/register",True,"BMOD-AUTH","{email, displayName (obligatorio), authHash, kdf{salt,memoryKib≥19456,iterations≥2,parallelism}, pvk{ciphertext,nonce}, deviceName}","201 {requiresEmailVerification:true, challengeToken (JWT 30 min), verification{sentTo,codeSent,expiresAt,resendAvailableInSeconds}}; correo existente: igual pero señuelo + aviso al dueño","400","EP-02"),
 ("BEP-04","POST","/api/auth/email/verify/confirm",True,"BMOD-AUTH","{challengeToken, code}","200 AuthResponse {tokens{tokenType,accessToken,expiresIn,refreshToken}, user, keys{kdf,pvk,keyVersion}} o desafío 2FA","400 InvalidCode; 409 ya verificado; 401 token inválido","EP-03"),
 ("BEP-05","POST","/api/auth/email/verify/resend",True,"BMOD-AUTH","{challengeToken}","202 mismo objeto que el registro","429 TooManyRequests details.retryAfterSeconds; 409","EP-04"),
 ("BEP-06","POST","/api/auth/login",True,"BMOD-AUTH","{email, authHash, deviceName}","200 una de tres: AuthResponse | {requires2FA:true, challengeToken (5 min)} | {requiresEmailVerification:true, challengeToken, verification}","401 'Credenciales inválidas.' (mismo tiempo con correo inexistente); 429 por IP","EP-05"),
 ("BEP-07","POST","/api/auth/2fa/verify",True,"BMOD-2FA","{challengeToken, code? | recoveryCode? (5+5, alfabeto abcdefghjkmnpqrstuvwxyz23456789, guion opcional)}","200 AuthResponse","401 InvalidSecondFactor; 429 tras 5 fallos/15 min","EP-06, EP-07"),
 ("BEP-08","POST","/api/auth/refresh",True,"BMOD-AUTH","{refreshToken ('<uuid>.<43 base64url>')}","200 TokenPair (sin sessionId)","401 (reúso detectado revoca la sesión)","EP-08"),
 ("BEP-09","POST","/api/auth/logout",False,"BMOD-AUTH","—","204","401","EP-09"),
 ("BEP-10","GET","/api/auth/sessions",False,"BMOD-AUTH","—","[{id, deviceName, createdAt, lastUsedAt, expiresAt, current}]","401","EP-23"),
 ("BEP-11","DELETE","/api/auth/sessions/:id",False,"BMOD-AUTH","—","204","404 (no existe, cerrada o ajena)","EP-24"),
 ("BEP-12","POST","/api/auth/sessions/revoke-others",False,"BMOD-AUTH","—","200 {revoked}","401","EP-24 (lote)"),
 ("BEP-13","GET","/api/auth/2fa",False,"BMOD-2FA","—","{status: disabled|pending|active, activatedAt, recoveryCodesRemaining}","401","EP-13 (parcial)"),
 ("BEP-14","POST","/api/auth/2fa/setup",False,"BMOD-2FA","{authHash} (reautenticación)","200 {secret, otpauthUrl, qrCodeDataUrl}","403 contraseña; 409 ya activa","EP-19"),
 ("BEP-15","POST","/api/auth/2fa/activate",False,"BMOD-2FA","{code}","200 {recoveryCodes[10]}","401 InvalidSecondFactor; 400; 429","EP-20"),
 ("BEP-16","POST","/api/auth/2fa/disable",False,"BMOD-2FA","{authHash, code (TOTP o recuperación)}","200 TwoFactorStatus","403; 401 InvalidSecondFactor; 429","EP-21"),
 ("BEP-17","POST","/api/auth/2fa/recovery-codes",False,"BMOD-2FA","{authHash, code}","200 {recoveryCodes[10]}","403; 401; 429","EP-22"),
 ("BEP-18","POST","/api/auth/password",False,"BMOD-ACCOUNT","{currentAuthHash, newAuthHash, kdf, pvk, code?} (sin expectedKeyVersion)","200 {keyVersion, otherSessionsRevoked}","403 contraseña; 400 SecondFactorRequired; 401 InvalidSecondFactor","EP-17"),
 ("BEP-19","DELETE","/api/users/me",False,"BMOD-ACCOUNT","{authHash, code?} (cuerpo en DELETE)","204; marca deleted_at y anonymized_at en el acto","403; 400; 401","EP-18"),
 ("BEP-20","GET","/api/users/me",False,"BMOD-USERS","—","{id, email, displayName, emailVerified, twoFactorEnabled, createdAt} (sin keyVersion, kdf, pvk ni marketingOptIn)","401","EP-13"),
 ("BEP-21","PATCH","/api/users/me",False,"BMOD-USERS","{displayName} (obligatorio)","200 perfil","400","EP-14 (parcial)"),
 ("BEP-22","GET","/api/vault/sync?since=<revision>",False,"BMOD-VAULT","since (0 = todo)","{revision, purgedThroughRevision, keyVersion, fullResync, items[{id,version,revision,deleted,nonce?,ciphertext?,formatVersion,updatedAt}]}; fullResync si since=0, < purgado o > actual; sin paginación","401","EP-10, EP-26 (sustituto)"),
 ("BEP-23","POST","/api/vault/sync",False,"BMOD-VAULT","{items[1..50]: {id, baseVersion, deleted?, nonce?, ciphertext? (17..32768 B), formatVersion?}}","200 {revision, applied[{id,version,revision}]}","409 VaultSyncConflict details.conflicts[{id, reason: version_mismatch|not_found|id_unavailable, currentVersion}] (todo o nada)","EP-11, EP-12"),
 ("BEP-24","POST","/api/vault/rotate-key",False,"BMOD-VAULT","{authHash, code?, keyVersion, pvk, items[] (todos recifrados)}","200 {keyVersion, revision, itemsRotated, otherSessionsRevoked}","409 KeyVersionMismatch | VaultChanged","— (la app no rota la vault_key)"),
 ("BEP-25","GET","/api/audit?limit&before",False,"BMOD-AUDIT","limit 1..100, before (id)","{items[{id, eventType, outcome, occurredAt, metadata, deviceName, ip, userAgent}], nextCursor}","401","EP-25"),
 ("BEP-26","GET","/api/audit/verify",False,"BMOD-AUDIT","—","{valid, checkedEntries, firstInvalidId?, reason?}","401","— (nuevo; útil para la demo)"),
]
for id, m, r, pub, mod, i, o, err, eq in eps:
    node(id, "endpoint", f"{m} {r}", f"Entrada: {i}. Salida: {o}. Errores: {err}.",
         metodo=m, ruta=r, publico=pub, equivale_a_app=eq)
    edge(mod, id, "expone")

# ---------------------------------------------------------------- catálogo de eventos de auditoría
eventos = ["auth.register","auth.login","auth.2fa_challenge","auth.refresh_reuse","auth.logout","auth.session_revoke",
           "auth.sessions_revoke_others","vault.sync_push","vault.sync_conflict","vault.key_rotation","email.verify_request",
           "email.verify_confirm","2fa.setup","2fa.activate","2fa.verify","2fa.disable","2fa.recovery_regenerate",
           "account.profile_update","account.password_change","account.delete"]
node("BCAT-AUDIT","catalogo","AuditEvent (20 eventos)",
     "Catálogo cerrado; outcome success|failure; metadata.reason en fallos (invalid_credentials, email_not_verified, email_taken, invalid_second_factor…). Distinto de los 18 eventType del contrato de la app: hace falta una tabla de equivalencias en la app.",
     eventos=eventos, archivo="src/modules/audit/audit.constants.ts")
edge("BMOD-AUDIT","BCAT-AUDIT","define")

# ---------------------------------------------------------------- seguridad que ya cumple
seguridad = [
 ("BSEC-01","Hash del auth key","Argon2id del servidor (19 MiB, t=2, p=1) sobre el auth key; hash señuelo para igualar tiempos con correos inexistentes."),
 ("BSEC-02","Registro sin enumeración","Correo existente → mismo 201 con desafío señuelo que nunca se confirma y aviso al dueño (máx. 1/h)."),
 ("BSEC-03","Refresh rotativo","Token '<uuid>.<secreto>'; guarda hash actual y anterior; reúso del anterior revoca la sesión (auth.refresh_reuse)."),
 ("BSEC-04","Sesión viva en cada petición","JwtStrategy consulta que la sesión no esté revocada ni vencida."),
 ("BSEC-05","TOTP","Secreto cifrado AES-256 con llaves rotables por id (TOTP_ENCRYPTION_KEYS), anti-replay por paso, 5 fallos/15 min."),
 ("BSEC-06","Códigos de recuperación","10 códigos de 10 caracteres sin ambiguos, HMAC con pepper, un solo uso."),
 ("BSEC-07","Bitácora encadenada","HMAC encadenado (AUDIT_HMAC_KEY, versión de formato 1) y verificación por usuario; PII aparte."),
 ("BSEC-08","Bóveda opaca","Solo ciphertext/nonce; lápidas sin contenido; control optimista por versión; todo o nada por lote."),
 ("BSEC-09","Reautenticación","Operaciones sensibles piden authHash (+ segundo factor si hay 2FA); 403 sin cerrar la sesión."),
 ("BSEC-10","Configuración validada","env.validation exige secretos de ≥ 32 caracteres, distintos entre sí, y RESEND_API_KEY en producción."),
]
for id, n, d in seguridad:
    node(id, "seguridad", n, d); edge("BE-00", id, "cumple")

# ---------------------------------------------------------------- brechas con la app (contrato 1.4.0)
# (id, nombre, descripcion, dueño, severidad, afecta)
brechas = [
 ("BR-01","Prefijo /api en lugar de /v1","La app usa baseUrl …/v1.","app (adaptador)","baja",["BX-01"]),
 ("BR-02","Envoltura {success,data,timestamp}","Hay que desenvolver `data` en cada éxito.","app (adaptador)","baja",["BX-02"]),
 ("BR-03","Errores sin `code` del contrato","La app decide por `code`; aquí hay que mapear por (status, error, details). Tabla de equivalencias en el adaptador.","app (adaptador)","media",["BX-03"]),
 ("BR-04","Base64 estándar","La app usa base64url sin relleno: convertir en la frontera (DTO del transporte real).","app (adaptador)","baja",["BX-04"]),
 ("BR-05","Nombres de campos","authHash = auth_key; pvk{ciphertext,nonce} = protectedVaultKey; kdf.salt dentro de kdf; deviceName obligatorio en login/registro.","app (adaptador)","baja",["BEP-03","BEP-06"]),
 ("BR-06","El servidor genera el userId","CRÍTICA: la app envuelve la vault_key con AAD end-security/vault-key|userId|keyVersion ANTES de registrarse (DEC-05) y cifra cada elemento con AAD id|userId|formatVersion. Si el id lo inventa el servidor, la pvk no abre. Arreglo mínimo en el backend: RegisterDto.userId (@IsUUID) y users.create({id}); con id repetido, el mismo señuelo.","backend","crítica",["BEP-03","BTBL-USERS"]),
 ("BR-07","displayName obligatorio en el registro","La app no pide nombre. Opción app: mandar la parte local del correo; opción backend: hacerlo opcional.","app o backend","baja",["BEP-03"]),
 ("BR-08","Verificación de correo con challengeToken","El contrato usa {email, code}. La app guarda el challengeToken en memoria (PendingAuth); si se cierra, un nuevo login devuelve otro desafío.","app (adaptador)","media",["BEP-04","BEP-05"]),
 ("BR-09","Login con tres respuestas 200","Mapear: tokens → sesión; requires2FA → pantalla 2FA con challengeToken; requiresEmailVerification → pantalla de código.","app (adaptador)","media",["BEP-06"]),
 ("BR-10","Formato de códigos de recuperación","Backend: 10 caracteres (5-5) del alfabeto abcdefghjkmnpqrstuvwxyz23456789, minúsculas. App: XXXX-XXXX Crockford. Cambiar el campo y su validación en la app.","app","media",["BEP-07","BEP-15","BEP-17"]),
 ("BR-11","Sesión revocada = 401 genérico","Adaptador: 401 → refresh una vez; si refresh da 401 → session_revoked (signOutLocally).","app (adaptador)","media",["BX-07","BEP-08"]),
 ("BR-12","Descarga de la bóveda","GET /vault/sync?since: sin paginación ni 410; fullResync=true sustituye a cursor_expired.","app (adaptador)","media",["BEP-22"]),
 ("BR-13","Subida por lote atómico (D-4)","Resuelto sin tocar el backend: la app manda lotes de 1 elemento, así cada conflicto es por elemento como espera DEC-57. 409 VaultSyncConflict → conflict.","app (adaptador)","media",["BEP-23"]),
 ("BR-14","Sin GET /vault/items/{id} (EP-26)","Para «Elegir versión» la app obtiene la versión del servidor con GET /vault/sync?since=<cursor> antes de mostrar el conflicto.","app","media",["BEP-22"]),
 ("BR-15","/users/me sin llaves ni keyVersion","La regla de la tarea 19 (key_version mayor) toma keyVersion de GET /vault/sync.","app (adaptador)","baja",["BEP-20"]),
 ("BR-16","Sin marketingOptIn","Contrato 1.4.0 (DEC-64): falta columna y campo en register / users/me / PATCH. Decidir: backend lo agrega, o la app oculta la casilla y el interruptor en prod y se ajusta el aviso.","decisión del equipo","media",["BEP-03","BEP-20","BEP-21","BTBL-USERS"]),
 ("BR-17","Sin cambio de correo (DEC-63)","No existen EP-15/16 ni sus límites. Decidir: backend lo agrega, o la app oculta la flecha de la tarjeta en prod.","decisión del equipo","media",["BMOD-EMAIL"]),
 ("BR-18","Cambio de contraseña sin expectedKeyVersion","Sin 409 si otro teléfono cambió antes. La app compara keyVersion antes (GET /vault/sync). Sugerencia menor al backend.","app (+ sugerencia backend)","baja",["BEP-18"]),
 ("BR-19","Eliminación anonimiza al instante","El aviso de privacidad dice «a los 30 días anonimizamos lo restante». Corregir el aviso (más simple) o el backend.","legal (aviso)","media",["BEP-19"]),
 ("BR-20","Setup 2FA pide reautenticación","La app abre la activación sin contraseña: agregar ReauthSheet antes del paso 1; usar otpauthUrl del servidor.","app","media",["BEP-14"]),
 ("BR-21","Catálogo de actividad distinto","20 eventos con otros nombres (BCAT-AUDIT) y además IP y user-agent. Nueva tabla de títulos e íconos en la app; no mostrar IP.","app","media",["BEP-25","BCAT-AUDIT"]),
 ("BR-22","Límites solo por IP","Sin límite por cuenta en login (D-6) ni límites de DEC-63. La app interpreta 429 con Retry-After o details.retryAfterSeconds.","backend (sugerencia) + app","media",["BX-06"]),
 ("BR-23","Parámetros KDF señuelo distinguibles","prelogin de correos inexistentes devuelve parallelism 4; las cuentas creadas por la app usan 1 → revela qué correos existen. Arreglo de una línea: DEFAULT_KDF_PARAMS.parallelism = 1. Además el piso del servidor (19 456 KiB, t ≥ 2) es más bajo que DEC-05 (65 536, t ≥ 3).","backend","alta",["BEP-02","BEP-03"]),
 ("BR-24","Dominio de correo pendiente","EMAIL_FROM onboarding@resend.dev solo entrega al dueño de la cuenta de Resend: sin dominio verificado nadie más recibe el código de registro.","backend (Diego, ya identificado)","alta",["BMOD-MAIL"]),
 ("BR-25","Syslog RFC 5424 (DEC-62) no implementado","Solo el Logger de Nest. Lo pidió el profesor.","backend","alta",["BE-00"]),
 ("BR-26","URL pública y pines SPKI (E2)","Falta la URL HTTPS de producción y los dos hashes SPKI para network_security_config.xml.","backend + app","alta",["BE-00"]),
 ("BR-27","Sin pruebas automatizadas","Solo Postman. La app probará contra el servidor real con una suite de integración.","backend (sugerencia)","baja",["BE-00"]),
]
for id, n, d, owner, sev, afecta in brechas:
    node(id, "brecha", n, d, estado="abierta", dueno=owner, severidad=sev)
    for a in afecta: edge(id, a, "afecta")

meta = {
 "nombre": "Grafo de conocimiento — backend real de End Security",
 "version": "1.0.0", "actualizado": "2026-11-18",
 "fuente": "end-security-backend@develop f2b5732 (leído completo por la sesión de diseño)",
 "proposito": "Mapa del backend real para integrarlo con la app: módulos, endpoints, tablas, seguridad y brechas contra el contrato docs/api 1.4.0.",
 "como_usar_para_agentes": [
   "Cada BEP-xx dice a qué EP-xx del contrato de la app equivale (detalles.equivale_a_app).",
   "Las brechas BR-xx dicen quién las resuelve (detalles.dueno). Claude Code solo toca las de la app; nunca escribe código del backend.",
   "El grafo de la app (ai/context/knowledge-graph.json) sigue siendo la fuente de las decisiones (DEC-xx).",
 ],
 "tipos_de_nodo": sorted({n["tipo"] for n in N}),
 "estadisticas": {"nodos": len(N), "aristas": len(E),
   "por_tipo": {t: sum(1 for n in N if n["tipo"] == t) for t in sorted({n["tipo"] for n in N})}},
}
ids = {n["id"] for n in N}
assert all(e["de"] in ids and e["a"] in ids for e in E), "arista con nodo inexistente"
json.dump({"meta": meta, "nodos": N, "aristas": E}, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("OK", meta["estadisticas"])
