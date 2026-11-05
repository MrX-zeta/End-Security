# Aviso de privacidad integral

**End Security** · Última actualización: 4 de noviembre de 2026

Este aviso explica qué datos personales trata End Security, para qué y cómo puedes ejercer tus derechos, conforme a la Ley Federal de Protección de Datos Personales en Posesión de los Particulares (DOF 20-03-2025).

## Resumen

- Somos [Nombre completo del integrante 1] y [Nombre completo del integrante 2], responsables de tus datos. Contacto: [privacidad@correo.com].
- Tratamos tu correo, un nombre visible opcional y datos técnicos de seguridad (IP, dispositivo y bitácora). Tu bóveda se cifra en tu teléfono: no podemos leerla ni recuperar tu contraseña.
- Usamos tus datos para darte el servicio y protegerlo y, solo si lo aceptas, para enviarte novedades y encuestas.
- No vendemos tus datos ni usamos publicidad o analítica. Resend envía nuestros correos.
- Puedes ejercer tus derechos ARCO desde la app o por correo.

## 1. Identidad y contacto del responsable

- **Responsables:** [Nombre completo del integrante 1] y [Nombre completo del integrante 2], personas físicas desarrolladoras de End Security, proyecto académico de la Universidad Politécnica de Chiapas.
- **Domicilio de contacto:** Carretera Tuxtla Gutiérrez – Portillo Zaragoza Km. 21+500, Colonia Las Brisas, Suchiapa, Chiapas, C.P. 29145, México.
- **Canal de privacidad:** [privacidad@correo.com]. Este correo se usa exclusivamente para dudas y solicitudes sobre el manejo de tus datos personales.

## 2. Datos personales que recabamos

**Datos de cuenta**
- Correo electrónico y, si lo escribes, un nombre visible.
- **Contraseña maestra: nunca la recibimos ni la guardamos.** Tu teléfono deriva de ella una clave de autenticación; el servidor almacena solo un hash de esa clave calculado con el algoritmo Argon2id y una sal aleatoria. Por eso nadie, ni nosotros, puede conocer ni recuperar tu contraseña.
- Si activas la verificación en dos pasos: la clave TOTP (cifrada con una llave del servidor) y tus códigos de respaldo (guardados como hash HMAC).

**Contenido de tu bóveda**
- Las contraseñas y notas que guardas se cifran en tu teléfono con AES-256-GCM antes de enviarse. En el servidor solo existe información cifrada que no podemos leer. Sí vemos datos técnicos sin contenido: cuántos elementos tienes, su tamaño y la fecha en que cambiaron.

**Datos de uso y aprendizaje**
- End Security no es una plataforma educativa: no recabamos historial de módulos, respuestas a cuestionarios, puntuaciones, rutas ni métricas de aprendizaje.
- El análisis de salud de tus contraseñas (fortaleza, repetidas, antiguas, filtradas, puntuación e historial) se calcula y se guarda cifrado **solo en tu teléfono**; no lo recibimos.

**Datos técnicos y registros de acceso (logs)**
- Dirección IP y agente de usuario (versión de la app, modelo del teléfono y versión de Android) de cada evento de seguridad.
- Nombre del dispositivo y fechas de cada sesión abierta.
- Bitácora de eventos de tu cuenta: inicios de sesión, intentos fallidos y cambios de seguridad.
- Registros operativos del servidor (syslog) con la IP truncada.
- La app no usa navegador ni cookies (sección 6).

**Datos de pago**
- No aplica. End Security es gratuita: no procesa pagos ni almacena datos de tarjetas.

**Datos sensibles**
- No te pedimos datos sensibles. Lo que guardes en tu bóveda está cifrado y no podemos verlo.

## 3. Finalidades del tratamiento

**Finalidades primarias (necesarias para el servicio)**
1. Registrarte, verificar tu correo y autenticarte, incluida la verificación en dos pasos.
2. Guardar tu bóveda cifrada y sincronizarla entre tus dispositivos.
3. Mostrarte tus dispositivos y la actividad de tu cuenta, y permitirte cerrar sesiones.
4. Enviarte correos de verificación y alertas de seguridad (inicio de sesión nuevo, cambio de contraseña o de correo, verificación en dos pasos desactivada, eliminación de la cuenta).
5. Soporte técnico, resolución de fallas y seguridad del sistema: límites de intentos, detección de accesos sospechosos y respuesta a incidentes.
6. Atender tus solicitudes de derechos ARCO.

End Security no emite constancias ni certificados.

**Finalidades secundarias (opcionales)**
1. Enviarte avisos sobre novedades y actualizaciones de End Security.
2. Invitarte a encuestas de satisfacción y medir, de forma agregada, el uso de la app.

**Mecanismo de rechazo:** al crear tu cuenta aparece la casilla «Quiero recibir novedades y encuestas de End Security», **desmarcada por defecto**. Si no la marcas, no usaremos tus datos para estas finalidades. Puedes retirar tu consentimiento en cualquier momento escribiendo al canal de privacidad. Negarte no afecta el servicio.

No usamos tus datos para publicidad ni perfilamiento, y no los vendemos.

## 4. Transferencias de datos a terceros

No transferimos tus datos a terceros para que los usen con fines propios. Solo compartimos lo indispensable con proveedores que actúan como **encargados**, por nuestra cuenta y bajo nuestras instrucciones:

| Encargado | Qué hace | Datos que recibe |
|---|---|---|
| **Infraestructura y almacenamiento:** [proveedor de hosting y país del servidor] | Aloja el servidor, la base de datos y los registros | Los de la sección 2, con la bóveda siempre cifrada |
| **Correo transaccional:** Resend (Estados Unidos) | Envía los correos de verificación y alertas | Tu correo y el contenido de esos mensajes |
| **Analítica web** | No usamos Google Analytics, PostHog ni herramientas similares | Ninguno |

**Revisión de filtraciones (Have I Been Pwned):** para saber si una contraseña apareció en filtraciones, tu teléfono consulta directamente el servicio Pwned Passwords enviando solo los 5 primeros caracteres del hash SHA-1 de la contraseña (modelo de k-anonimato). Nunca se envía la contraseña ni el hash completo, ni tu correo. Ese servicio ve la IP de tu teléfono.

Solo comunicaremos datos a una autoridad cuando una ley o una orden fundada lo exija.

## 5. Derechos ARCO y cómo ejercerlos

- **Acceso:** saber qué datos personales tuyos tenemos.
- **Rectificación:** corregir datos incompletos o inexactos.
- **Cancelación:** pedir que eliminemos tu cuenta y tus datos.
- **Oposición:** oponerte a que usemos tus datos para fines específicos, como las finalidades secundarias.

También puedes revocar tu consentimiento y limitar el uso de tus datos.

**Desde la app** puedes hacerlo directamente: ver tu correo, dispositivos y actividad (Ajustes), cambiar tu correo (rectificación) y eliminar tu cuenta (Ajustes › Eliminar cuenta). Al eliminarla borramos de inmediato tu bóveda cifrada, tu verificación en dos pasos y tus sesiones; a los 30 días anonimizamos lo restante.

**Procedimiento por correo:**
1. Escribe a [privacidad@correo.com] **desde el correo de tu cuenta**, indicando tu nombre, el derecho que quieres ejercer y una descripción clara de lo que pides.
2. **Validación de identidad:** como no podemos conocer tu contraseña, comprobamos que la solicitud viene del correo de tu cuenta y te enviamos un código para confirmarla. Si actúas por medio de un representante, este deberá acreditarlo con identificación oficial y carta poder.
3. **Plazos:** te responderemos en un máximo de **20 días hábiles** desde que recibamos tu solicitud y, si procede, la haremos efectiva dentro de los **15 días hábiles** siguientes. El trámite es gratuito.

No podemos entregarte el contenido de tu bóveda en texto legible porque está cifrado con tu contraseña; puedes consultarlo en la app.

Si consideras que tu derecho no fue atendido, puedes acudir a la Secretaría Anticorrupción y Buen Gobierno, autoridad en protección de datos personales.

## 6. Uso de cookies y tecnologías de rastreo

- End Security es una aplicación nativa de Android: **no usa cookies**, ni cookies analíticas o de terceros, ni píxeles, rastreadores o identificadores publicitarios.
- **Estrictamente necesario:** para mantener tu sesión, la app guarda en tu teléfono tokens de sesión cifrados con Android Keystore y tus preferencias (bloqueo automático, opciones del generador). Se borran al cerrar sesión o eliminar tu cuenta.
- **Cómo borrarlos:** cierra sesión en la app, o ve a Ajustes del teléfono › Aplicaciones › End Security › Almacenamiento › Borrar datos. Como no hay rastreo de terceros, no hay nada que desactivar en un navegador.

## 7. Medidas de seguridad

- **Técnicas:** cifrado de extremo a extremo con conocimiento cero (AES-256-GCM y Argon2id), comunicación cifrada con HTTPS/TLS 1.3 y fijación de certificado, llaves protegidas por Android Keystore, verificación en dos pasos, control de acceso autenticado con sesiones de corta duración, límites de intentos, bitácora protegida contra alteraciones y respaldos periódicos de la base de datos.
- **Administrativas:** acceso al servidor solo para el equipo responsable, secretos fuera del código fuente y un plan de respuesta a incidentes que incluye avisarte sin demora si una vulneración afecta tus datos.
- **Conservación:** las IP y los registros operativos se borran a los 90 días; los datos de una cuenta eliminada se anonimizan a los 30 días.

## 8. Tratamiento de datos de menores de edad

End Security está dirigida a personas mayores de 18 años. Si eres menor de edad, solo puedes usarla con el consentimiento previo de tu madre, padre o tutor, quien deberá aceptar este aviso. Si sabemos de una cuenta de un menor sin ese consentimiento, la eliminaremos.

## 9. Modificaciones al aviso de privacidad

Publicaremos cualquier cambio en la app (Ajustes › Legal y ayuda) con su fecha de actualización. Si el cambio es importante, como una finalidad o un encargado nuevos, te avisaremos con un aviso dentro de la app al abrirla y por correo antes de aplicarlo. Si una finalidad nueva requiere tu consentimiento, te lo pediremos.
