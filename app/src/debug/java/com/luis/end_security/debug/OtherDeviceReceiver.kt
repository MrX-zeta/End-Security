package com.luis.end_security.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.vault.data.ItemCipher
import com.luis.end_security.feature.vault.domain.ReadableEntry
import com.luis.end_security.feature.vault.domain.VaultApi
import com.luis.end_security.feature.vault.domain.VaultItem
import com.luis.end_security.feature.vault.domain.VaultRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OtherDeviceEntryPoint {
    fun vaultApi(): VaultApi

    fun repository(): VaultRepository

    fun session(): VaultSession
}

/**
 * **Solo depuración.** Simula al «otro dispositivo» de la tarea 12: escribe en el backend falso (que guarda en
 * `files/fake_server.json`) una versión distinta de un elemento, con la misma `vault_key` de la sesión abierta.
 * Así se prueba un conflicto de punta a punta sin un segundo teléfono.
 *
 * Con la bóveda abierta y la app en pantalla:
 *
 * ```
 * adb -s emulator-5554 shell am broadcast -a com.luis.end_security.debug.OTHER_DEVICE \
 *     -n com.luis.end_security/.debug.OtherDeviceReceiver --es name "Netflix" --es new_name "Netflix (otro)"
 * ```
 *
 * Extras: `name` (el nombre actual del elemento), `new_name`, `new_password` (opcionales) o `--ez delete true`
 * para dejar una lápida. Solo se permite desde `adb shell` (permiso `DUMP`). No existe en release. No imprime
 * nombres ni contraseñas: solo `ok` o el motivo del fallo.
 */
class OtherDeviceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: return report("falta el extra name")
        val newName = intent.getStringExtra("new_name")
        val newPassword = intent.getStringExtra("new_password")
        val delete = intent.getBooleanExtra("delete", false)
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, OtherDeviceEntryPoint::class.java)
        val pending = goAsync()
        scope.launch {
            try {
                report(simulate(entry, name, newName, newPassword, delete))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun simulate(
        entry: OtherDeviceEntryPoint,
        name: String,
        newName: String?,
        newPassword: String?,
        delete: Boolean,
    ): String {
        val session = entry.session()
        val userId = (session.state.value as? VaultState.Unlocked)?.userId ?: return "la bóveda está bloqueada"
        val target = entry.repository().observeEntries().first()
            .filterIsInstance<ReadableEntry>()
            .firstOrNull { it.item.name == name } ?: return "no hay un elemento con ese nombre"
        val api = entry.vaultApi()
        val remote = (api.getItem(target.id) as? ApiResult.Success)?.value ?: return "no se pudo pedir el elemento"
        if (delete) {
            return when (api.deleteItem(target.id, remote.version)) {
                is ApiResult.Success -> "ok"
                is ApiResult.Failure -> "el servidor rechazó la lápida"
            }
        }
        val current = target.item
        val changed = VaultItem(
            name = newName ?: current.name,
            username = current.username,
            password = newPassword ?: current.password,
            url = current.url,
            notes = current.notes,
            createdAt = current.createdAt,
            passwordChangedAt = if (newPassword != null) Instant.now() else current.passwordChangedAt,
        )
        val sealed = session.withVaultKey { key -> ItemCipher.encrypt(key, target.id, userId, changed) }
        return when (api.putItem(target.id, remote.version, remote.formatVersion, sealed)) {
            is ApiResult.Success -> "ok"
            is ApiResult.Failure -> "el servidor rechazó la edición"
        }
    }

    private fun report(result: String) {
        Log.i(TAG, result)
    }

    private companion object {
        const val TAG = "OtherDevice"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
