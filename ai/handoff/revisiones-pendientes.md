# Revisiones humanas pendientes

> La lleva la sesión de diseño. Se repasa **al terminar el frontend**, antes de unir el backend real.
> La sesión de diseño ya revisó el código de cada tarea en GitHub. Esta lista es lo que **una persona del equipo** debe probar o leer.

## Pruebas manuales en un teléfono real
| # | Qué | Origen | Hecho |
|---|---|---|---|
| M1 | TalkBack en el campo de contraseña: dice «N caracteres», deja escribir y no lee el texto | Tarea 11 | ☐ |
| M2 | Conflicto a mano: editar, comando `adb` de `docs/api/backend-falso.md`, volver → aviso → resolver | Tarea 12 | ☐ |
| M3 | Splash en Android 9–11 (si consiguen un teléfono o emulador con API 28–30) | Tarea 10.1 | ☐ |
| M4 | 2FA con app autenticadora y código de respaldo (`+2fa`) | Tarea 10 | ☐ |
| M5 | Registro, desbloqueo y cierre de sesión en el Redmi | Tarea 10 | ✔ (cuenta creada el 2026-10-22) |
| M6 | Generador → «Guardar en la bóveda» → guardar → aparece en la lista | Tarea 13 | ✔ (2026-10-28) |
| M7 | En Nueva contraseña, el dado «Generar» abre la hoja y «Usar esta contraseña» llena el campo | Tarea 13 | ✔ (2026-10-28) |
| M8 | Salud: crear «password123» y una fuerte → «password123» sale filtrada y Crítica, la puntuación baja; repetidas y débiles se ven | Tarea 14 | ☐ |
| M9 | Salud en modo avión → «Sin revisar · sin conexión», lo no revisado queda pendiente | Tarea 14 | ☐ |
| M10 | Formularios con errores: tocar el botón con campos mal → salta al primer error, se abre el teclado y TalkBack lee el mensaje (Crear cuenta, Login, Desbloquear, códigos, Nueva/Editar) | Tarea 15 | ☐ |
| M11 | Huella en el Redmi: activar, bloquear con el candado, desbloquear con huella; registrar una huella nueva → pide la contraseña y avisa | Tarea 16 | ☐ |
| M12 | Ajustes en el Redmi: cambiar el bloqueo a «Al salir» y comprobar que bloquea; cerrar sesión con y sin cambios pendientes (modo avión) | Tarea 16 | ☐ |
| M13 | Desbloquear en el Redmi (candado, autobloqueo y al abrir la app): se ve la pantalla completa y la huella solo se pide al tocar su botón | Tarea 18 | ☐ |
| M14 | Cambiar la contraseña maestra con la huella activa: pide la huella para seguir usándola; desbloquear con la nueva contraseña | Tarea 18 | ☐ |
| M15 | Dispositivos y Actividad en el Redmi: cerrar otra sesión (con la cuenta abierta en el emulador) y verla en Actividad | Tarea 19 | ☐ |
| M16 | Eliminar una cuenta de prueba en el Redmi: vuelve al inicio y ya no se puede entrar con ella | Tarea 20 | ☐ |

## Lectura del código marcado «REQUIERE REVISIÓN HUMANA»
Hay que leerlo y entenderlo para poder explicarlo en la presentación.
| # | Módulo | Tarea | Hecho |
|---|---|---|---|
| R1 | `core/security`: `SecureScreen`, `ExcludeFromAutofill`, `SharedRequest` | 02 | ☐ |
| R2 | `core/crypto`: `KdfParams`, `Argon2idKdf`, `Hkdf`, `MasterPasswordEncoder` (y `tools/vectors/`) | 07 | ☐ |
| R3 | `core/crypto`: `AesGcm`, `SecureBytes`, `VaultKeys` | 08 | ☐ |
| R4 | `core/session`: `VaultSession`, `AutoLocker`; `ItemCipher` | 09 | ☐ |
| R5 | Autenticación: `AuthRepositoryImpl`, `PendingAuth`, `TokenStore`/`TokenManager` | 10 | ☐ |
| R6 | `VaultRepositoryImpl`, `SecretClipboard`, máscara de `PasswordField` | 11 | ☐ |
| R7 | `core/sync`: `SyncEngine`, `SyncScheduler`, `SyncWorker`; `ConflictResolver` | 12 | ☐ |
| R8 | `PasswordGenerator`, `PasswordHandoff`, `SyncShutdown` | 13 | ☐ |
| R9 | Salud: `HealthAnalyzer` y cliente HIBP | 14 | ☐ |
| R10 | Huella: `BiometricKeyStore`, activar/desactivar y desbloqueo con `CryptoObject` | 16 | ☐ |
| R11 | Reautenticación y 2FA: `ReauthRunnerImpl`, `TwoFactorSetupViewModel`, `TwoFactorManageViewModel`, `SessionLifecycleLock` | 17 | ☐ |
| R12 | Cambio de contraseña maestra: nueva sal, re-envoltura de la `vault_key` y de la huella | 18 | ☐ |
| R13 | Recuperación de cuenta con sal nueva en `login()` (tres condiciones) | 19 | ☐ |
| R14 | Eliminar cuenta: EP-18 con `NonCancellable` y limpieza local completa | 20 | ☐ |

## Antes de unir el backend real o de entregar
| # | Qué | Hecho |
|---|---|---|
| E1 | `USE_FAKE_BACKEND = false` en release (el código de correo no debe verse) | ☐ |
| E2 | Pines SPKI del servidor en `network_security_config.xml` (DEC-22) | ☐ |
| E3 | Volver a correr `ClipboardDeviceTest` con «Enable clipboard sharing» desactivado en el emulador (Android Studio → emulador → ⋯ Extended controls → Settings → General) | ☐ |
