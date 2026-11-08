package com.luis.end_security.feature.vault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.feature.vault.domain.VaultSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
