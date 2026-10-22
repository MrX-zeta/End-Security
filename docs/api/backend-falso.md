# Backend falso (mientras no existe el servidor)

La app habla con el servidor por `ApiTransport`. Mientras el backend de NestJS no exista, `BuildConfig.USE_FAKE_BACKEND = true`
(`app/build.gradle.kts`) conecta `FakeTransport`, que responde dentro del teléfono con las mismas reglas de
`docs/api/README.md` y los mismos JSON de `openapi.yaml`. El resto de la app no sabe la diferencia: cuando exista el servidor,
se cambia el transporte y nada más.

- Código: `app/src/main/java/com/luis/end_security/core/network/fake/` (`FakeServer`, `FakeTransport`, `FakeState`).
- Responde con una espera de 300 a 800 ms, para que se vean los estados de carga.
- Sus datos se guardan en `files/fake_server.json` dentro de la app; cerrar la app no borra las cuentas.
  Para empezar de cero: **Ajustes del teléfono → Apps → End Security → Almacenamiento → Borrar datos**.
- Ese archivo no tiene contraseñas ni llaves de la bóveda: guarda hashes, bloques cifrados y los datos que guardaría el servidor
  real (hash de `auth_key`, pvk, bloques de la bóveda). **Pero** también guarda en claro el pepper, el secreto del prelogin y el
  secreto TOTP, que en el servidor real vivirían en variables de entorno o cifrados. **Se acepta tal cual** (decisión de la
  sesión de diseño): es un simulador que desaparece al unir el backend real, y el archivo no sale del teléfono.

## Código de verificación del correo

No se envían correos. Mientras `USE_FAKE_BACKEND` sea `true` (**también en release**), Verificar correo muestra debajo de las
casillas «Modo demostración · Código: 123456» con el código vigente; «Reenviar código» lo cambia. El código vive solo en
memoria y no se escribe en logcat. Desaparece al unir el backend real.

## Cuentas con verificación en dos pasos

En las compilaciones de **depuración**, cualquier correo con `+2fa` en la parte de antes de la `@` se crea con 2FA activo, por
ejemplo `ana+2fa@correo.com`. En release no existe.
Al iniciar sesión con esa cuenta, la app pide el código de 6 dígitos.

1. En la app autenticadora (Google Authenticator, Aegis, 2FAS…), agrega una cuenta **con clave de configuración**:
   - Nombre: `End Security (depuración)`
   - Clave: `ENDSECURITYDEBUGTOTPSECRETKEY234`
   - Tipo: basada en tiempo (TOTP), 6 dígitos, cada 30 s, SHA-1.
2. Todas las cuentas `+2fa` comparten esa clave. Es pública y solo existe en el backend falso.
3. Un mismo código no se acepta dos veces (anti-replay): si fallaste, espera al siguiente.

Códigos de respaldo de esas cuentas (cada uno sirve una sola vez por cuenta; se pueden escribir con o sin guion):

| | | | | |
|---|---|---|---|---|
| `DBGA-0001` | `DBGA-0002` | `DBGA-0003` | `DBGA-0004` | `DBGA-0005` |
| `DBGA-0006` | `DBGA-0007` | `DBGA-0008` | `DBGA-0009` | `DBGA-0010` |

Al entrar con uno, si quedan 3 o menos, la Bóveda muestra cuántos quedan.

## Límites que sí aplica

Los de `docs/api/README.md` (contrato **1.1.0**): 5 contraseñas incorrectas seguidas en Iniciar sesión bloquean 15 min, y **el
quinto fallo ya responde `429 rate_limited`** (900 s), también con un correo que no existe; lo mismo con el quinto código del 2FA;
el código del correo dura
15 min y admite 5 intentos; «Reenviar código» espera 60 s entre envíos y permite 5 por hora; el reto del 2FA dura 5 min y sirve
una vez. Un correo que no existe responde igual y tarda lo mismo que una contraseña incorrecta.

No aplica el límite por IP (no hay IP) y el cambio de correo es mínimo. Lo demás se anota en el reporte de la tarea.
