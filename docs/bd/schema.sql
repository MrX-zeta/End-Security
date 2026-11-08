-- =====================================================================
--  End Security · Esquema PostgreSQL v1
--  Requiere PostgreSQL 16 o superior (gen_random_uuid() y sha256() nativos).
--  Ejecutar como el rol dueño de la migración (no como superusuario de la app).
--
--  Principio rector: el servidor solo guarda datos que NO puede descifrar.
--  Ninguna columna contiene contraseñas, nombres de cuentas, URLs ni notas.
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 0. Roles de grupo (sin login). Los usuarios con login se crean en el
--    despliegue:  create role endsec_api_login login password '...' in role endsec_api;
-- ---------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'endsec_api') then
    create role endsec_api nologin;   -- la API NestJS
  end if;
  if not exists (select 1 from pg_roles where rolname = 'endsec_jobs') then
    create role endsec_jobs nologin;  -- procesos programados (purgas, anonimización)
  end if;
end $$;

create schema if not exists app;
revoke all on schema app from public;

-- ---------------------------------------------------------------------
-- 1. Utilidades
-- ---------------------------------------------------------------------
create or replace function app.set_updated_at()
returns trigger language plpgsql
set search_path = app, pg_temp
as $$
begin
  new.updated_at := now();
  return new;
end $$;

-- =====================================================================
-- 2. USERS
--    Una fila por cuenta. Nunca se borra físicamente: al eliminar la cuenta
--    se da de baja (deleted_at) y, tras el periodo de gracia, se anonimiza
--    (anonymized_at). Así la bitácora encadenada nunca pierde su referencia.
-- =====================================================================
create table app.users (
  id                       uuid        primary key default gen_random_uuid(),
  email                    text,
  email_verified_at        timestamptz,
  display_name             text,
  -- Hash PHC de Argon2id calculado en el SERVIDOR sobre auth_key (que ya
  -- llega derivada del cliente). Incluye su propia sal y parámetros.
  auth_hash                text,
  -- Parámetros con los que el CLIENTE deriva master_key. No son secretos.
  kdf_salt                 bytea,
  kdf_memory_kib           integer,
  kdf_iterations           integer,
  kdf_parallelism          smallint,
  -- vault_key envuelta con enc_key (AES-256-GCM): 32 B de llave + 16 B de etiqueta.
  pvk_nonce                bytea,
  pvk_ciphertext           bytea,
  -- Sube cada vez que cambia la contraseña maestra; los dispositivos lo
  -- comparan para saber que su copia local de llaves quedó obsoleta.
  key_version              integer     not null default 1,
  -- Contador de cambios del almacén (cursor de sincronización).
  vault_revision           bigint      not null default 0,
  -- Revisión más alta de lápidas ya purgadas. Si el cursor de un cliente es
  -- menor, debe hacer descarga completa (410 Gone).
  purged_through_revision  bigint      not null default 0,
  created_at               timestamptz not null default now(),
  updated_at               timestamptz not null default now(),
  deleted_at               timestamptz,
  anonymized_at            timestamptz,

  constraint users_email_format_chk check (
    email is null or (
      email = lower(email)
      and char_length(email) <= 254
      and email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')),
  constraint users_display_name_chk check (
    display_name is null or char_length(btrim(display_name)) between 1 and 50),
  constraint users_auth_hash_chk check (
    auth_hash is null or auth_hash like '$argon2id$%'),
  constraint users_kdf_salt_chk check (
    kdf_salt is null or octet_length(kdf_salt) = 16),
  -- Mínimos de OWASP para Argon2id (19 MiB, t=2, p=1).
  constraint users_kdf_params_chk check (
    kdf_memory_kib is null or (
      kdf_memory_kib  between 19456 and 1048576
      and kdf_iterations  between 2 and 20
      and kdf_parallelism between 1 and 8)),
  constraint users_pvk_chk check (
    pvk_nonce is null or (octet_length(pvk_nonce) = 12 and octet_length(pvk_ciphertext) = 48)),
  constraint users_key_version_chk check (key_version >= 1),
  constraint users_revisions_chk check (
    vault_revision >= 0 and purged_through_revision between 0 and vault_revision),
  constraint users_lifecycle_chk check (
    anonymized_at is null or (deleted_at is not null and anonymized_at >= deleted_at)),
  -- Cuenta activa o en periodo de gracia: todos los datos presentes.
  constraint users_not_anonymized_complete_chk check (
    anonymized_at is not null or (
      email is not null and auth_hash is not null and kdf_salt is not null
      and kdf_memory_kib is not null and kdf_iterations is not null and kdf_parallelism is not null
      and pvk_nonce is not null and pvk_ciphertext is not null)),
  -- Cuenta anonimizada: no queda ningún dato personal ni material de llaves.
  constraint users_anonymized_empty_chk check (
    anonymized_at is null or (
      email is null and email_verified_at is null and display_name is null and auth_hash is null
      and kdf_salt is null and kdf_memory_kib is null and kdf_iterations is null
      and kdf_parallelism is null and pvk_nonce is null and pvk_ciphertext is null))
);

-- Un correo solo puede pertenecer a una cuenta no eliminada.
create unique index users_email_active_uk on app.users (email) where deleted_at is null;
-- Para el proceso de anonimización.
create index users_pending_anonymization_idx on app.users (deleted_at)
  where deleted_at is not null and anonymized_at is null;

create trigger users_set_updated_at before update on app.users
  for each row execute function app.set_updated_at();

-- =====================================================================
-- 3. VAULT_ITEMS
--    Un bloque cifrado por elemento. El id lo genera el CLIENTE (UUID) para
--    poder crear sin conexión.
--    version  = cuántas veces cambió ESTE elemento (concurrencia optimista).
--    revision = posición en la historia de cambios del usuario (cursor).
-- =====================================================================
create table app.vault_items (
  id              uuid        primary key,
  user_id         uuid        not null references app.users (id) on delete cascade,
  version         integer     not null,
  revision        bigint      not null,
  format_version  smallint    not null default 1,
  nonce           bytea,
  ciphertext      bytea,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz,

  constraint vault_items_version_chk  check (version >= 1),
  constraint vault_items_revision_chk check (revision >= 1),
  constraint vault_items_format_chk   check (format_version >= 1),
  -- Elemento vivo: tiene contenido. Lápida: no tiene contenido.
  constraint vault_items_tombstone_chk check (
    (deleted_at is null     and nonce is not null and ciphertext is not null) or
    (deleted_at is not null and nonce is null     and ciphertext is null)),
  constraint vault_items_nonce_chk check (nonce is null or octet_length(nonce) = 12),
  -- Mínimo: etiqueta GCM (16 B) + 1 B. Máximo 32 KiB para evitar abuso.
  constraint vault_items_ciphertext_chk check (
    ciphertext is null or octet_length(ciphertext) between 17 and 32768),
  -- Cubre también el índice de la FK user_id y la consulta de cambios por cursor.
  constraint vault_items_user_revision_uk unique (user_id, revision)
);

create index vault_items_tombstones_idx on app.vault_items (deleted_at)
  where deleted_at is not null;

create trigger vault_items_set_updated_at before update on app.vault_items
  for each row execute function app.set_updated_at();

-- =====================================================================
-- 4. SESSIONS — una por dispositivo con sesión iniciada.
--    El refresh token es opaco (32 B aleatorios); solo se guarda su SHA-256.
-- =====================================================================
create table app.sessions (
  id                           uuid        primary key default gen_random_uuid(),
  user_id                      uuid        not null references app.users (id) on delete cascade,
  device_name                  text        not null,
  refresh_token_hash           bytea       not null,
  previous_refresh_token_hash  bytea,
  created_at                   timestamptz not null default now(),
  last_used_at                 timestamptz not null default now(),
  expires_at                   timestamptz not null,
  revoked_at                   timestamptz,
  revoke_reason                text,

  constraint sessions_device_name_chk check (char_length(btrim(device_name)) between 1 and 100),
  constraint sessions_token_hash_chk check (
    octet_length(refresh_token_hash) = 32
    and (previous_refresh_token_hash is null or octet_length(previous_refresh_token_hash) = 32)),
  constraint sessions_expiry_chk check (expires_at > created_at),
  constraint sessions_revoke_chk check (
    (revoked_at is null and revoke_reason is null) or
    (revoked_at is not null and revoke_reason in
      ('logout', 'remote_logout', 'password_changed', 'token_reuse', 'account_deleted'))),
  constraint sessions_refresh_token_uk unique (refresh_token_hash)
);

create index sessions_user_idx on app.sessions (user_id);
create index sessions_previous_token_idx on app.sessions (previous_refresh_token_hash)
  where previous_refresh_token_hash is not null;
create index sessions_expires_idx on app.sessions (expires_at);

-- =====================================================================
-- 5. TWO_FACTOR — relación 1:0..1 con users.
--    El secreto se cifra con una llave del SERVIDOR (variable de entorno);
--    server_key_id permite rotar esa llave. Fuera del conocimiento cero (RSG-07).
-- =====================================================================
create table app.two_factor (
  user_id            uuid        primary key references app.users (id) on delete cascade,
  status             text        not null,
  secret_nonce       bytea       not null,
  secret_ciphertext  bytea       not null,
  server_key_id      smallint    not null,
  -- Último paso de tiempo TOTP aceptado: impide reutilizar un código.
  last_used_step     bigint,
  created_at         timestamptz not null default now(),
  activated_at       timestamptz,

  constraint two_factor_status_chk check (status in ('pending', 'active')),
  constraint two_factor_activation_chk check (
    (status = 'pending' and activated_at is null) or
    (status = 'active'  and activated_at is not null)),
  constraint two_factor_nonce_chk check (octet_length(secret_nonce) = 12),
  -- Secreto de 20 B (160 bits, RFC 4226) + 16 B de etiqueta; margen hasta 64 B.
  constraint two_factor_ciphertext_chk check (octet_length(secret_ciphertext) between 26 and 80),
  constraint two_factor_key_id_chk check (server_key_id >= 1),
  constraint two_factor_step_chk check (last_used_step is null or last_used_step > 0)
);

-- =====================================================================
-- 6. RECOVERY_CODES — dependen de two_factor: si se desactiva el 2FA,
--    los códigos desaparecen en cascada y no pueden existir sin él.
--    code_hash = HMAC-SHA256(pepper_del_servidor, código_normalizado).
-- =====================================================================
create table app.recovery_codes (
  id          uuid        primary key default gen_random_uuid(),
  user_id     uuid        not null references app.two_factor (user_id) on delete cascade,
  code_hash   bytea       not null,
  used_at     timestamptz,
  created_at  timestamptz not null default now(),

  constraint recovery_codes_hash_chk check (octet_length(code_hash) = 32),
  constraint recovery_codes_user_hash_uk unique (user_id, code_hash)
);

-- =====================================================================
-- 7. EMAIL_VERIFICATIONS — códigos de 6 dígitos enviados por correo.
--    code_hash = HMAC-SHA256(pepper, código). Máx. 5 intentos.
-- =====================================================================
create table app.email_verifications (
  id            uuid        primary key default gen_random_uuid(),
  user_id       uuid        not null references app.users (id) on delete cascade,
  purpose       text        not null,
  target_email  text        not null,
  code_hash     bytea       not null,
  attempts      smallint    not null default 0,
  expires_at    timestamptz not null,
  consumed_at   timestamptz,
  created_at    timestamptz not null default now(),

  constraint email_verifications_purpose_chk check (purpose in ('verify_email', 'change_email')),
  constraint email_verifications_target_chk check (
    target_email = lower(target_email)
    and char_length(target_email) <= 254
    and target_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
  constraint email_verifications_hash_chk check (octet_length(code_hash) = 32),
  constraint email_verifications_attempts_chk check (attempts between 0 and 5),
  constraint email_verifications_expiry_chk check (expires_at > created_at)
);

-- Solo un código vigente por usuario y propósito.
create unique index email_verifications_active_uk on app.email_verifications (user_id, purpose)
  where consumed_at is null;
create index email_verifications_user_idx on app.email_verifications (user_id);
create index email_verifications_expires_idx on app.email_verifications (expires_at);

-- =====================================================================
-- 8. AUDIT_LOG — bitácora append-only y encadenada por hash.
--    El id y el hash los asigna el trigger bajo un candado, así que la
--    cadena no tiene huecos aunque haya inserciones concurrentes.
--    Los datos personales (IP, user-agent) viven en audit_log_pii para
--    poder anonimizarlos SIN romper la cadena.
-- =====================================================================
create table app.audit_log (
  id           bigint      primary key,
  occurred_at  timestamptz not null default now(),
  user_id      uuid        references app.users (id) on delete restrict,
  session_id   uuid,        -- sin FK: las sesiones se purgan, la historia no
  event_type   text        not null,
  outcome      text        not null,
  metadata     jsonb       not null default '{}'::jsonb,
  prev_hash    bytea,
  hash         bytea       not null,

  constraint audit_log_event_type_chk check (event_type in (
    'account_registered', 'email_verification_sent', 'email_verified',
    'login', 'totp_verification', 'recovery_code_login', 'account_locked',
    'token_reuse_detected', 'logout', 'session_revoked',
    'master_password_changed', 'email_change_requested', 'email_changed',
    'two_factor_enabled', 'two_factor_disabled', 'recovery_codes_regenerated',
    'account_deleted', 'account_anonymized')),
  constraint audit_log_outcome_chk check (outcome in ('success', 'failure')),
  constraint audit_log_metadata_chk check (
    jsonb_typeof(metadata) = 'object' and pg_column_size(metadata) <= 2048),
  constraint audit_log_chain_chk check (
    (id = 1 and prev_hash is null) or (id > 1 and octet_length(prev_hash) = 32)),
  constraint audit_log_hash_chk check (octet_length(hash) = 32),
  constraint audit_log_hash_uk unique (hash)
);

create index audit_log_user_time_idx on app.audit_log (user_id, occurred_at desc);

create table app.audit_log_pii (
  event_id    bigint primary key references app.audit_log (id) on delete restrict,
  ip          inet,
  user_agent  text,
  constraint audit_log_pii_ua_chk check (user_agent is null or char_length(user_agent) <= 512),
  constraint audit_log_pii_not_empty_chk check (ip is not null or user_agent is not null)
);

-- Serialización canónica de un evento para calcular su hash.
create or replace function app.audit_hash(
  p_prev_hash bytea, p_id bigint, p_occurred_at timestamptz, p_user_id uuid,
  p_session_id uuid, p_event_type text, p_outcome text, p_metadata jsonb)
returns bytea language sql stable
set search_path = app, pg_temp
as $$
  select sha256(
    coalesce(p_prev_hash, ''::bytea) ||
    convert_to(concat_ws('|',
      p_id::text,
      to_char(p_occurred_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
      coalesce(p_user_id::text, ''),
      coalesce(p_session_id::text, ''),
      p_event_type,
      p_outcome,
      p_metadata::text), 'UTF8'));
$$;

create or replace function app.audit_log_chain()
returns trigger language plpgsql
set search_path = app, pg_temp
as $$
declare
  v_last_id   bigint;
  v_last_hash bytea;
begin
  -- Serializa a quienes escriben en la bitácora hasta el COMMIT.
  -- Por eso el evento debe insertarse al FINAL de cada transacción.
  perform pg_advisory_xact_lock(hashtext('app.audit_log_chain'));
  select a.id, a.hash into v_last_id, v_last_hash
    from app.audit_log a order by a.id desc limit 1;
  new.id          := coalesce(v_last_id, 0) + 1;
  new.prev_hash   := v_last_hash;
  new.occurred_at := clock_timestamp();
  new.hash        := app.audit_hash(new.prev_hash, new.id, new.occurred_at, new.user_id,
                                    new.session_id, new.event_type, new.outcome, new.metadata);
  return new;
end $$;

create trigger audit_log_chain before insert on app.audit_log
  for each row execute function app.audit_log_chain();

create or replace function app.audit_log_reject_change()
returns trigger language plpgsql as $$
begin
  raise exception 'app.audit_log es append-only: % no permitido', tg_op
    using errcode = 'insufficient_privilege';
end $$;

create trigger audit_log_no_update_delete before update or delete on app.audit_log
  for each row execute function app.audit_log_reject_change();
create trigger audit_log_no_truncate before truncate on app.audit_log
  for each statement execute function app.audit_log_reject_change();

-- Recorre la cadena y devuelve el primer evento alterado (si lo hay).
create or replace function app.verify_audit_chain()
returns table (ok boolean, events_checked bigint, first_broken_id bigint)
language plpgsql stable
set search_path = app, pg_temp
as $$
declare
  r           app.audit_log%rowtype;
  v_prev      bytea  := null;
  v_expected  bigint := 1;
begin
  for r in select * from app.audit_log order by id loop
    if r.id <> v_expected
       or r.prev_hash is distinct from v_prev
       or r.hash <> app.audit_hash(r.prev_hash, r.id, r.occurred_at, r.user_id,
                                   r.session_id, r.event_type, r.outcome, r.metadata) then
      return query select false, v_expected - 1, r.id;
      return;
    end if;
    v_prev := r.hash;
    v_expected := v_expected + 1;
  end loop;
  return query select true, v_expected - 1, null::bigint;
end $$;

-- =====================================================================
-- 9. FUNCIONES TRANSACCIONALES (lógica con concurrencia delicada)
-- =====================================================================

-- Crea, actualiza o borra (lápida) un elemento con concurrencia optimista.
--   p_nonce y p_ciphertext NULL  → borrar.
--   p_expected_version NULL      → el cliente cree que el elemento es nuevo.
-- Resultados: created | updated | deleted | conflict | not_found | forbidden
-- Orden de candados: primero users, luego vault_items (evita interbloqueos).
create or replace function app.vault_write_item(
  p_user_id          uuid,
  p_item_id          uuid,
  p_expected_version integer,
  p_format_version   smallint,
  p_nonce            bytea,
  p_ciphertext       bytea)
returns table (result text, item_version integer, item_revision bigint)
language plpgsql
set search_path = app, pg_temp
as $$
declare
  v_rev       bigint;
  v_item      app.vault_items%rowtype;
  v_is_delete boolean := p_nonce is null and p_ciphertext is null;
begin
  if (p_nonce is null) <> (p_ciphertext is null) then
    raise exception 'nonce y ciphertext deben enviarse juntos' using errcode = 'invalid_parameter_value';
  end if;

  select u.vault_revision into v_rev
    from app.users u
   where u.id = p_user_id and u.deleted_at is null
     for update;
  if not found then
    raise exception 'usuario inexistente o eliminado' using errcode = 'no_data_found';
  end if;

  select * into v_item from app.vault_items vi where vi.id = p_item_id for update;

  if not found then
    if v_is_delete or p_expected_version is not null then
      return query select 'not_found'::text, null::integer, null::bigint;
      return;
    end if;
    v_rev := v_rev + 1;
    insert into app.vault_items (id, user_id, version, revision, format_version, nonce, ciphertext)
    values (p_item_id, p_user_id, 1, v_rev, p_format_version, p_nonce, p_ciphertext)
    on conflict (id) do nothing;
    if not found then                       -- carrera con otro usuario y el mismo UUID
      return query select 'forbidden'::text, null::integer, null::bigint;
      return;
    end if;
    update app.users u set vault_revision = v_rev where u.id = p_user_id;
    return query select 'created'::text, 1, v_rev;
    return;
  end if;

  if v_item.user_id <> p_user_id then
    return query select 'forbidden'::text, null::integer, null::bigint;
    return;
  end if;

  if p_expected_version is null
     or v_item.version <> p_expected_version
     or v_item.deleted_at is not null then
    return query select 'conflict'::text, v_item.version, v_item.revision;
    return;
  end if;

  v_rev := v_rev + 1;
  update app.vault_items vi
     set version        = vi.version + 1,
         revision       = v_rev,
         format_version = case when v_is_delete then vi.format_version else p_format_version end,
         nonce          = p_nonce,
         ciphertext     = p_ciphertext,
         deleted_at     = case when v_is_delete then now() else null end
   where vi.id = p_item_id;
  update app.users u set vault_revision = v_rev where u.id = p_user_id;

  return query select (case when v_is_delete then 'deleted' else 'updated' end)::text,
                      v_item.version + 1, v_rev;
end $$;

-- Rota el refresh token. Si llega un token ya rotado, se asume robo y se
-- revoca la sesión completa.
-- Resultados: rotated | reuse_detected | invalid
create or replace function app.session_rotate(
  p_presented_hash bytea,
  p_new_hash       bytea,
  p_new_expires_at timestamptz)
returns table (result text, out_session_id uuid, out_user_id uuid)
language plpgsql
set search_path = app, pg_temp
as $$
declare
  v_s app.sessions%rowtype;
begin
  select * into v_s from app.sessions s where s.refresh_token_hash = p_presented_hash for update;
  if found then
    if v_s.revoked_at is not null or v_s.expires_at <= now() then
      return query select 'invalid'::text, v_s.id, v_s.user_id;
      return;
    end if;
    update app.sessions s
       set previous_refresh_token_hash = s.refresh_token_hash,
           refresh_token_hash          = p_new_hash,
           last_used_at                = now(),
           expires_at                  = p_new_expires_at
     where s.id = v_s.id;
    return query select 'rotated'::text, v_s.id, v_s.user_id;
    return;
  end if;

  select * into v_s from app.sessions s where s.previous_refresh_token_hash = p_presented_hash for update;
  if found and v_s.revoked_at is null then
    update app.sessions s set revoked_at = now(), revoke_reason = 'token_reuse' where s.id = v_s.id;
    return query select 'reuse_detected'::text, v_s.id, v_s.user_id;
    return;
  end if;

  return query select 'invalid'::text, null::uuid, null::uuid;
end $$;

-- ---------------------------------------------------------------------
-- 10. MANTENIMIENTO (rol endsec_jobs)
-- ---------------------------------------------------------------------

-- Purga lápidas antiguas y registra hasta qué revisión se purgó.
create or replace function app.purge_tombstones(p_older_than interval default interval '90 days')
returns integer language plpgsql
set search_path = app, pg_temp
as $$
declare
  r        record;
  v_total  integer := 0;
  v_n      integer;
begin
  for r in
    select vi.user_id, max(vi.revision) as max_rev
      from app.vault_items vi
     where vi.deleted_at is not null and vi.deleted_at < now() - p_older_than
     group by vi.user_id
     order by vi.user_id
  loop
    perform 1 from app.users u where u.id = r.user_id for update;   -- mismo orden de candados
    delete from app.vault_items vi
     where vi.user_id = r.user_id and vi.deleted_at is not null and vi.revision <= r.max_rev
       and vi.deleted_at < now() - p_older_than;
    get diagnostics v_n = row_count;
    update app.users u
       set purged_through_revision = greatest(u.purged_through_revision, r.max_rev)
     where u.id = r.user_id;
    v_total := v_total + v_n;
  end loop;
  return v_total;
end $$;

-- Anonimiza cuentas eliminadas tras el periodo de gracia.
create or replace function app.anonymize_deleted_accounts(p_grace interval default interval '30 days')
returns integer language plpgsql
set search_path = app, pg_temp
as $$
declare
  r       record;
  v_total integer := 0;
begin
  for r in
    select u.id from app.users u
     where u.deleted_at is not null and u.anonymized_at is null
       and u.deleted_at < now() - p_grace
     order by u.id
     for update skip locked
  loop
    delete from app.audit_log_pii p using app.audit_log a
     where p.event_id = a.id and a.user_id = r.id;
    delete from app.vault_items         where user_id = r.id;   -- defensivo: ya deberían estar vacíos
    delete from app.sessions            where user_id = r.id;
    delete from app.two_factor          where user_id = r.id;
    delete from app.email_verifications where user_id = r.id;
    update app.users u
       set email = null, email_verified_at = null, display_name = null, auth_hash = null,
           kdf_salt = null, kdf_memory_kib = null, kdf_iterations = null, kdf_parallelism = null,
           pvk_nonce = null, pvk_ciphertext = null, anonymized_at = now()
     where u.id = r.id;
    insert into app.audit_log (user_id, event_type, outcome) values (r.id, 'account_anonymized', 'success');
    v_total := v_total + 1;
  end loop;
  return v_total;
end $$;

-- Punto de entrada único del proceso diario.
create or replace function app.run_maintenance(
  p_tombstone_retention interval default interval '90 days',
  p_deletion_grace      interval default interval '30 days',
  p_pii_retention       interval default interval '90 days')
returns jsonb language plpgsql
set search_path = app, pg_temp
as $$
declare
  v_tomb int; v_anon int; v_sess int; v_codes int; v_totp int; v_pii int;
begin
  v_tomb := app.purge_tombstones(p_tombstone_retention);
  v_anon := app.anonymize_deleted_accounts(p_deletion_grace);

  delete from app.sessions s
   where s.expires_at < now() - interval '7 days'
      or s.revoked_at < now() - interval '7 days';
  get diagnostics v_sess = row_count;

  delete from app.email_verifications e
   where e.expires_at < now() - interval '1 day' or e.consumed_at < now() - interval '1 day';
  get diagnostics v_codes = row_count;

  delete from app.two_factor t
   where t.status = 'pending' and t.created_at < now() - interval '1 day';
  get diagnostics v_totp = row_count;

  delete from app.audit_log_pii p using app.audit_log a
   where p.event_id = a.id and a.occurred_at < now() - p_pii_retention;
  get diagnostics v_pii = row_count;

  return jsonb_build_object(
    'tombstones_purged', v_tomb, 'accounts_anonymized', v_anon, 'sessions_purged', v_sess,
    'email_codes_purged', v_codes, 'pending_2fa_purged', v_totp, 'audit_pii_purged', v_pii);
end $$;

-- =====================================================================
-- 11. PRIVILEGIOS (mínimo privilegio)
-- =====================================================================
revoke all on all tables    in schema app from public;
revoke all on all functions in schema app from public;

grant usage on schema app to endsec_api, endsec_jobs;

-- API: nunca borra usuarios ni toca la bitácora salvo para insertar.
grant select, insert, update         on app.users               to endsec_api;
grant select, insert, update, delete on app.vault_items         to endsec_api;
grant select, insert, update, delete on app.sessions            to endsec_api;
grant select, insert, update, delete on app.two_factor          to endsec_api;
grant select, insert, update, delete on app.recovery_codes      to endsec_api;
grant select, insert, update, delete on app.email_verifications to endsec_api;
grant select, insert                 on app.audit_log           to endsec_api;
grant select, insert                 on app.audit_log_pii       to endsec_api;
grant execute on function
  app.vault_write_item(uuid, uuid, integer, smallint, bytea, bytea),
  app.session_rotate(bytea, bytea, timestamptz),
  app.audit_hash(bytea, bigint, timestamptz, uuid, uuid, text, text, jsonb),
  app.verify_audit_chain(),
  app.set_updated_at()
  to endsec_api;

-- Jobs: purgas y anonimización.
grant select, update         on app.users               to endsec_jobs;
grant select, delete         on app.vault_items         to endsec_jobs;
grant select, delete         on app.sessions            to endsec_jobs;
grant select, delete         on app.two_factor          to endsec_jobs;
grant select, delete         on app.recovery_codes      to endsec_jobs;
grant select, delete         on app.email_verifications to endsec_jobs;
grant select, insert         on app.audit_log           to endsec_jobs;
grant select, delete         on app.audit_log_pii       to endsec_jobs;
grant execute on function
  app.run_maintenance(interval, interval, interval),
  app.purge_tombstones(interval),
  app.anonymize_deleted_accounts(interval),
  app.audit_hash(bytea, bigint, timestamptz, uuid, uuid, text, text, jsonb),
  app.verify_audit_chain(),
  app.set_updated_at()
  to endsec_jobs;

commit;
