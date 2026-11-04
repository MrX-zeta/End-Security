package com.luis.end_security.feature.activity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.feature.activity.domain.DeviceSession
import com.luis.end_security.feature.activity.domain.LastUsed
import com.luis.end_security.feature.activity.domain.RevokeSessions
import com.luis.end_security.feature.activity.domain.SessionApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class DevicesPhase { Loading, Loaded, Offline }

/** Una fila de «Dispositivos»: nombre tal como lo manda el servidor y cuándo se usó por última vez. */
data class DeviceRow(val id: UUID, val name: String, val current: Boolean, val lastUsed: LastUsed)

/** La hoja de confirmación abierta. */
sealed interface DevicesSheet {
    data class CloseOne(val id: UUID, val name: String) : DevicesSheet

    data object CloseOthers : DevicesSheet
}

/** Aviso breve al pie. [id] cambia en cada uno, para que dos iguales seguidos vuelvan a mostrarse. */
sealed interface DevicesNotice {
    val id: Int

    data class Closed(val name: String, override val id: Int) : DevicesNotice

    /** Cerrar los demás salió completo. */
    data class OthersClosed(override val id: Int) : DevicesNotice

    /** Una sola sesión no se pudo cerrar. */
    data class OneFailed(val name: String, override val id: Int) : DevicesNotice

    /** [count] sesiones no se pudieron cerrar (cerrar los demás). */
    data class SomeFailed(val count: Int, override val id: Int) : DevicesNotice
}

/** Estado de «Dispositivos» (SCR-33). Nada secreto vive aquí: solo nombres y horas. */
data class DevicesUiState(
    val phase: DevicesPhase = DevicesPhase.Loading,
    /** La actual primero. */
    val devices: List<DeviceRow> = emptyList(),
    val sheet: DevicesSheet? = null,
    /** Cerrando sesión(es): el botón de la hoja carga y la hoja no se cierra. */
    val isClosing: Boolean = false,
    val notice: DevicesNotice? = null,
) {
    /** «Cerrar sesión en los demás» solo si hay más de uno. */
    val canCloseOthers: Boolean get() = devices.size > 1 && devices.any { !it.current }
}

sealed interface DevicesAction {
    data object Retry : DevicesAction

    data class RowClicked(val id: UUID) : DevicesAction

    data object CloseOthersClicked : DevicesAction

    data object SheetConfirmed : DevicesAction

    data object SheetDismissed : DevicesAction
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val api: SessionApi,
    private val revokeSessions: RevokeSessions,
    private val clock: DeviceClock,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var noticeSequence = 0

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(phase = DevicesPhase.Loading) }
        loadJob = viewModelScope.launch {
            when (val result = api.listSessions()) {
                is ApiResult.Success -> {
                    val now = Instant.ofEpochMilli(clock.wallMillis())
                    _state.update { it.copy(phase = DevicesPhase.Loaded, devices = rowsOf(result.value, now)) }
                }
                // Sin red, sin sesión válida o error del servidor: la misma pantalla «No pudimos cargar tus dispositivos».
                is ApiResult.Failure -> _state.update { it.copy(phase = DevicesPhase.Offline) }
            }
        }
    }

    /** La actual primero y, después, las más usadas hace menos; el orden del servidor se respeta en los empates. */
    private fun rowsOf(sessions: List<DeviceSession>, now: Instant): List<DeviceRow> =
        sessions
            .sortedWith(compareByDescending<DeviceSession> { it.current }.thenByDescending { it.lastUsedAt })
            .map { DeviceRow(it.id, it.deviceName, it.current, LastUsed.of(it.lastUsedAt, now, it.current)) }

    fun onAction(action: DevicesAction) {
        when (action) {
            DevicesAction.Retry -> load()
            is DevicesAction.RowClicked -> {
                val row = _state.value.devices.firstOrNull { it.id == action.id } ?: return
                // La sesión actual no tiene acción: se cierra desde Ajustes → Cerrar sesión.
                if (row.current || _state.value.isClosing) return
                _state.update { it.copy(sheet = DevicesSheet.CloseOne(row.id, row.name)) }
            }
            DevicesAction.CloseOthersClicked ->
                if (_state.value.canCloseOthers && !_state.value.isClosing) _state.update { it.copy(sheet = DevicesSheet.CloseOthers) }
            DevicesAction.SheetDismissed -> _state.update { if (it.isClosing) it else it.copy(sheet = null) }
            DevicesAction.SheetConfirmed -> confirm()
        }
    }

    private fun confirm() {
        val current = _state.value
        val sheet = current.sheet ?: return
        if (current.isClosing) return
        val targets = when (sheet) {
            is DevicesSheet.CloseOne -> listOf(sheet.id)
            DevicesSheet.CloseOthers -> current.devices.filter { !it.current }.map { it.id }
        }
        _state.update { it.copy(isClosing = true) }
        viewModelScope.launch {
            val outcome = try {
                revokeSessions(targets)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { state ->
                val failed = outcome?.failed ?: targets
                val remaining = state.devices.filter { it.id !in (outcome?.closed ?: emptyList()) }
                state.copy(
                    isClosing = false,
                    sheet = null,
                    devices = remaining,
                    notice = noticeFor(sheet, failed.size),
                )
            }
        }
    }

    private fun noticeFor(sheet: DevicesSheet, failedCount: Int): DevicesNotice {
        val id = ++noticeSequence
        return when (sheet) {
            is DevicesSheet.CloseOne ->
                if (failedCount == 0) DevicesNotice.Closed(sheet.name, id) else DevicesNotice.OneFailed(sheet.name, id)
            // Cerrar los demás: si todo salió bien, el mismo aviso con la cuenta; si no, cuántos no se cerraron.
            DevicesSheet.CloseOthers ->
                if (failedCount == 0) DevicesNotice.OthersClosed(id) else DevicesNotice.SomeFailed(failedCount, id)
        }
    }
}
