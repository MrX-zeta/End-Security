# Reporte · Tarea 30 · Textos de Eliminar cuenta sin contradecir el conocimiento cero

Solo textos (`strings.xml`) y las pruebas que los comparan. No se tocó código, crypto, sesión, autenticación ni SQL; no se tocaron los textos legales (`docs/legal`).

## Paso 1 · Textos nuevos
Aplicados tal cual la tabla de la tarea (se quitó «de nuestros servidores»; basta con «cifradas»):

| Recurso | Texto nuevo |
|---|---|
| `delete_consequence_passwords` | Se borrarán tus contraseñas cifradas. |
| `delete_sheet_body` (`one`) | Se borrará tu %d contraseña cifrada y se cerrará tu sesión en todos tus dispositivos. |
| `delete_sheet_body` (`many`/`other`) | Se borrarán tus %d contraseñas cifradas y se cerrará tu sesión en todos tus dispositivos. |
| `delete_sheet_body_empty` | Se borrará tu cuenta y se cerrará tu sesión en todos tus dispositivos. |
| `deleted_body` | Borramos tus contraseñas cifradas. Gracias por usar End Security. |

Con la tabla también desaparece «No podrás recuperarla/recuperarlas» del cuerpo de la hoja; el título de la hoja («¿Eliminar tu cuenta para siempre?») sigue avisando que no se deshace.

## Paso 2 · Resto de los textos
Revisé `strings.xml` buscando «servidor», «nuestros», «guardamos», «subir», «nube», «sincroniz», «respald» y «tus contraseñas/tu contraseña», y las pantallas en busca de literales con esas palabras (no hay ninguno fuera de `strings.xml`).

**Cambié uno más:**
- `settings_sign_out_body`: «…Tu bóveda sigue guardada en tu cuenta.» → «…Tu bóveda **cifrada** sigue guardada en tu cuenta.»

**Los revisé y los dejé igual:**
- `devices_offline_body` y `activity_offline_body` («Esta información está en nuestros servidores…»): hablan de la lista de dispositivos y de la actividad, no de contraseñas.
- `vault_offline_notice` («Tus cambios se guardan y se subirán al reconectar») y `settings_sign_out_unsynced_title` («Tienes N cambios sin subir»): dicen «cambios», no «contraseñas»; no sugieren que el servidor las vea.
- `vault_empty_body`, `health_ranking_footnote` y `health_breached_footnote`: ya dicen que las contraseñas no salen del teléfono o que se protegen antes de salir.

## Pruebas
- `DeleteAccountScreensTest`: actualizadas las tres comparaciones de texto (plural con 24, singular con 1 y «Cuenta eliminada»); la que usa `delete_consequence_passwords` lee el recurso, así que sigue valiendo.
- `DeleteAccountScreensTest` 15/15; `testDebugUnitTest`, `lintDebug` y `assembleRelease`: OK.

## Pendiente
Nada de esta tarea. (Figma ya estaba actualizado según la tarea.)
