# Feature Specification: Adaptador de la variante prod al backend real

**Feature Branch**: `integracion/backend` (la carpeta de la especificación es `001-adaptador-backend-real`)

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Adaptador prod al backend real (tarea 32, fase 5, DEC-65). La variante prod de la app habla con el backend NestJS real a través de las mismas interfaces de dominio (AuthApi, VaultApi, AccountApi, SessionApi); la variante demo no cambia. El adaptador traduce las brechas BR-01..05, 08..15, 20 y 21 […]. Pruebas unitarias del adaptador con respuestas reales del backend. El contrato real es el código del backend; desarrollo guiado por contrato."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear una cuenta y entrar contra el servidor real (Priority: P1)

Una persona instala la variante de producción, crea su cuenta, escribe el código que le llegó por correo y queda dentro
de su bóveda. Más tarde cierra sesión y vuelve a entrar con su correo y su contraseña maestra.

**Why this priority**: sin registro, verificación y acceso no hay nada más que probar; es la puerta de toda la integración.

**Independent Test**: con el servidor local corriendo, registrar una cuenta, leer el código de la consola del servidor,
verificarlo, cerrar sesión e iniciarla de nuevo. La bóveda abre con la misma contraseña maestra.

**Acceptance Scenarios**:

1. **Given** un correo sin cuenta, **When** la persona se registra, **Then** el servidor guarda la cuenta con el mismo
   identificador que generó el teléfono y la app pide el código de verificación.
2. **Given** el código correcto, **When** lo escribe, **Then** entra a la bóveda sin volver a escribir la contraseña.
3. **Given** una cuenta verificada, **When** inicia sesión con la contraseña correcta, **Then** entra; con una incorrecta,
   ve «correo o contraseña incorrectos», el mismo mensaje que para un correo que no existe.
4. **Given** una cuenta sin verificar, **When** inicia sesión con la contraseña correcta, **Then** la app la lleva a la
   pantalla del código, y el código que escriba ahí sirve.
5. **Given** una cuenta con verificación en dos pasos, **When** inicia sesión, **Then** la app pide el código de 6 dígitos
   o un código de respaldo, y con cualquiera de los dos entra.

---

### User Story 2 - Guardar, editar y borrar contraseñas sincronizadas (Priority: P1)

La persona crea, edita y borra elementos de su bóveda; el servidor guarda solo bloques cifrados y otro teléfono con la
misma cuenta ve los mismos cambios. Si dos teléfonos editan el mismo elemento, la app muestra el conflicto y deja elegir.

**Why this priority**: es el uso principal del gestor; sin sincronización la variante de producción no aporta nada.

**Independent Test**: crear, editar y borrar un elemento; desde un segundo cliente editar el mismo elemento y comprobar
que el primero recibe un conflicto con la versión del servidor.

**Acceptance Scenarios**:

1. **Given** una bóveda vacía, **When** se guarda un elemento, **Then** el servidor lo acepta y la app lo marca como sincronizado.
2. **Given** un elemento que otro teléfono ya cambió, **When** se sube la edición local, **Then** la app marca el conflicto y
   puede mostrar la versión del servidor para elegir.
3. **Given** un elemento borrado, **When** otro teléfono sincroniza, **Then** el elemento desaparece también allí.
4. **Given** que el servidor pide descargar todo de nuevo, **When** la app sincroniza, **Then** reemplaza su copia sin perder
   los cambios locales pendientes.

---

### User Story 3 - Administrar la cuenta (Priority: P2)

La persona activa y desactiva la verificación en dos pasos, regenera sus códigos de respaldo, cambia su correo y su
contraseña maestra, cambia el consentimiento de novedades, revisa sus sesiones y su actividad, cierra otras sesiones y
elimina su cuenta.

**Why this priority**: son flujos de seguridad que el proyecto debe demostrar, pero dependen de las historias 1 y 2.

**Independent Test**: recorrer cada flujo de Ajustes contra el servidor local y comprobar el resultado en la app y en el
servidor.

**Acceptance Scenarios**:

1. **Given** la verificación en dos pasos apagada, **When** la persona la activa, **Then** la app primero pide la contraseña
   maestra en la hoja que ya existe, después muestra la clave y al final los 10 códigos de respaldo tal como los entrega el
   servidor.
2. **Given** un código de respaldo del servidor, **When** la persona lo escribe para entrar, **Then** el campo lo acepta tal
   como se mostró (10 caracteres en dos grupos de 5).
3. **Given** que otro teléfono ya cambió la contraseña, **When** esta persona intenta cambiarla, **Then** la app lo detecta y
   le pide iniciar sesión de nuevo, sin llamarlo «contraseña incorrecta».
4. **Given** el historial de actividad, **When** la persona lo abre, **Then** ve solo los eventos que la app sabe nombrar,
   sin direcciones IP.
5. **Given** que otra sesión cerró la de este teléfono, **When** la app hace su siguiente petición, **Then** intenta renovar
   una vez y, si no puede, cierra la sesión local.

---

### User Story 4 - La variante de demostración no cambia (Priority: P1)

Quien instala la variante demo sigue usando el servidor simulado del teléfono, con el «Modo demostración», sin red.

**Why this priority**: es la versión publicada (v1.0.0-demo); romperla sería un retroceso.

**Independent Test**: las pruebas existentes de la variante demo pasan sin cambios en su comportamiento.

**Acceptance Scenarios**:

1. **Given** la variante demo, **When** se usa cualquier flujo, **Then** responde igual que antes de esta funcionalidad,
   salvo que la activación de dos pasos también pide la contraseña maestra (mismo flujo en ambas variantes).

---

### Edge Cases

- El teléfono se cierra entre el registro y la verificación: el código ya no se puede confirmar sin volver a iniciar
  sesión; la app lo trata como desafío vencido y el inicio de sesión vuelve a la pantalla del código.
- El servidor no responde, tarda o devuelve algo que no se entiende: la app lo trata como «sin conexión» o error genérico
  y nunca muestra el texto del servidor.
- Límite de intentos del servidor: la app muestra la espera que indica el servidor (o 60 s si no la indica).
- Eventos de actividad que la app no conoce: se ocultan.
- El servidor pide descarga completa de la bóveda con un cursor viejo: la app hace la descarga completa.
- La URL del servidor de producción todavía no existe: la variante prod de release queda sin servidor (sin conexión).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La variante prod MUST comunicarse con el servidor real usando las mismas interfaces de dominio que la
  variante demo; ninguna pantalla ni caso de uso distingue entre variantes, salvo el formato de los códigos de respaldo.
- **FR-002**: El adaptador MUST traducir rutas, envoltura de respuestas, nombres de campos y codificación de bytes entre
  el contrato de la app y el servidor real (BR-01, BR-02, BR-04, BR-05).
- **FR-003**: El adaptador MUST convertir cada error del servidor en uno de los códigos que la app ya maneja, y nunca usar
  el texto del servidor como mensaje (BR-03).
- **FR-004**: El registro MUST enviar el identificador que generó el teléfono y responder a la app lo mismo exista o no el
  correo (BR-06 ya resuelto en el servidor).
- **FR-005**: El adaptador MUST guardar en memoria el desafío del registro o del inicio de sesión para poder confirmar o
  reenviar el código del correo; nunca en disco ni en registros (BR-08).
- **FR-006**: El inicio de sesión MUST distinguir las tres respuestas del servidor: acceso, segundo paso o correo sin
  verificar (BR-09).
- **FR-007**: Un rechazo de autorización en una petición con sesión MUST provocar una sola renovación; si la renovación
  también falla, la sesión local se cierra (BR-11).
- **FR-008**: La descarga de la bóveda MUST tratar la orden de «descarga completa» del servidor como cursor vencido (BR-12).
- **FR-009**: Cada subida de un elemento MUST viajar sola, de modo que un conflicto siempre sea de un elemento (BR-13).
- **FR-010**: Para mostrar la versión del servidor en un conflicto, la app MUST obtenerla de la descarga de cambios
  (BR-14), incluidos los elementos borrados.
- **FR-011**: La consulta de la cuenta MUST obtener la versión de llaves de la descarga de la bóveda (BR-15).
- **FR-012**: La app MUST mostrar y aceptar los códigos de respaldo del servidor tal como llegan (BR-10) en la variante
  prod, y seguir con los suyos en demo.
- **FR-013**: Activar la verificación en dos pasos MUST pedir la contraseña maestra con la hoja de reautenticación que ya
  existe, y tomar los datos de la clave del enlace `otpauth` del servidor (BR-20).
- **FR-014**: La actividad MUST mostrar solo los eventos del servidor que la app sabe nombrar, sin IP ni agente (BR-21).
- **FR-015**: La dirección del servidor MUST configurarse por variante y tipo de compilación: en desarrollo, el servidor
  local por el puerto reenviado; en release, vacía hasta tener la de producción (BR-26).
- **FR-016**: El tráfico sin cifrar MUST permitirse solo en desarrollo y solo hacia 127.0.0.1 y 10.0.2.2; release solo HTTPS.
- **FR-017**: El adaptador MUST tener pruebas unitarias con respuestas copiadas del servidor real, y la variante demo
  conserva sus pruebas.

### Key Entities

- **Desafío pendiente**: token temporal del servidor ligado a un correo; vive solo en memoria mientras se confirma el código
  o el segundo paso.
- **Llaves conocidas**: parámetros KDF, llave de bóveda protegida y versión de llaves recibidos al entrar; el adaptador los
  recuerda en memoria para responder la consulta de la cuenta.
- **Cursor de la bóveda**: última revisión que el servidor devolvió; marca qué cambios faltan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los 10 pasos de la prueba de punta a punta (registro, verificación, acceso, crear/editar/borrar, conflicto,
  dos pasos con código y con respaldo, cambio de contraseña, cambio de correo, cierre de sesiones y eliminación) terminan
  bien contra el servidor local.
- **SC-002**: El 100 % de las respuestas de error que el servidor puede dar en esos flujos se convierten en un código que la
  app conoce; ninguna llega como texto del servidor.
- **SC-003**: Las pruebas de la variante demo pasan sin cambios de comportamiento.
- **SC-004**: El APK de release no permite tráfico sin cifrar.

## Assumptions

- El servidor es la rama `integracion/app` del backend, con BR-06, 07, 16, 17, 18, 22, 23 y 25 ya resueltos (fase 4).
- La renovación del refresh token vence a los 7 días desde la última renovación, como el valor por defecto del servidor.
- El nombre del dispositivo para el registro es el modelo del teléfono, igual que en el inicio de sesión.
- El desafío pendiente no sobrevive a cerrar la app; el inicio de sesión genera uno nuevo (BR-08).
- La URL pública, los pines SPKI (BR-26) y el correo real (BR-24) quedan fuera.
