# Reporte · Tarea 29 · Aviso al copiar dentro de las hojas (última del frontend)

No se tocó `core/crypto`, `core/session`, autenticación ni SQL; el portapapeles (`SecretClipboard`) no se modificó, solo se verificó. Sin capturas.

## Paso 1 · Avisos dentro de las hojas inferiores
**Causa confirmada:** una `ModalBottomSheet` es otra ventana encima de la principal; el `HostedMessage` de `GeneratorScreen` iba al anfitrión de `AppNavHost`, que queda detrás de ella.

**Arreglo:** `AppBottomSheet` (`core/designsystem/component/AppBottomSheet.kt`), un envoltorio de `ModalBottomSheet` con **su propio `MessageHostState`** que provee `LocalMessageHost`; `HostedMessage` usa el más cercano, así que lo pedido dentro de una hoja aparece dentro de ella.
- **Posición:** `SheetMessageSlot()` se pone justo encima del botón inferior; deja 12 dp entre el aviso y el botón y ocupa 0 de alto mientras no hay aviso. En la hoja del Generador va encima de «Usar esta contraseña». Una hoja que no pone su lugar dibuja el aviso al final de su contenido.
- **Animación:** la misma del anfitrión (tarea 26): entra subiendo 16 dp en 200 ms y sale en 150 ms.
- **Al cerrarse la hoja** su anfitrión se limpia (`clear()` y relojes cancelados).
- Las **11** `ModalBottomSheet` de la app ahora son `AppBottomSheet` (no queda ninguna directa).

| Hoja | ¿Pide avisos? | ¿Se ven ahora? |
|---|---|---|
| Generador (desde Nueva/Editar, «Usar esta contraseña») | **Sí**: «Copiada · se borra del portapapeles en 30 s» | **Sí**, dentro de la hoja y encima del botón (antes salía detrás) |
| `ReauthSheet` (contraseña maestra) | No | Sí, tiene su anfitrión por si lo pide |
| Cerrar sesión (`SignOutSheet`) | No | Ídem |
| Cerrar sesión de un dispositivo / de los demás (Dispositivos) | No (su resultado sale en la pantalla, ya visible al cerrarse la hoja) | Ídem |
| Eliminar cuenta (confirmación) | No | Ídem |
| Eliminar contraseña (detalle) | No (el «Deshacer» sale en la lista, tras cerrarse la hoja) | Ídem |
| Descartar cambios (Nueva/Editar) | No | Ídem |
| Conflictos | No tienen hoja propia (resolver es una pantalla; su aviso sale en la ventana principal) | — |
| Documentos legales | No | Ídem |
| Licencias | No | Ídem |
| Bloqueo automático (Ajustes) | No (la fila ya muestra el valor nuevo) | Ídem |
| «¿Ya guardaste tus códigos?» (2FA) | No | Ídem |

## Paso 2 · Confirmación en el propio botón
`CopyConfirmation` + `CopyIcon` + `copiedSemantics` (`CopyConfirmation.kt`): al copiar, el ícono pasa a **palomita** con fundido cruzado de 150 ms (`Motion.EaseOut`), se queda **1,5 s** (`Motion.CopyConfirmHoldMillis`) y vuelve; **tocar otra vez mientras tanto vuelve a copiar y reinicia los 1,5 s**. Accesibilidad: el botón anuncia «Copiado» (`stateDescription` + región viva) y su descripción **no cambia de nombre**. Con «Quitar animaciones» el cambio es instantáneo (un solo ícono, sin fundido).

Aplicado a **todos** los botones de copiar:
| Botón | Cómo |
|---|---|
| Generador, pestaña y hoja (ícono amarillo) | `ResultIconButton` con `confirmation` |
| Detalle: usuario (ícono) | `FieldActionButton(confirmation)` |
| Detalle: contraseña (botón «Copiar contraseña») | `PrimaryButton` con `trailingIcon` (el ícono de copiar al final del botón → palomita) |
| 2FA: clave (ícono) | `CopyIconButton` con `confirmation` |
| 2FA: códigos de respaldo (Paso 3 y «Generar códigos nuevos») | `SecondaryButton` con `trailingIcon` |

*Nota de diseño:* «Copiar contraseña» y «Copiar códigos» eran botones de solo texto; se les añadió un ícono de copiar al final para que la palomita tenga dónde mostrarse (`PrimaryButton`/`SecondaryButton` ganan un parámetro opcional `trailingIcon`).

## Paso 3 · La copia es sensible
- **Un solo camino:** ninguna pantalla habla con el `ClipboardManager`; todo pasa por `SecretClipboard`, que marca el `ClipData` con `EXTRA_IS_SENSITIVE` **antes** de `setPrimaryClip`. **La hoja del Generador** usa el mismo `GeneratorViewModel` que la pestaña (con otra clave de ViewModel), que copia con `SecretClipboard.copy`. Una prueba nueva (`ClipboardOnlyThroughSecretClipboardTest`) vigila que ningún otro archivo use `setPrimaryClip`/`ClipboardManager`, y `GeneratorViewModelTest` ya comprueba que se copia por `SecretClipboard`. No hubo ninguna copia fuera de `SecretClipboard` que corregir.
- **Vista previa del sistema (emulador API 36):** lo intenté con una copia por `SecretClipboard` y otra de control sin la marca, con la app en primer plano, leyendo la pantalla con `uiautomator`. **En ninguna de las dos apareció la burbuja de vista previa del sistema** (ni el texto en claro ni los puntos), así que esta comprobación **no es concluyente en el emulador**. Lo que sí se vio: `ClipboardDeviceTest` (E3) pasó en la corrida de `core` tras reiniciar el emulador (111/111), es decir, la marca `EXTRA_IS_SENSITIVE` queda en la descripción del portapapeles. **Pendiente en el Redmi:** copiar una contraseña y mirar la sugerencia del teclado y la vista previa (en MIUI/HyperOS la presentación es del fabricante).

## Pruebas (salida real)
- Nuevas, sin capturas: `AppBottomSheetTest` (6: el aviso pedido dentro de la hoja se ve dentro de ella y no en el anfitrión principal, encima de su botón, entra subiendo y sale más rápido, **al cerrar la hoja su aviso desaparece** y su anfitrión queda limpio, una hoja sin lugar propio lo dibuja al final, y la **hoja real del Generador** muestra «Copiada…» encima de «Usar esta contraseña»), `CopyConfirmationTest` (5: palomita y vuelta a los 1,5 s con «Copiado» anunciado, fundido cruzado de 150 ms con los dos íconos a la mitad, otro toque reinicia, con «Quitar animaciones» no hay movimiento, el nombre del botón no cambia) y `ClipboardOnlyThroughSecretClipboardTest` (JVM).
- `testDebugUnitTest`, `lintDebug` y `assembleRelease`: OK. Instrumentadas: `core` 111/111 (incluye E3), `feature.vault` 14/14, `feature.account` 62/62.
- Antes de las pruebas el emulador se cayó y hubo que reiniciarlo en frío (ruido del entorno, no del código).

## Pendiente
- Confirmar en el Redmi la vista previa/sugerencia del portapapeles con la copia marcada como sensible.
- Probar a mano que el aviso y la palomita se vean bien al copiar en la hoja del Generador.

## Vistas / cambios (cómo llegar)
No hay vistas nuevas. **Hoja del Generador:** Bóveda → «+» (Nueva contraseña) → generar/«Usar» desde el campo de contraseña → copiar (aviso dentro de la hoja y palomita). **Botones de copiar:** Generador (pestaña), detalle de una contraseña (usuario y «Copiar contraseña») y Ajustes → Verificación en dos pasos (clave y códigos).
