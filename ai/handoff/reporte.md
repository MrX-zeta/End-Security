# Reporte · Tarea 39 · Pines SPKI, APK firmado y release v1.0.0

**Estado:** hecha hasta el paso 5 en el árbol de trabajo; commit, push y PR los hace la persona (memoria del proyecto). Falta el paso 6 (tag y release) cuando `main` esté mezclada.

- **Pines (BR-26 resuelto):** una conexión `openssl s_client` a `api.endsecurity.lat`. Cadena: hoja → intermedia YR1 → ISRG Root YR (firmada cruzada por ISRG Root X1). Pin 1 = YR1 `LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=`; pin 2 (respaldo) = Root YR `fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=`; `expiration` 2027-12-31; hoja no fijada; debug sin pines. Prueba `NetworkSecurityConfigTest`. No se probó una conexión real con los pines desde el teléfono.
- **Versión:** ya estaba `1.0.0` / `versionCode 1`; sin cambios.
- **APK:** `releases/EndSecurity-1.0.0.apk` (fuera de git), SHA-256 `21bf5f8abc22b10fdc64d692c91e7610530218af1a310ed74cbb8acd6acec674`. `apksigner verify` OK; certificado `CN=Luis, OU=End-Security`, SHA-256 `b5ba83bb971544b241b47bbfa41bd83a7680bbfbab963106bc8f744830f8a9dd` (no es el de debug).
- **Pruebas:** `testProdDebugUnitTest`, `testDemoDebugUnitTest`, `lintProdDebug`, `assembleProdRelease` en verde. En la primera corrida paralela fallaron 2 pruebas de tiempo de `PasswordSimilarityTest` (carga de la máquina); aisladas pasan.
- **Docs:** README «Instalar», `docs/release/v1.0.0.md`, `estado-actual.md`, `backend-graph.json` (BR-26). `build_backend_graph.py` aún dice «pendiente» para BR-26: si se regenera el grafo, actualizarlo.
- **PR #2** (`integracion/backend` → `development`): sin verificar su estado (no se ejecutó `gh`); el PR `development` → `main` aún no existe.
- **Paso 6 (hecho):** PR #3, #4 y #5 mezclados; tag `v1.0.0` sobre `main` (c9fb103) y release https://github.com/MrX-zeta/End-Security/releases/tag/v1.0.0 (latest) con `EndSecurity-1.0.0.apk`. `v1.0.0-demo` intacto.
