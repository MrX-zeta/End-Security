package com.luis.end_security.feature.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.vault.domain.VaultSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bóveda · cargando (SCR-39): la descarga inicial (OP-32) y, al terminar, a la Bóveda. Si falla
 * (sin red), sigue igual con lo que haya en Room (DEC-58); la Bóveda mostrará el aviso.
 */
@HiltViewModel
class VaultLoadingViewModel @Inject constructor(
    sync: VaultSyncRepository,
) : ViewModel() {

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            sync.initialDownload()
            _done.send(Unit)
        }
    }
}

/** Estado del marcador de la Bóveda (SCR-10/11) hasta que exista la lista. */
data class VaultUiState(
    /** «Te quedan N códigos de respaldo» tras entrar con uno, si quedan 3 o menos. */
    val recoveryCodesLeft: Int? = null,
)

/**
 * Marcador de la Bóveda. Como toda pantalla con datos de la bóveda, vacía su estado en cuanto la
 * sesión se bloquea (hallazgo 3 de la tarea 09).
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    auth: AuthRepository,
    session: VaultSession,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState(recoveryCodesLeft = auth.consumeRecoveryCodesNotice()))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { if (it is VaultState.Locked) _state.value = VaultUiState() }
        }
    }
}
