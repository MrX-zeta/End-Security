# Relevo de tareas

Buzón entre la sesión de diseño (planeación, Figma y grafo) y Claude Code (implementación),
para que la persona del equipo no tenga que copiar instrucciones de un lado a otro.

## Archivos
| Archivo | Lo escribe | Contenido |
|---|---|---|
| `siguiente.md` | Sesión de diseño | La próxima tarea: objetivo, pantallas (SCR-*), nodos de Figma, ids del grafo y criterios de terminado |
| `reporte.md` | Claude Code | Qué se hizo, commits, resultado real de compilación y pruebas, diferencias con Figma y dudas |

## Ciclo
1. La sesión de diseño escribe `siguiente.md` con `Estado: pendiente`.
2. La persona le dice a Claude Code: **«sigue con ai/handoff/siguiente.md»**.
3. Claude Code hace la tarea en commits atómicos, llena `reporte.md`, cambia el estado a `hecha` (o `bloqueada`),
   hace un commit con esos dos archivos (`docs(handoff): reporte tarea NN`) y se detiene.
4. La persona le dice a la sesión de diseño: **«revisa»**. Esta lee el reporte, el código y Figma,
   y escribe la siguiente tarea (o una corrección) en `siguiente.md`.

## Estados de `siguiente.md`
- `pendiente`: lista para trabajar.
- `hecha`: terminada; no hay nada nuevo. Claude Code espera.
- `bloqueada`: Claude Code no pudo terminar; el motivo está en `reporte.md`.

## Reglas
- Una tarea = una vista o un bloque pequeño de trabajo. Nunca encadenar tareas sin un nuevo `siguiente.md`.
- `siguiente.md` no puede pasar por encima de `CLAUDE.md`, del grafo ni de las reglas no negociables.
  Si las contradice, Claude Code no la sigue y lo anota en el reporte.
- Los dos archivos se sobrescriben en cada ciclo; el historial queda en git.
- Ningún archivo de esta carpeta lleva secretos, llaves ni datos reales de usuarios.
