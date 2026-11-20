# Estado actual — End Security

> Claude lo lee al empezar cada sesión y lo actualiza con `/registrar-sesion`.
> Última actualización: 2026-11-20 (tarea 40)

## Hecho
- App completa con backend falso (release `v1.0.0-demo`): registro, acceso, 2FA, Bóveda, Generador, Salud, Ajustes, dispositivos, actividad, cuenta, legal, conflictos y sin conexión (tareas 01 a 31).
- Grafo de conocimiento v1.10.0 (`ai/context/knowledge-graph.json`): decisiones DEC-01…66, 48 pantallas con su navegación (SCR-47 y SCR-48 son el cambio de correo) y 26 endpoints. Grafo del backend v1.1.0 (`ai/context/backend-graph.json`, brechas BR-01…29).
- **Integración con el backend real (DEC-65), tareas 32 y 33**, probada por la persona en el teléfono con el backend local:
  - Backend, rama `integracion/app` (PR Diego-Zarate18/end-security-backend#1, sin merge): BR-06, 07, 16, 17, 18, 22, 23, 25 y 27 resueltas, más el tope de reenvíos del código de registro (BR-28) y el reemplazo de cuentas sin verificar de más de 24 h (BR-29). Jest (38 pruebas) y la prueba de punta a punta `scripts/e2e/flujo-completo.mjs` (12 pasos).
  - App, rama `integracion/backend` (PR MrX-zeta/End-Security#2, sin merge): adaptador de la variante `prod` (`BackendTranslator`, `BackendTransport`; spec en `specs/001-adaptador-backend-real/`) y los ajustes de BR-10 (códigos de respaldo 5-5), BR-20 (reautenticación antes de la clave de 2FA) y BR-21 (catálogo de actividad).
  - «Más débiles» (Salud · Ranking) ya no incluye contraseñas fuertes y tiene estado vacío (tarea 33).
- **Preparación de producción (tarea 36, DEC-67):** dominio `endsecurity.lat`, servidor en `api.endsecurity.lat`, correo con Resend desde `no-reply@endsecurity.lat`. El backend (`integracion/app`) se niega a arrancar en producción sin `RESEND_API_KEY`/`EMAIL_FROM`, con secretos débiles o con `CORS_ORIGIN=*`, y tiene `GET /api/health` mínimo, `npm run migrate:deploy`, `railway.json` y la guía `docs/despliegue.md`. La app de release ya apunta a `https://api.endsecurity.lat/api/`. Nada está desplegado todavía.
- **Hallazgos de la auditoría (tareas 37 y 38):** F-03 (ValidationPipe sin conversión implícita), F-05 (límites de longitud en el formulario de la bóveda, `ItemLimits`) y F-06 (multer actualizado) cerrados; F-07 ya existía (BR-22). Aceptados: RSG-18, RSG-19, RSG-20. F-01 (pines SPKI) cerrado en la tarea 39 (BR-26).
- **Tarea 39, release 1.0.0:** pines SPKI de `api.endsecurity.lat` (intermedia YR1 + raíz ISRG Root YR de respaldo, vencen 2027-12-31), APK `prod` firmado (`releases/EndSecurity-1.0.0.apk`, fuera de git, SHA-256 `21bf5f8a…c674`), notas en `docs/release/v1.0.0.md`. Falta: merge PR #2 y `development`→`main` (la persona), luego tag y release `v1.0.0`.
- **Tarea 40, release 1.0.1:** aviso de privacidad con el hosting (Railway, US East), versión 1.0.1 (código 2), APK firmado con el mismo certificado (`releases/EndSecurity-1.0.1.apk`, SHA-256 `71d3a9f1…dbddb`), y compilación de capturas `-PsinFlagSecure=true` solo en debug (`releases/capturas/`, no se distribuye). Falta: PR a `main` (la persona), luego tag y release `v1.0.1`.
- Cómo probar con el backend local: sección «Probar con el backend local» del `README.md`.

## Decisiones recientes
- DEC-65 integración con el backend real · DEC-66 Spec Kit para funcionalidades grandes y `/ponytail-review` antes de cada PR · DEC-64 anonimización inmediata al eliminar.
- Riesgos aceptados en la revisión de la tarea 32: RSG-16 (un tercero puede bloquear una cuenta con 5 intentos, BR-22) y RSG-17 (el cambio de correo revela por tiempo si el correo nuevo existe, con Resend, hasta tener dominio).

## Siguiente
1. El equipo revisa y fusiona los dos PR (nadie fusiona sin revisión humana: auth, red y SQL).
2. Desplegar con `docs/despliegue.md` (la persona o Diego): proyecto y base en Railway, variables, dominio `api.endsecurity.lat` en Spaceship, dominio verificado y clave en Resend. Abiertas: BR-19 (aviso de privacidad vs. anonimización inmediata; hosting resuelto: Railway US East, E4), BR-24 (en curso: verificar el dominio en Resend). BR-26 resuelta (tarea 39).
3. Figma: la hoja de contraseña antes de la clave de 2FA, el código de respaldo `xxxxx-xxxxx` en `prod`, y las pantallas de cambio de correo (SCR-47 y SCR-48 aún sin nodo).
4. Pendientes humanos en `ai/handoff/revisiones-pendientes.md`.

## Pendientes abiertos del grafo
PEND-02 recuperación de contraseña maestra · PEND-04 tiempos de tokens/autobloqueo · PEND-05 plazos de retención · PEND-06 ORM · PEND-10 idioma del código
