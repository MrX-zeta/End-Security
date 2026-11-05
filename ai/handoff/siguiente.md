# Tarea 23 · Toques finales: legal, detalles de uso y brechas del backend

**Estado:** hecha
**Escrita:** 2026-11-04 · sesión de diseño

## Revisión de la tarea 22
Aprobada:
- los documentos con resumen y acordeones, y la semántica expandido/contraído;
- `copyLegalDocs` con la copia en `.gitignore`;
- la casilla opcional;
- el interruptor que vuelve a su valor si falla;
- Licencias y Acerca de;
- la suite completa (202, 0 fallas).

Aprobadas tus desviaciones (nombres de terceros en código, `cc-by-4.0.txt` con aviso y enlace, el lugar del interruptor, `displayName` ausente ≠ `null`).

**Figma de la página «Legal»** (no la encontraste): Aviso de privacidad `235:4976`, Términos `236:5003`, Crear cuenta con la casilla opcional `236:5063`. Archivo `K8GenUmR7GcNZAtJRjagER`; si el MCP no cambia de página, usa esos ids directamente.

## Paso 1 · Datos reales (E4)
- El equipo ya completó `docs/legal/*.md`:
  - responsables: Brian Luis Ruiz Pérez y Diego Alberto Zárate Vázquez;
  - canal de privacidad: `endsecurity.privacidad@gmail.com`.
- En `LegalInfo`:
  - `PRIVACY_EMAIL = "endsecurity.privacidad@gmail.com"`;
  - **nuevo** `SUPPORT_EMAIL = "endsecurity.soporte@gmail.com"`.
- **«Reportar un problema de seguridad»** sigue yendo a `PRIVACY_EMAIL`. Agrega en Acerca de una fila **«Soporte»** que abre el correo a `SUPPORT_EMAIL`, con el asunto «Soporte End Security».
- El único marcador que queda es el **proveedor de hosting**: se deja hasta que el backend esté desplegado.
- Prueba: ningún texto que la app muestre contiene `[privacidad@correo.com]` ni `[Nombre completo`. El de hosting sí puede aparecer.

## Paso 2 · Comparar con Figma
- Compara los documentos (con los acordeones abiertos) y Crear cuenta con los ids de arriba.
- Corrige diferencias de estilo: resumen amarillo claro, títulos, viñetas y tarjetas de encargados.

## Paso 3 · `security-reviewer`
Pásale lo que dejaste marcado en la tarea 22:
- `marketingOptIn` en el registro (`RegisterRequest` → `AuthApiImpl`);
- `updateMarketingOptIn`;
- `SecureScreen` del código de correo.

Corrige lo que encuentre.

## Paso 4 · Preparar la unión con el backend real (solo análisis, sin cambiar la red)
El backend de Diego está en https://github.com/Diego-Zarate18/end-security-backend (rama `develop`). **No escribas código del backend.**

Revisión rápida de la sesión de diseño: hoy **no sigue el contrato `docs/api` 1.4.0** en varios puntos:
- prefijo `/api` en lugar de `/v1`;
- rutas distintas: `auth/email/verify/confirm`, `vault/sync`, `auth/email/change/*`;
- respuestas envueltas en `{ success, data, timestamp }`;
- errores sin `application/problem+json`;
- todavía no tiene 2FA, `account`, sesiones, actividad ni syslog.

Clónalo en solo lectura **fuera** de `proyecto-U4` (por ejemplo, en `../end-security-backend`) y escribe `docs/api/brechas-backend.md` con una tabla por endpoint EP-01…EP-26:
- qué pide el contrato;
- qué hace hoy el backend (ruta, cuerpo, respuesta, errores);
- qué falta.

Al final, una lista corta de **decisiones** para el equipo: dónde conviene que el backend se ajuste al contrato y dónde, si algo, conviene cambiar el contrato. **No cambies el contrato ni `ApiTransport`.**

## Paso 5 · Detalles de uso que encontró el equipo en el Redmi
1. **Aviso de la Bóveda** («N contraseñas necesitan atención · Revisar»): «Revisar» se encima con el texto. Usa una `Row` centrada verticalmente con el texto en `weight(1f)` (máximo 2 líneas) y «Revisar» como botón de texto con su propio margen y un área táctil de 48 dp. El texto nunca debe pasar por debajo del botón, ni siquiera con fuente grande (prueba con escala 1.3).
2. **Retraso al volver a la Bóveda desde otra pestaña** (Bóveda → Generador → Bóveda: el regreso tarda). Mídelo primero (Layout Inspector o trazas) y corrige la causa. Lo probable es que la Bóveda se vuelva a crear y a descifrar la lista al regresar. Con la regla del punto 3 (`saveState`/`restoreState`), el ViewModel y la lista ya descifrada se conservan mientras la bóveda siga abierta; si hace falta, el primer cuadro debe usar el último estado. Cuenta en el reporte qué era y cuánto tarda ahora.
3. **Pila de las pestañas.** Hoy Atrás recorre todas las pestañas visitadas al revés. **Regla nueva** (patrón de navegación inferior de Material):
   - cambiar de pestaña **no** suma historial: `popUpTo(startDestination) { saveState = true }`, `launchSingleTop = true` y `restoreState = true`;
   - Atrás desde Salud, Generador o Ajustes vuelve a **Bóveda**;
   - Atrás desde Bóveda **sale de la app**;
   - dentro de una pestaña, Atrás sí recorre su propia pila (por ejemplo, Ajustes → Dispositivos → Atrás → Ajustes).

   Revisa que las rutas con secretos (2FA, códigos) se sigan vaciando al cambiar de pestaña (M3 de la tarea 17 y la hoja de la tarea 18). Prueba de navegación para cada caso.
4. **Barra inferior sin efecto de toque:** quita la `indication` (onda) de los elementos de la barra; la píldora negra de la pestaña activa ya marca la selección. Mantén `Role.Tab`, el estado seleccionado para TalkBack y el área táctil.
5. **Teclado: auditoría de todas las vistas con campos.** En Desbloquear, al abrir el teclado el botón de huella y «Desbloquear» se recortan.
   - **Regla para todas:** el contenido va en un `Column` desplazable con `imePadding()` (o `WindowInsets.ime`), y el botón principal queda **completo y visible** encima del teclado o se alcanza desplazando. Nada puede quedar cortado ni encimado.
   - **En Desbloquear:** el botón de huella va debajo de «Desbloquear» y se desplaza con el contenido. Con el teclado abierto se puede ocultar la huella si no cabe; nunca se ve a medias.
   - **Recorre y captura con el teclado abierto** (emulador de 412×917 y uno pequeño de 360×640):
     - Iniciar sesión, Crear cuenta, Verificar correo, 2FA del acceso, Código de respaldo, Desbloquear;
     - Nueva/Editar contraseña, búsqueda de la Bóveda, Generador;
     - Contraseña maestra, Correo (nuevo y código), los 3 pasos de la 2FA, Eliminar cuenta y la hoja `ReauthSheet` (es una hoja inferior: los campos y el botón deben subir con el teclado).
   - **El reporte lleva una tabla:** vista, si tenía problema y qué se cambió.
6. **Salud sin conexión:** hoy «Revisado ayer a las 22:10 · sin conexión · Revisar ahora» se parte y «Revisar ahora» queda en 4 renglones.
   - La línea solo dice «Revisado hoy/ayer a las HH:MM» (o «Sin revisar»).
   - «Revisar ahora» va como **botón de texto en su propia línea**, debajo.
   - Si al tocarlo no hay conexión: `MessageBar` «Sin conexión. Te mostramos la última revisión.», y lo no revisado sigue como «pendiente».
   - Quita «sin conexión» de la línea.

## Terminado cuando
- Paso 1 con su prueba.
- Paso 2 con capturas comparadas.
- Paso 3 con los hallazgos corregidos.
- Paso 4: `docs/api/brechas-backend.md` completo.
- Paso 5: pruebas de navegación de las pestañas (Atrás y estado conservado), prueba de Compose del aviso de la Bóveda con escala de fuente 1.3 y de Salud sin conexión, la tabla del teclado con capturas y la medición del retraso antes y después.
- `testDebugUnitTest`, lint, `assembleRelease` y la suite completa de `connectedDebugAndroidTest` (salvo E3, en tandas) pasan.
- Commits hechos y subidos (`docs/legal` en `docs:`).

## Al terminar
Llena `ai/handoff/reporte.md`, cambia el estado de arriba a `hecha` y detente.
