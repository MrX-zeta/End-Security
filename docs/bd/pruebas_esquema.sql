-- =====================================================================
--  End Security · Pruebas del esquema PostgreSQL
--  Ejecutar como superusuario sobre una BD recién creada con schema.sql:
--    psql -v ON_ERROR_STOP=1 -d endsec -f pruebas_esquema.sql
--  Cada bloque imprime "OK ..." o se detiene con el error.
--  Sirve como evidencia para el entregable de pruebas de seguridad.
-- =====================================================================
\set QUIET on
set client_min_messages = notice;

-- Datos auxiliares (bytes de longitud fija, solo para pruebas)
create or replace function pg_temp.b(seed text, len int) returns bytea language sql as
$f$ select substring(sha256(convert_to(seed, 'UTF8')) || sha256(convert_to(seed || '2', 'UTF8')) from 1 for len) $f$;

-- ---------------------------------------------------------------------
-- 1. USERS: alta válida y restricciones
-- ---------------------------------------------------------------------
set role endsec_api;

insert into app.users (id, email, auth_hash, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, pvk_nonce, pvk_ciphertext)
values
 ('11111111-1111-1111-1111-111111111111', 'ana@example.com', '$argon2id$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA',
  pg_temp.b('s1',16), 65536, 3, 1, pg_temp.b('n1',12), pg_temp.b('k1',48)),
 ('22222222-2222-2222-2222-222222222222', 'beto@example.com', '$argon2id$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA',
  pg_temp.b('s2',16), 65536, 3, 1, pg_temp.b('n2',12), pg_temp.b('k2',48));

do $t$
begin
  begin
    insert into app.users (email, auth_hash, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, pvk_nonce, pvk_ciphertext)
    values ('Ana@Example.com', '$argon2id$x', pg_temp.b('s',16), 65536, 3, 1, pg_temp.b('n',12), pg_temp.b('k',48));
    raise exception 'FALLO: aceptó correo con mayúsculas';
  exception when check_violation then raise notice 'OK  users: rechaza correo no normalizado';
  end;

  begin
    insert into app.users (email, auth_hash, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, pvk_nonce, pvk_ciphertext)
    values ('debil@example.com', '$argon2id$x', pg_temp.b('s',16), 1024, 1, 1, pg_temp.b('n',12), pg_temp.b('k',48));
    raise exception 'FALLO: aceptó parámetros Argon2 débiles';
  exception when check_violation then raise notice 'OK  users: rechaza parámetros Argon2 por debajo de OWASP';
  end;

  begin
    insert into app.users (email, auth_hash, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, pvk_nonce, pvk_ciphertext)
    values ('plano@example.com', 'MiPassword123', pg_temp.b('s',16), 65536, 3, 1, pg_temp.b('n',12), pg_temp.b('k',48));
    raise exception 'FALLO: aceptó auth_hash que no es Argon2id';
  exception when check_violation then raise notice 'OK  users: auth_hash debe ser un hash PHC de Argon2id';
  end;

  begin
    insert into app.users (email, auth_hash, kdf_salt, kdf_memory_kib, kdf_iterations, kdf_parallelism, pvk_nonce, pvk_ciphertext)
    values ('ana@example.com', '$argon2id$x', pg_temp.b('s',16), 65536, 3, 1, pg_temp.b('n',12), pg_temp.b('k',48));
    raise exception 'FALLO: aceptó correo duplicado';
  exception when unique_violation then raise notice 'OK  users: correo único entre cuentas activas';
  end;

  begin
    delete from app.users where id = '22222222-2222-2222-2222-222222222222';
    raise exception 'FALLO: la API pudo borrar un usuario';
  exception when insufficient_privilege then raise notice 'OK  privilegios: la API no puede borrar usuarios';
  end;
end $t$;

-- ---------------------------------------------------------------------
-- 2. VAULT: concurrencia optimista, lápidas y cursor
-- ---------------------------------------------------------------------
do $t$
declare r record;
  ana  uuid := '11111111-1111-1111-1111-111111111111';
  beto uuid := '22222222-2222-2222-2222-222222222222';
  item uuid := 'aaaaaaaa-0000-0000-0000-000000000001';
begin
  select * into r from app.vault_write_item(ana, item, null, 1::smallint, pg_temp.b('a',12), pg_temp.b('c1',40));
  assert r.result = 'created' and r.item_version = 1 and r.item_revision = 1, 'crear';
  raise notice 'OK  vault: crear → created v1 r1';

  select * into r from app.vault_write_item(ana, item, 1, 1::smallint, pg_temp.b('b',12), pg_temp.b('c2',40));
  assert r.result = 'updated' and r.item_version = 2 and r.item_revision = 2, 'editar';
  raise notice 'OK  vault: editar con versión correcta → updated v2 r2';

  select * into r from app.vault_write_item(ana, item, 1, 1::smallint, pg_temp.b('c',12), pg_temp.b('c3',40));
  assert r.result = 'conflict' and r.item_version = 2, 'conflicto';
  raise notice 'OK  vault: editar con versión vieja → conflict (el servidor tiene v2)';

  select * into r from app.vault_write_item(beto, item, 2, 1::smallint, pg_temp.b('d',12), pg_temp.b('c4',40));
  assert r.result = 'forbidden', 'ajeno';
  raise notice 'OK  vault: otro usuario no puede tocar el elemento → forbidden';

  select * into r from app.vault_write_item(ana, item, 2, null, null, null);
  assert r.result = 'deleted' and r.item_version = 3 and r.item_revision = 3, 'borrar';
  raise notice 'OK  vault: borrar → lápida v3 r3 (sin contenido)';

  select * into r from app.vault_write_item(ana, item, 3, 1::smallint, pg_temp.b('e',12), pg_temp.b('c5',40));
  assert r.result = 'conflict', 'revivir';
  raise notice 'OK  vault: no se puede editar una lápida → conflict';

  select * into r from app.vault_write_item(ana, 'aaaaaaaa-0000-0000-0000-00000000ffff', null, null, null, null);
  assert r.result = 'not_found', 'borrar inexistente';
  raise notice 'OK  vault: borrar algo que no existe → not_found';

  begin
    perform app.vault_write_item(ana, gen_random_uuid(), null, 1::smallint, pg_temp.b('f',12), null);
    raise exception 'FALLO: aceptó nonce sin ciphertext';
  exception when invalid_parameter_value then raise notice 'OK  vault: nonce y ciphertext van juntos';
  end;

  begin
    insert into app.vault_items (id, user_id, version, revision, nonce, ciphertext)
    values (gen_random_uuid(), ana, 1, 99, null, null);
    raise exception 'FALLO: aceptó elemento vivo sin contenido';
  exception when check_violation then raise notice 'OK  vault: un elemento vivo siempre tiene contenido cifrado';
  end;

  perform app.vault_write_item(ana, 'aaaaaaaa-0000-0000-0000-000000000002', null, 1::smallint, pg_temp.b('g',12), pg_temp.b('c6',40));
  assert (select count(*) from app.vault_items where user_id = ana and revision > 2) = 2, 'cursor';
  assert (select vault_revision from app.users where id = ana) = 4, 'revision usuario';
  raise notice 'OK  sync: cambios desde el cursor 2 → 2 filas (la lápida y el nuevo)';
end $t$;

-- ---------------------------------------------------------------------
-- 3. SESIONES: rotación y detección de reutilización
-- ---------------------------------------------------------------------
do $t$
declare r record;
  ana uuid := '11111111-1111-1111-1111-111111111111';
begin
  insert into app.sessions (user_id, device_name, refresh_token_hash, expires_at)
  values (ana, 'Pixel 7 · Android 15', sha256('rt-1'::bytea), now() + interval '30 days');

  select * into r from app.session_rotate(sha256('rt-1'::bytea), sha256('rt-2'::bytea), now() + interval '30 days');
  assert r.result = 'rotated', 'rotar';
  raise notice 'OK  sesiones: refresh válido → rotated';

  select * into r from app.session_rotate(sha256('rt-1'::bytea), sha256('rt-x'::bytea), now() + interval '30 days');
  assert r.result = 'reuse_detected', 'reuso';
  raise notice 'OK  sesiones: token viejo reutilizado → reuse_detected';

  assert (select revoke_reason from app.sessions where id = r.out_session_id) = 'token_reuse', 'revocada';
  select * into r from app.session_rotate(sha256('rt-2'::bytea), sha256('rt-3'::bytea), now() + interval '30 days');
  assert r.result = 'invalid', 'tras revocar';
  raise notice 'OK  sesiones: tras detectar robo, ni el token legítimo sirve → invalid';
end $t$;

-- ---------------------------------------------------------------------
-- 4. 2FA: anti-replay y códigos de recuperación de un solo uso
-- ---------------------------------------------------------------------
do $t$
declare n int;
  ana uuid := '11111111-1111-1111-1111-111111111111';
begin
  insert into app.two_factor (user_id, status, secret_nonce, secret_ciphertext, server_key_id)
  values (ana, 'pending', pg_temp.b('tn',12), pg_temp.b('ts',36), 1);

  begin
    update app.two_factor set status = 'active' where user_id = ana;
    raise exception 'FALLO: activó 2FA sin fecha de activación';
  exception when check_violation then raise notice 'OK  2fa: activar exige activated_at';
  end;
  update app.two_factor set status = 'active', activated_at = now(), last_used_step = 59000000 where user_id = ana;

  update app.two_factor set last_used_step = 59000001
   where user_id = ana and status = 'active' and (last_used_step is null or last_used_step < 59000001);
  get diagnostics n = row_count; assert n = 1, 'paso nuevo';
  update app.two_factor set last_used_step = 59000001
   where user_id = ana and status = 'active' and (last_used_step is null or last_used_step < 59000001);
  get diagnostics n = row_count; assert n = 0, 'replay';
  raise notice 'OK  2fa: el mismo código TOTP no se acepta dos veces';

  insert into app.recovery_codes (user_id, code_hash)
  select ana, sha256(convert_to('code-' || g, 'UTF8')) from generate_series(1, 10) g;
  update app.recovery_codes set used_at = now()
   where user_id = ana and code_hash = sha256('code-3'::bytea) and used_at is null;
  get diagnostics n = row_count; assert n = 1, 'uso';
  update app.recovery_codes set used_at = now()
   where user_id = ana and code_hash = sha256('code-3'::bytea) and used_at is null;
  get diagnostics n = row_count; assert n = 0, 'reuso';
  raise notice 'OK  2fa: un código de recuperación solo sirve una vez';

  begin
    insert into app.recovery_codes (user_id, code_hash) values ('22222222-2222-2222-2222-222222222222', sha256('x'::bytea));
    raise exception 'FALLO: códigos sin 2FA';
  exception when foreign_key_violation then raise notice 'OK  2fa: no hay códigos de recuperación sin 2FA';
  end;

  delete from app.two_factor where user_id = ana;
  assert (select count(*) from app.recovery_codes where user_id = ana) = 0, 'cascada';
  raise notice 'OK  2fa: desactivar 2FA elimina sus códigos en cascada';
end $t$;

-- ---------------------------------------------------------------------
-- 5. VERIFICACIÓN DE CORREO: un solo código vigente por propósito
-- ---------------------------------------------------------------------
do $t$
declare ana uuid := '11111111-1111-1111-1111-111111111111';
begin
  insert into app.email_verifications (user_id, purpose, target_email, code_hash, expires_at)
  values (ana, 'verify_email', 'ana@example.com', sha256('123456'::bytea), now() + interval '15 minutes');
  begin
    insert into app.email_verifications (user_id, purpose, target_email, code_hash, expires_at)
    values (ana, 'verify_email', 'ana@example.com', sha256('654321'::bytea), now() + interval '15 minutes');
    raise exception 'FALLO: dos códigos vigentes';
  exception when unique_violation then raise notice 'OK  correo: un solo código vigente por propósito';
  end;
  begin
    update app.email_verifications set attempts = 6 where user_id = ana;
    raise exception 'FALLO: más de 5 intentos';
  exception when check_violation then raise notice 'OK  correo: máximo 5 intentos';
  end;
end $t$;

-- ---------------------------------------------------------------------
-- 6. BITÁCORA: append-only y encadenada
-- ---------------------------------------------------------------------
do $t$
declare r record; v_id bigint;
  ana uuid := '11111111-1111-1111-1111-111111111111';
begin
  insert into app.audit_log (user_id, event_type, outcome) values (ana, 'account_registered', 'success') returning id into v_id;
  insert into app.audit_log_pii (event_id, ip, user_agent) values (v_id, '203.0.113.7', 'EndSecurity/1.0 Android 15');
  insert into app.audit_log (user_id, event_type, outcome, metadata) values (ana, 'login', 'failure', '{"reason":"bad_password"}');
  insert into app.audit_log (user_id, event_type, outcome) values (ana, 'login', 'success');
  -- el cliente no puede imponer id ni hash
  insert into app.audit_log (id, user_id, event_type, outcome, hash) values (999, ana, 'logout', 'success', sha256('falso'::bytea))
  returning id into v_id;
  assert v_id = 4, 'id lo asigna el trigger';
  raise notice 'OK  bitácora: id y hash los asigna el servidor (ignoró id=999 y hash falso)';

  select * into r from app.verify_audit_chain();
  assert r.ok and r.events_checked = 4, 'cadena';
  raise notice 'OK  bitácora: cadena íntegra (% eventos)', r.events_checked;

  begin
    update app.audit_log set outcome = 'success' where id = 2;
    raise exception 'FALLO: la API modificó la bitácora';
  exception when insufficient_privilege then raise notice 'OK  bitácora: la API no puede modificar eventos';
  end;
  begin
    delete from app.audit_log where id = 2;
    raise exception 'FALLO: la API borró de la bitácora';
  exception when insufficient_privilege then raise notice 'OK  bitácora: la API no puede borrar eventos';
  end;
end $t$;

reset role;

-- Incluso el dueño de las tablas choca con el trigger
do $t$
begin
  begin
    update app.audit_log set outcome = 'success' where id = 2;
    raise exception 'FALLO: el dueño modificó la bitácora';
  exception when insufficient_privilege then raise notice 'OK  bitácora: el trigger bloquea UPDATE incluso al dueño';
  end;
  begin
    truncate app.audit_log cascade;
    raise exception 'FALLO: truncate';
  exception when insufficient_privilege then raise notice 'OK  bitácora: TRUNCATE bloqueado';
  end;
end $t$;

-- Un superusuario malicioso desactiva el trigger y altera un evento: la cadena lo delata.
begin;
alter table app.audit_log disable trigger audit_log_no_update_delete;
update app.audit_log set outcome = 'success' where id = 2;
alter table app.audit_log enable trigger audit_log_no_update_delete;
do $t$
declare r record;
begin
  select * into r from app.verify_audit_chain();
  assert not r.ok and r.first_broken_id = 2, 'detección';
  raise notice 'OK  bitácora: alteración directa detectada en el evento %', r.first_broken_id;
end $t$;
rollback;

-- ---------------------------------------------------------------------
-- 7. MANTENIMIENTO: purga de lápidas y anonimización
-- ---------------------------------------------------------------------
-- Simulamos el paso del tiempo (solo posible como superusuario).
update app.vault_items set deleted_at = now() - interval '100 days' where deleted_at is not null;

set role endsec_api;
-- Beto elimina su cuenta (transacción T13 resumida)
begin;
  delete from app.vault_items where user_id = '22222222-2222-2222-2222-222222222222';
  update app.sessions set revoked_at = now(), revoke_reason = 'account_deleted'
   where user_id = '22222222-2222-2222-2222-222222222222' and revoked_at is null;
  update app.users set deleted_at = now() where id = '22222222-2222-2222-2222-222222222222';
  insert into app.audit_log (user_id, event_type, outcome) values ('22222222-2222-2222-2222-222222222222', 'account_deleted', 'success');
commit;
reset role;
update app.users set deleted_at = now() - interval '31 days' where id = '22222222-2222-2222-2222-222222222222';

set role endsec_jobs;
do $t$
declare j jsonb; r record;
begin
  j := app.run_maintenance();
  assert (j->>'tombstones_purged')::int = 1 and (j->>'accounts_anonymized')::int = 1, j::text;
  raise notice 'OK  mantenimiento: %', j;

  assert (select purged_through_revision from app.users where id = '11111111-1111-1111-1111-111111111111') = 3, 'horizonte';
  raise notice 'OK  sync: purged_through_revision = 3 → un cliente con cursor < 3 debe hacer descarga completa';

  assert (select email is null and auth_hash is null and anonymized_at is not null
            from app.users where id = '22222222-2222-2222-2222-222222222222'), 'anonimizado';
  raise notice 'OK  anonimización: sin correo ni llaves; la fila queda solo como referencia de la bitácora';

  select * into r from app.verify_audit_chain();
  assert r.ok, 'cadena tras anonimizar';
  raise notice 'OK  bitácora: la cadena sigue íntegra después de anonimizar (% eventos)', r.events_checked;
end $t$;
reset role;

\echo '>>> Todas las pruebas pasaron'

-- ---------------------------------------------------------------------
-- 8. CONCURRENCIA (manual, requiere dos terminales)
--    Dos dispositivos editan el mismo elemento con la misma versión esperada.
--    Terminal A:
--      begin; set role endsec_api;
--      select * from app.vault_write_item('11111111-1111-1111-1111-111111111111',
--        'aaaaaaaa-0000-0000-0000-000000000002', 1, 1::smallint,
--        '\x000000000000000000000001', '\xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
--      -- (no hacer commit todavía)
--    Terminal B (mismo comando con otro nonce): se queda esperando.
--    Terminal A: commit;   → A obtiene "updated v2"
--    Terminal B continúa  → obtiene "conflict" (servidor en v2), no sobrescribe.
-- ---------------------------------------------------------------------
