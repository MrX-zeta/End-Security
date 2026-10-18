---
paths:
  - "app/src/**/core/crypto/**"
  - "app/src/**/core/session/**"
  - "backend/src/**/crypto/**"
---

# Reglas de criptografía (End Security)

Antes de escribir código aquí, busca en `ai/context/knowledge-graph.json`: DEC-05, DEC-08, DEC-09, DEC-28, DEC-29 e INV-01…INV-07.

## Jerarquía de llaves (DEC-05)
- `master_key = Argon2id(contraseña_maestra, kdf_salt, parámetros)`. Los parámetros vienen del servidor o de `account`; **no los elijas tú** (PEND-01).
- `enc_key = HKDF-SHA256(master_key, info="end-security/enc")` · `auth_key = HKDF-SHA256(master_key, info="end-security/auth")`.
- `vault_key` = 32 bytes de `SecureRandom`, envuelta como `pvk = AES-256-GCM(enc_key, vault_key)` (nonce 12 B + 48 B).
- Elemento: `AES-256-GCM(vault_key, json{v:1,…}, nonce aleatorio 12 B, AAD = id|user_id|format_version)`.

## Obligatorio
- Solo librerías reconocidas: JCA (`Cipher.getInstance("AES/GCM/NoPadding")`, etiqueta de 128 bits) y la librería de Argon2 que se elija. Nada de implementaciones propias.
- Nonce nuevo en **cada** cifrado. Nunca derivarlo de un contador ni reutilizarlo.
- Fallo de la etiqueta GCM = dato alterado o contraseña incorrecta: lanza un error tipado, nunca devuelvas datos parciales.
- Comparaciones de secretos en tiempo constante (`MessageDigest.isEqual`).
- Secretos (DEC-27): `ByteArray` solo **dentro** de las primitivas de `core/crypto` (`Argon2idKdf`, `Hkdf`, `MasterPasswordEncoder`, `AesGcm`), probadas con vectores y con `wipe()` en `finally`. En cuanto un secreto sale de ahí o se guarda en un campo (`VaultSession`, repositorios, casos de uso), va en `SecureBytes`: `use { }` para leerlo, `wipe()`/`close()`, error si se lee después de borrar, `toString()` = `"SecureBytes(***)"` y `equals` por identidad. Funciones de alto nivel: `KeyDerivation`.
- Ojo: `SecureBytes.use { }` solo presta los bytes; no lo cierra como el `use` de Kotlin. Para borrarlo al terminar, `try { … } finally { close() }`.
- Llaves del Keystore: AES-256 GCM, no exportables; la biométrica con `setUserAuthenticationRequired(true)` y `setInvalidatedByBiometricEnrollment(true)`.

## Pruebas exigidas por cada función nueva
1. Vector oficial: RFC 9106 (Argon2id), RFC 5869 (HKDF), NIST (AES-GCM).
2. Ida y vuelta (cifrar → descifrar = original).
3. Alteración: cambiar 1 bit del ciphertext, del nonce o del AAD → debe fallar.
4. Nonces distintos en 10 000 cifrados.

## Al terminar
Escribe "REQUIERE REVISIÓN HUMANA" en el resumen y sugiere ejecutar el subagente `security-reviewer`.
