# Tarea 30 · Textos de Eliminar cuenta sin contradecir el conocimiento cero

**Estado:** hecha
**Escrita:** 2026-11-07 · sesión de diseño

## Revisión de la tarea 29
Aprobada:
- `AppBottomSheet` con su propio anfitrión de avisos (las 11 hojas migradas);
- `SheetMessageSlot` encima del botón;
- la palomita en todos los botones de copiar (aprobado el `trailingIcon` en «Copiar contraseña» y «Copiar códigos»);
- un solo camino al portapapeles por `SecretClipboard`;
- el arreglo del ícono negro.

La vista previa del portapapeles queda para el Redmi.

## El problema
Los textos de Eliminar cuenta dicen que se borran «tus contraseñas de nuestros servidores». Un lector atento (el profesor) puede entender que el servidor **guarda las contraseñas**, lo que contradice el conocimiento cero. El servidor solo tiene la **copia cifrada**, que no puede leer. El texto debe seguir avisando que se borra, pero decir **qué** se borra.

## Paso 1 · Textos nuevos (`strings.xml`)
Cortos a propósito: la palabra «cifradas» basta. Se quitó «de nuestros servidores» y no se agregan explicaciones.

| Recurso | Texto nuevo |
|---|---|
| `delete_consequence_passwords` | Se borrarán tus contraseñas cifradas. |
| `delete_sheet_body` (plural `one`) | Se borrará tu %d contraseña cifrada y se cerrará tu sesión en todos tus dispositivos. |
| `delete_sheet_body` (`many` / `other`) | Se borrarán tus %d contraseñas cifradas y se cerrará tu sesión en todos tus dispositivos. |
| `delete_sheet_body_empty` | Se borrará tu cuenta y se cerrará tu sesión en todos tus dispositivos. |
| `deleted_body` | Borramos tus contraseñas cifradas. Gracias por usar End Security. |

Figma ya está actualizado (`47:2290`, `62:3233`, `62:3270`).

## Paso 2 · Revisar el resto de los textos
Busca en `strings.xml` (y en los textos de las pantallas que no estén ahí) cualquier frase que pueda leerse como «el servidor tiene o ve tus contraseñas»: «contraseñas … servidor», «guardamos tus contraseñas», «subir tus contraseñas», etc.
- Corrígela con la misma idea, igual de corta: basta decir «cifradas»; no agregues explicaciones.
- **No toques** los textos legales (`docs/legal`): ya lo dicen bien.
- Entrega en el reporte la lista de los que cambiaste, o «ninguno más».

## Terminado cuando
- Los textos cambiados y las pruebas de pantalla que comparan esos textos (`DeleteAccountScreensTest` y las que salgan) actualizadas y pasando.
- `testDebugUnitTest`, lint y `assembleRelease` pasan.
- Commits hechos y subidos, sin cambios de finales de línea.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
