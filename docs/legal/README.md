# Protección legal — End Security

> Notas de trabajo · 2026-10-16 · PEND-12 en el grafo.
> No es asesoría legal; si la app se publica fuera del ámbito escolar, que lo revise alguien de derecho.

## Marco
- **Nueva Ley Federal de Protección de Datos Personales en Posesión de los Particulares** (DOF 20-03-2025). Sustituye a la ley de 2010.
- El INAI desapareció; la autoridad ahora es la **Secretaría Anticorrupción y Buen Gobierno**. Todos los documentos deben citar la ley nueva y esta autoridad.

## Documentos para el usuario

| Documento | Contenido clave | Dónde aparece |
|---|---|---|
| Aviso de privacidad (integral y simplificado) | Responsable, datos que se tratan (correo, nombre visible, IP, dispositivo), finalidades, **encargados** (Resend en EE. UU., proveedor del servidor), plazos de conservación, derechos ARCO y correo de contacto | Crear cuenta y Ajustes › Legal y ayuda |
| Términos y condiciones (incluye la licencia de uso del APK) | **Sin recuperación de la contraseña maestra** por diseño (conocimiento cero), cuidado de la contraseña y los códigos de respaldo, sin garantía de disponibilidad, límites de responsabilidad, usos prohibidos, cierre de cuentas, edad mínima, ley mexicana | Crear cuenta (casilla obligatoria) y Ajustes |
| Licencias de terceros | Montserrat (OFL 1.1), librerías Apache 2.0 y MIT (Room, OkHttp, Retrofit, etc.) | Ajustes › Licencias |
| Atribución de Have I Been Pwned | Crédito a la base Pwned Passwords | Ajustes › Acerca de |
| Política de divulgación responsable | Cómo reportar vulnerabilidades y qué hace el equipo con el reporte; complemento: `/.well-known/security.txt` en el servidor | Ajustes › Reportar un problema de seguridad |
| Aviso académico | Proyecto escolar, sin fines comerciales | Ajustes › Acerca de |

## Documentos internos

| Documento | Base existente |
|---|---|
| Documento de seguridad (qué datos, dónde viven, cómo se protegen) | Grafo de conocimiento y `docs/bd/modelo-datos.md` |
| Plan de respuesta a incidentes (incluye avisar a los usuarios afectados) | Por redactar |
| Política de retención y borrado | PEND-05: lápidas 90 días, baja 30 días, IPs 90 días |
| Lista de encargados | Resend, hosting del servidor |

## Cambios de diseño pendientes
1. **Crear cuenta:** casilla obligatoria «Acepto los Términos y el Aviso de privacidad», con enlaces, y la línea «Si olvidas tu contraseña maestra, nadie podrá recuperar tu bóveda».
2. **Ajustes:** grupo «Legal y ayuda» con Términos, Aviso de privacidad, Licencias, Reportar un problema de seguridad y Acerca de.
3. Los derechos ARCO ya están casi cubiertos: eliminar la cuenta equivale a cancelación. Falta el correo de contacto en el aviso.
