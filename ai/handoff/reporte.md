# Reporte · Tarea 14 · Salud (SCR-22…28, SCR-40; OP-09, OP-10, OP-44, OP-45)

**Estado:** hecha
**Fecha:** 2026-10-28

**REQUIERE REVISIÓN HUMANA:** análisis que descifra toda la bóveda (`HealthRepositoryImpl`), la caché cifrada de HIBP y las fotos diarias (`HealthStores`), el cliente de red (`HibpClient`, permiso `INTERNET`) y SQL de Room. Pasó por el subagente `security-reviewer` (ver abajo).

## Qué se hizo

### Paso 1 · Análisis (`feature/health/domain`)
- **`HealthAnalyzer`** (Kotlin puro): fuerza con el mismo estimador (débil 0–1, regular 2, fuerte 3–4), repetidas por SHA-256 **en memoria** (no se guarda ni el hash ni el grupo), antigua = más de 365 días, filtrada = cuenta de HIBP > 0. Niveles: Crítico = filtrada; Alto = repetida o débil; Medio = regular o antigua; Bajo = el resto. Puntuación `round(100 × promedio(pesos))` con 1,0 / 0,7 / 0,35 / 0; sin elementos no hay puntuación. Mensajes en 90, 70 y 50. El resultado (`HealthReport`/`ItemHealth`) **no lleva contraseñas**: solo ids, nombres, niveles, largos, cuentas y fechas.
- **`HealthRepositoryImpl`:** descifra solo durante el análisis (`Dispatchers.Default`); dañados y de versión desconocida no cuentan; el estado se vacía al bloquear.
- **Progreso (`HealthProgressCalculator`):** un punto por semana (el último valor), «+N puntos … de X a Y», «fortalecidas este mes», «filtradas resueltas» (máximo del mes menos hoy, mínimo 0) y la distribución por fuerza.
- **Foto diaria (OP-45):** una fila cifrada por día local (reemplaza la de hoy), solo totales (INV-29), y se borran las de más de 12 meses.

### Paso 2 · HIBP (`feature/health/data`, DEC-21)
- **`HibpClient`** (`HttpURLConnection`, aparte de `ApiTransport`): `GET /range/{5 hex de SHA-1}` con `Add-Padding: true` y `User-Agent: EndSecurity-Android`; el sufijo se busca en memoria, las líneas con cuenta 0 son relleno; espera de 10 s; sin redirecciones; nada a logs. Un prefijo por contraseña distinta, en serie.
- **Red:** `INTERNET` en el manifiesto y `api.pwnedpasswords.com` en `network_security_config.xml` con CA del sistema, sin pinning.
- **Caché (`BreachCache`):** `{pwned_count, local_seq, checked_at}` cifrado con la `vault_key` y AAD `breach|itemId|userId|1`; se vuelve a consultar si cambió el elemento (`local_seq`) o a los 7 días.
- **Cuándo:** al desbloquear (como máximo una vez al día), con «Revisar ahora» y la primera vez que se abre Salud (SCR-40). Sin red lo no revisado queda «pendiente» y no cuenta como filtrado; no se insiste en cada apertura (5 min entre intentos).

### Paso 3 · Pantallas
- **Resumen (SCR-22), Progreso (SCR-23) y cargando (SCR-40)** en una sola ruta con el selector fijo «Resumen | Progreso» (no suma historial): aro con la puntuación, mensaje, «Revisado hoy a las … · Revisar ahora» (o «sin conexión») y las cuatro filas; gráfica de 12 semanas, indicadores y distribución con «Ver ranking de fuerza»; estado vacío «Agrega contraseñas para ver la salud de tu bóveda».
- **Ranking (SCR-24/25)** con «Más fuertes | Más débiles» (los 10 primeros, largo y nivel) y su pie; «Débiles» del Resumen abre «Más débiles».
- **Filtradas, Repetidas y Antiguas (SCR-26…28):** textos de Figma, «Misma contraseña en N sitios», «Cambiar»/tocar una fila lleva a Editar ese elemento, «Ninguna, bien hecho» y la atribución «Datos de Have I Been Pwned».
- **Bóveda:** aviso «N contraseñas necesitan atención · Ver» (Crítico + Alto), triángulos en esas filas y, en el detalle, el bloque de salud («Segura · Fuerte, única y sin filtraciones» o el problema más grave).
- Todo en `RequireUnlocked` con `SecureScreen()`; la pestaña **Salud** de la barra inferior navega. Íconos nuevos: alerta, escudo con alerta y reloj de 20 dp.

## Pruebas (salida real)
- **`testDebugUnitTest`: 543, 0 fallas.** Nuevas: `HealthAnalyzerTest` (14: cada nivel y prioridad, puntuación a mano incluidos 0, 51, 70, 78, 85 y 100, mensajes, grupos de repetidas, límite de 365 días, pendientes), `HealthProgressTest` (7), `HibpClientTest` (13, con un servidor local: prefijo, encabezados, nunca el sufijo ni la contraseña, relleno, tiempo de espera, respuesta vacía, cuenta negativa y cuerpo enorme; vector `password` → `5BAA61E4…` → prefijo `5BAA6`), `HealthRepositoryTest` (16: una consulta por contraseña distinta, caché, `local_seq` cambiado, 7 días, sin red, dañados, una foto por día, recorte de 12 meses, caché cifrada y atada al usuario, bloqueo a mitad de una revisión), `HealthViewModelTest` (9) y casos en la lista y el detalle.
- **`connectedDebugAndroidTest`: 72, 70 pasan y 2 fallan: `ClipboardDeviceTest` (portapapeles compartido del emulador, ya anotado como E3; no lo toqué).** Nuevas que pasan: `HealthScreensTest` (10: textos del diseño, filas que abren, estado vacío, listas) y `HibpClientDeviceTest` con la **red real**.
- **`lintDebug`: 0 errores. `assembleRelease`: compila.**

## En el emulador
- `HibpClientDeviceTest` (red real): «password123» sale **filtrada** (más de 1 000 veces) y una contraseña aleatoria **no**. Valida el permiso `INTERNET` y la configuración de red reales.
- Las pantallas se revisaron con capturas de `HealthScreensTest` (dibujan los composables sin `SecureScreen`, no se quitó ninguna protección): Resumen y Progreso coinciden con el diseño.
- **No recorrí por la interfaz de la app real** crear «password123» y una fuerte y ver la puntuación bajar ni el modo avión: la prueba de Compose contra la app real se colgaba (ver tarea 13). Eso queda cubierto por `HealthRepositoryTest` (crítica por filtrada, pendiente sin red) y la prueba de red real, y **queda para probarlo a mano en el Redmi**.
- Textos que **no** vi en Figma: la descripción de «Antiguas» (`62:2788` no se abrió; se usó el patrón de las otras listas), el título «Aún sin historial» y las leyendas de la ausencia de historial.

## Revisión con `security-reviewer`
Sin hallazgos críticos. **Corregí:**
- (Alta) Con un análisis o una revisión en curso, el bloqueo no vaciaba el estado y el informe podía reaparecer. Ahora el bloqueo corta el trabajo (época por sesión), no se publica nada si se bloqueó durante el análisis y la red se detiene; el trabajo del desbloqueo corre aparte del colector.
- (Media) `VaultLockedException` sin capturar en análisis y revisión; ya no cierra la app.
- (Media) La caché se podía «rejuvenecer» cambiando columnas en claro: `local_seq` y la hora van dentro del bloque cifrado y se verifican al abrir.
- (Media) HIBP: cuerpo con tope de 2 MB, y una respuesta vacía, sin formato o con cuenta negativa ya **no** se toma por «limpia» (queda pendiente).
- (Media/Baja) El detalle ya no vuelve a descifrar toda la bóveda al abrirse; la firma de «¿cambió la bóveda?» sale de los resúmenes (sin contraseñas); los bytes del SHA-1/SHA-256 se sobrescriben; no se reintenta HIBP en cada apertura sin red.

**Quedan abiertos:**
- `checkBreaches` mantiene el mutex del repositorio durante la red (hasta N × 10 s); un bloqueo lo corta, pero otro `refresh()` espera.
- Filas de caché de elementos ya borrados se descifran hasta que se borran con el cierre de sesión (se borran con el elemento por la llave foránea; solo cuenta lo huérfano de otro origen).
- La cantidad y el momento de las consultas a HIBP revelan a quien observa la red cuántas contraseñas distintas hay (el relleno cubre solo el tamaño).
- Las contraseñas en `String` siguen en el heap durante el análisis (limitación conocida, DEC-27).

## Decisiones y desviaciones a revisar
- El **eje de la gráfica** va de 50 a 100 como en Figma; si alguna semana baja de 50, llega hasta 0.
- «Revisado hoy a las …» se calcula con la fila más reciente de la caché (no hay ajuste en DataStore): se borra al cerrar sesión.
- La Bóveda mantiene el análisis al día en segundo plano (1 s después de cada cambio) para el aviso y los triángulos.
- «Débiles» abre el ranking en «Más débiles»; Filtradas, Repetidas y Antiguas llevan a Editar ese elemento.

## Vistas nuevas (cómo llegar)
- **Salud (SCR-22/23/40):** barra inferior → «Salud» (también «Ver» en el aviso de la Bóveda).
- **Ranking (SCR-24/25):** Salud → Progreso → «Ver ranking de fuerza», o Resumen → «Débiles».
- **Filtradas / Repetidas / Antiguas (SCR-26…28):** Salud → Resumen → la fila correspondiente.
