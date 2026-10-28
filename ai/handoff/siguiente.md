# Tarea 14 · Salud (SCR-22…28, SCR-40; OP-09, OP-10, OP-44, OP-45)

**Estado:** hecha
**Escrita:** 2026-10-22 · sesión de diseño

## Revisión de la tarea 13
Aprobada, con la revisión humana de `PasswordGenerator`: es correcto. Usa `nextInt(bound)`, pone al menos un carácter de
cada clase, mezcla con Fisher-Yates y devuelve `CharArray`. También están bien:
- `SyncShutdown` con el borrado dentro del mutex;
- la lápida de rescate escrita antes del DELETE;
- `PasswordHandoff`.

Aprobadas tus desviaciones:
- barras amarillas en la tarjeta oscura del generador;
- el interruptor apagado en gris;
- ir a la Bóveda después de guardar.

Sobre lo que quedó abierto:
- **`lock()` al final de `signOutLocally`:** se queda así. Son segundos y el flujo es correcto.
- **`ClipboardDeviceTest`:** la persona del equipo desactivará el portapapeles compartido del emulador y las repetirá (E3 en `revisiones-pendientes.md`).
- **Las dos pruebas a mano** (guardar lo generado y verlo en la lista; la hoja «Generar» dentro de Nueva) quedaron anotadas para el Redmi.

## Objetivo
La pestaña **Salud** completa. Todo se calcula en el teléfono con la bóveda abierta. Al servidor nunca le llega nada:
HIBP se consulta directo y solo con el prefijo (DEC-20, DEC-21, DEC-54, DEC-55; grafo **v1.5.7**, que ya tiene las fórmulas).

**Figma:**
- Resumen `47:1045` y Progreso `62:2357`;
- Ranking `62:2477` / `62:2634`;
- Filtradas `47:1186`, Repetidas `54:2496` y Antiguas `62:2788`;
- Salud · cargando `69:3307`.

## Paso 1 · Análisis (`feature/health/domain` + `data`)
- **`HealthAnalyzer`** (dominio, Kotlin puro). Por cada elemento legible calcula:
  - **fuerza** con el mismo estimador: débil (0–1), regular (2) o fuerte (3–4);
  - **repetida:** misma contraseña en 2 o más elementos. Se agrupa con SHA-256 **en memoria**; ni el hash ni el grupo se guardan;
  - **antigua:** `password_changed_at` hace más de 365 días;
  - **filtrada:** la cuenta de HIBP es mayor que 0 (del Paso 2).
- **Nivel por elemento (DEC-20):**
  - Crítico: filtrada;
  - Alto: repetida o débil;
  - Medio: regular o antigua;
  - Bajo: el resto.
- **Puntuación global:** `round(100 × promedio(pesos))`, con los pesos Bajo 1,0 · Medio 0,7 · Alto 0,35 · Crítico 0. Si no hay elementos, no hay puntuación y se muestra el estado vacío.
- **Mensajes:**
  - 90 o más: «Excelente»;
  - 70–89: «Bien, pero puedes mejorar»;
  - 50–69: «Necesita atención»;
  - menos de 50: «En riesgo».
- Los elementos dañados o de versión desconocida no cuentan.
- Las contraseñas descifradas existen solo durante el análisis (`Dispatchers.Default`) y se descartan al terminar. El resultado que se queda en memoria no tiene contraseñas: solo ids, nombres, niveles, largos y fechas.

## Paso 2 · HIBP (`feature/health/data`, DEC-21)
- `GET https://api.pwnedpasswords.com/range/{prefijo}`:
  - el prefijo son los 5 primeros caracteres hex en mayúsculas del SHA-1 de la contraseña en UTF-8;
  - encabezados `Add-Padding: true` y `User-Agent: EndSecurity-Android`;
  - se busca el sufijo en memoria, y **las líneas con cuenta 0 son relleno**.
- **Un prefijo por elemento distinto, en serie** (las repetidas comparten consulta). Tiempo de espera de 10 s.
- **Red:**
  - agrega `INTERNET` al manifiesto;
  - en `network_security_config.xml`, `api.pwnedpasswords.com` usa las CA del sistema **sin pinning**;
  - el pinning de la API propia llega al unir el backend.
  - **No uses `ApiTransport`** (es la API propia): un cliente HTTP mínimo aparte, con OkHttp solo si ya está; si no, `HttpURLConnection`.
- **Caché:** `breach_checks`, cifrada con la `vault_key` (`ItemCipher` u otro cifrador con su propia AAD `breach|itemId|userId|v`), guarda la cuenta y `checked_local_seq`. Se vuelve a consultar si el elemento cambió o si la revisión tiene más de 7 días.
- **Cuándo:** al desbloquear, como máximo una vez al día; con «Revisar ahora»; y la primera vez que se abre Salud (SCR-40, «Revisando filtraciones…»).
- **Sin red:** la pantalla muestra lo que haya en caché, y lo no revisado queda «pendiente» sin contar como filtrado. Muestra «Revisado hoy a las 10:30» o «Sin revisar · sin conexión».
- **Nunca se registra en logs** un prefijo, un sufijo ni una cuenta.

## Paso 3 · Pantallas
- **Resumen (SCR-22):**
  - título en dos pesos y el selector fijo «Resumen | Progreso», que no suma pasos al historial (DEC-56);
  - círculo con la puntuación «78 de 100», el mensaje, «Revisado hoy a las 10:30 · Revisar ahora»;
  - cuatro filas: Filtradas, Repetidas, Débiles y Antiguas, con su número.
- **Progreso (SCR-23):**
  - «+N puntos en las últimas 12 semanas · de X a Y», con la gráfica de `health_snapshots` (un punto por semana; el último valor de cada semana);
  - «fortalecidas este mes»: elementos con `password_changed_at` de este mes que hoy son fuertes;
  - «filtradas resueltas»: el máximo de filtradas en las fotos del mes menos las de hoy, sin bajar de 0;
  - «Por fuerza», con la distribución;
  - «Ver ranking de fuerza».
- **Ranking (SCR-24/25):** «Más fuertes | Más débiles», los 10 primeros con su largo y nivel. El pie dice «La fuerza se calcula en tu teléfono…».
- **Filtradas, Repetidas y Antiguas (SCR-26…28)** con los textos de Figma:
  - Repetidas se agrupa por contraseña («Misma contraseña en N sitios»);
  - «Cambiar» o tocar una fila lleva a Editar ese elemento.
- **Foto diaria (OP-45, DEC-55):** después de cada análisis, una fila cifrada en `health_snapshots` por día local, que reemplaza la de hoy. Se borran las de más de 12 meses.
- **Bóveda:** ahora sí van el aviso «N contraseñas necesitan atención» (Crítico + Alto) y los triángulos en las filas de nivel Crítico o Alto. En el detalle va el bloque de salud («Segura · Fuerte, única y sin filtraciones», o el problema más grave).
- **Estados vacíos:** sin contraseñas, «Agrega contraseñas para ver la salud de tu bóveda»; Filtradas, Repetidas o Antiguas vacías, «Ninguna, bien hecho».
- Todo dentro de `RequireUnlocked`, y los ViewModel se vacían al bloquear. La barra inferior con **Salud** activa ya navega.
- **Atribución de HIBP:** una línea pequeña al pie de Filtradas, «Datos de Have I Been Pwned». Lo completo va en «Acerca de» (PEND-12).

## Terminado cuando
- **Pruebas de `HealthAnalyzer`:**
  - cada nivel;
  - la puntuación con casos calculados a mano (incluye 0 y 100);
  - las repetidas en grupos;
  - el límite de 365 días;
  - que dañados y de versión desconocida no cuentan.
- **Pruebas del cliente HIBP** con un servidor falso local (MockWebServer o un `HttpURLConnection` falso):
  - el prefijo correcto;
  - los encabezados;
  - que se ignora el relleno;
  - el tiempo de espera;
  - que nunca se manda el sufijo ni la contraseña.
  - Agrega además un vector conocido: `password` → SHA-1 `5BAA6…`, prefijo `5BAA6`.
- **Pruebas de caché:** se vuelve a revisar con `local_seq` cambiado y a los 7 días.
- **Pruebas de la foto diaria:** una por día y recorte de 12 meses.
- **En el emulador con red:** crea una contraseña «password123» y otra fuerte. «password123» sale **filtrada** y Crítica, y la puntuación baja. Las repetidas y débiles se ven, y en modo avión aparece «sin conexión».
- `security-reviewer` revisa los Pasos 1 y 2. **REQUIERE REVISIÓN HUMANA.**
- `assembleRelease` y lint pasan. Los commits están hechos y subidos.

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
