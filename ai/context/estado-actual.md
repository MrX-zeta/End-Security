# Estado actual — End Security

> Claude lo lee al empezar cada sesión y lo actualiza con `/registrar-sesion`.
> Última actualización: 2026-11-18 (tarea 33)

## Hecho
- App completa con backend falso (release `v1.0.0-demo`): registro, acceso, 2FA, Bóveda, Generador, Salud, Ajustes, dispositivos, actividad, cuenta, legal, conflictos y sin conexión (tareas 01 a 31).
- Grafo de conocimiento v1.10.0 (`ai/context/knowledge-graph.json`): decisiones DEC-01…66, 48 pantallas con su navegación (SCR-47 y SCR-48 son el cambio de correo) y 26 endpoints. Grafo del backend v1.1.0 (`ai/context/backend-graph.json`, brechas BR-01…29).
- **Integración con el backend real (DEC-65), tareas 32 y 33**, probada por la persona en el teléfono con el backend local:
  - Backend, rama `integracion/app` (PR Diego-Zarate18/end-security-backend#1, sin merge): BR-06, 07, 16, 17, 18, 22, 23, 25 y 27 resueltas, más el tope de reenvíos del código de registro (BR-28) y el reemplazo de cuentas sin verificar de más de 24 h (BR-29). Jest (38 pruebas) y la prueba de punta a punta `scripts/e2e/flujo-completo.mjs` (12 pasos).
  - App, rama `integracion/backend` (PR MrX-zeta/End-Security#2, sin merge): adaptador de la variante `prod` (`BackendTranslator`, `BackendTransport`; spec en `specs/001-adaptador-backend-real/`) y los ajustes de BR-10 (códigos de respaldo 5-5), BR-20 (reautenticación antes de la clave de 2FA) y BR-21 (catálogo de actividad).
  - «Más débiles» (Salud · Ranking) ya no incluye contraseñas fuertes y tiene estado vacío (tarea 33).
- Cómo probar con el backend local: sección «Probar con el backend local» del `README.md`.

## Decisiones recientes
- DEC-65 integración con el backend real · DEC-66 Spec Kit para funcionalidades grandes y `/ponytail-review` antes de cada PR · DEC-64 anonimización inmediata al eliminar.
- Riesgos aceptados en la revisión de la tarea 32: RSG-16 (un tercero puede bloquear una cuenta con 5 intentos, BR-22) y RSG-17 (el cambio de correo revela por tiempo si el correo nuevo existe, con Resend, hasta tener dominio).

## Siguiente
1. El equipo revisa y fusiona los dos PR (nadie fusiona sin revisión humana: auth, red y SQL).
2. Abiertas en el backend-graph: BR-19 (aviso de privacidad vs. anonimización inmediata), BR-24 (dominio de correo en Resend) y BR-26 (URL pública HTTPS y los dos pines SPKI; hoy `API_BASE_URL` de release está vacía).
3. Figma: la hoja de contraseña antes de la clave de 2FA, el código de respaldo `xxxxx-xxxxx` en `prod`, y las pantallas de cambio de correo (SCR-47 y SCR-48 aún sin nodo).
4. Pendientes humanos en `ai/handoff/revisiones-pendientes.md`.

## Pendientes abiertos del grafo
PEND-02 recuperación de contraseña maestra · PEND-04 tiempos de tokens/autobloqueo · PEND-05 plazos de retención · PEND-06 ORM · PEND-10 idioma del código
