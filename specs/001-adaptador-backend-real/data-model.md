# Data Model: estado del adaptador (solo memoria)

Nada de esto se guarda en disco ni se registra. Se pierde al cerrar la app (ver research R3 y R6).

| Entidad | Campos | Se llena | Se usa | Se borra |
|---|---|---|---|---|
| Desafío de correo | correo normalizado → `challengeToken` | registro; login con correo sin verificar; reenvío | confirmar y reenviar el código | al confirmar; al reemplazarlo |
| Llaves conocidas | `userId`, `kdf`, `protectedVaultKey`, `keyVersion` | toda respuesta de acceso (verificación, login, 2FA) | `GET /account` y los que devuelven la cuenta | al cerrar sesión o eliminar la cuenta |
| Cursor | última `revision` de `/vault/sync` | cada descarga | `keyVersion` en `GET /account` sin bajar toda la bóveda | al cerrar sesión |

## Reglas
- El correo del mapa se normaliza igual que el servidor: recortar, minúsculas, NFC.
- `toString()` de las clases de estado no imprime tokens ni llaves.
- La `keyVersion` de `GET /account` es siempre la del servidor (`/vault/sync`); las llaves conocidas solo se usan si su
  `keyVersion` coincide.
