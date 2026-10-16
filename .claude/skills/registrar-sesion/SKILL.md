---
name: registrar-sesion
description: Registra la sesión de trabajo con Claude en ai/sessions/ y actualiza ai/context/estado-actual.md. Úsalo al terminar una sesión de desarrollo.
disable-model-invocation: true
argument-hint: "[tema-corto]"
---

Registra lo que se hizo en esta sesión. Tema: $ARGUMENTS

1. Revisa la conversación de esta sesión y, si hay repositorio git, `git status` y `git diff --stat` para listar los archivos creados o modificados.
2. Crea `ai/sessions/AAAA-MM-DD-<tema-corto>.md` (fecha de hoy; si existe, agrega `-2`, `-3`…) con esta plantilla:

```markdown
# AAAA-MM-DD — <Título>

- **Objetivo:**
- **Herramienta / modelo:** Claude Code (<modelo usado>)
- **Archivos generados o modificados:**
  - `ruta` — qué cambió
- **Decisiones del grafo aplicadas:** DEC-…, OP-…, INV-…
- **Decisiones nuevas o cambios propuestos al grafo:** (o "ninguno")
- **Pruebas ejecutadas y resultado:** (comando y resultado real; "no se ejecutaron" si es el caso)
- **Requiere revisión humana:** sí/no — por qué
- **Revisado por (humano):** _pendiente_   **Fecha:** _pendiente_
- **Hallazgos de la revisión:** _pendiente_
- **Pendientes para la siguiente sesión:**
```

3. Actualiza `ai/context/estado-actual.md`: mueve lo terminado a "Hecho", ajusta "En curso" y "Siguiente". No borres el historial.
4. No marques nada como revisado por un humano: eso lo llena el equipo.
5. Muestra un resumen de 3 a 5 líneas de lo registrado.
