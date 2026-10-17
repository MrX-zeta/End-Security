# Reporte · Tarea 01 · Arquitectura base y sistema de diseño

**Estado:** hecha
**Fecha:** 2026-10-16

## Qué se hizo
La mayor parte de la tarea ya estaba hecha (y en GitHub) desde la sesión anterior, que siguió el prompt inicial
completo (fases 0, 1 y 2). En esta tarea solo agregué lo que faltaba frente a `siguiente.md`:
- **Paso 1 (ya estaba):** `ui/theme` → `core/designsystem/theme` con `git mv`; compila.
- **Paso 2 (ya estaba):** DEC-37: `allowBackup="false"`, reglas de respaldo y extracción que excluyen todo,
  `usesCleartextTraffic="false"` + `network_security_config`, R8 en release y eliminación de `android.util.Log` en release.
  No se tocó `local.properties` ni archivos de firma.
- **Paso 3 (completado ahora):** tokens `danger`, `danger-container`, `success` y `success-container` en claro y oscuro,
  leídos de las colecciones «Carbón y miel · claro/oscuro» de Figma, y formas del tema (`Shape.kt`).
  Ya estaban los demás tokens (`background`, `surface`, `outline`, `on-surface`, `on-surface-variant`, `primary`,
  `on-primary`, `blob`, sombras) y Montserrat Regular/Medium/SemiBold/Bold con su `OFL.txt`.
- **Paso 4 (ya estaba):** botón principal (`PrimaryButton`), botón de texto (`TextLink`) e indicador de páginas
  (`PageIndicator`) en `core/designsystem/component`.
- **Nuevo:** `DesignSystemCatalog.kt` con un `@Preview` del tema claro y otro del oscuro (tokens, tipografía y los
  tres componentes base).

## Commits
| Hash | Mensaje |
|---|---|
| `ca82968` | feat: tokens danger y success del tema (tarea 01) |
| `a4347c0` | feat: formas del tema con los radios de Figma (tarea 01) |
| `df91dd6` | feat: previews del sistema de diseño en tema claro y oscuro (tarea 01) |

Commits anteriores que ya cubrían esta tarea (ya en GitHub): `e5ab4f3` (paso 1), `75f10cb` y `2ee252d` (paso 2),
`a004278` (dependencias), `6cfddcf` (tema y Montserrat), `25fb666` (componentes).

## Compilación y pruebas (salida real)
- `assembleDebug`: `BUILD SUCCESSFUL in 19s` · 41 actionable tasks: 8 executed, 33 up-to-date. Sin errores ni avisos de Kotlin.
- `testDebugUnitTest`: `BUILD SUCCESSFUL in 9s` · 9 pruebas, 0 fallas
  (`OnboardingViewModelTest` 8/8, `ExampleUnitTest` 1/1).
- En la sesión anterior también compiló `assembleRelease` con R8 (APK sin firmar de 3 MB).

## Diferencias con Figma o con el grafo
- **R8:** la tarea pide `isMinifyEnabled = true`; el proyecto usa `optimization { enable = true }`, que es el DSL que
  genera AGP 9 para lo mismo (minificación, ofuscación y recorte de recursos). Requiere `android.r8.gradual.support=true`
  en `gradle.properties`, que AGP marca como experimental.
- **`danger` en Material:** `danger` y `danger-container` se mapean a `colorScheme.error` y `errorContainer` para que
  los componentes de Material los usen solos. `success` y `success-container` van en los colores extendidos
  (`EndSecurityTheme.extendedColors`) porque Material no tiene ese rol.
- **`on-danger` / `on-success`:** no existen en Figma, así que no los definí; `onError` conserva el valor de Material.
  Hará falta si un botón de peligro lleva texto sobre fondo rojo.
- **Formas:** solo definí los radios que aparecen en Figma (4, 14 y 16); `large` y `extraLarge` conservan los de Material.
- **DEC-50 en el grafo** no lista `danger` ni `success`; los valores salieron de las variables de Figma.

## Dudas o decisiones que tomé
- **Alcance:** la tarea dice que la Fase 2 (onboarding) no es parte de ella y que solo haya tres componentes base.
  El onboarding (SCR-01…SCR-03), DataStore (`onboarding_completed`) y componentes extra (disco neumórfico, manchas,
  botón «Omitir», título en dos pesos) ya existen y están en GitHub desde la sesión anterior. No los deshice;
  la tarea 02 puede revisarlos en lugar de crearlos.
- **«Botón de texto»:** lo interpreté como `TextLink` (enlace subrayado de 48 dp de alto, como «Ya tengo una cuenta»).
  Si en Figma es otro componente, dime el nodo.
- **Archivos que no son míos:** `ai/handoff/README.md` está sin commit y `CLAUDE.md` tiene la sección «Relevo de tareas»
  sin commit. No los incluí en mis commits; súbelos tú.
- **Pendiente de registrar en el grafo:** `onboarding_completed` en KV-DATASTORE, el token `blobGrain`,
  `danger`/`success` en DEC-50, el nombre «End Security» y la arista SCR-03 → SCR-04 «Ya tengo una cuenta».

## Revisión
- [ ] REQUIERE REVISIÓN HUMANA (si tocó `core/crypto`, `core/session`, autenticación o SQL) — no aplica: no se tocaron.
