package com.luis.end_security.feature.vault.ui.conflict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.designsystem.component.initialOf
import com.luis.end_security.feature.vault.domain.ConflictRepository
import com.luis.end_security.feature.vault.domain.ConflictRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Una fila de «Por resolver» (SCR-19). */
class ConflictListRow(val id: UUID, val name: String?, val initial: String, val localAt: Instant) {
    override fun toString(): String = "ConflictListRow(***)"
}

/** SCR-19 y SCR-20: «Por resolver», con el aviso «{nombre} quedó resuelta» cuando se acaba de resolver una. */
class ConflictListUiState(
    val rows: List<ConflictListRow> = emptyList(),
    /** `null` sin aviso; vacío cuando se resolvió una sin nombre. */
    val resolvedName: String? = null,
    val isLoading: Boolean = true,
) {
    override fun toString(): String = "ConflictListUiState(count=${rows.size}, ***)"
}

sealed interface ConflictListEffect {
    data class Open(val id: UUID) : ConflictListEffect

    /** No queda ningún conflicto: la pantalla vuelve a la Bóveda y el aviso desaparece. */
    data object Close : ConflictListEffect
}

@HiltViewModel
class ConflictListViewModel @Inject constructor(
    private val conflicts: ConflictRepository,
) : ViewModel() {

    private val _effects = Channel<ConflictListEffect>(Channel.BUFFERED)
    val effects: Flow<ConflictListEffect> = _effects.receiveAsFlow()

    private var noticeJob: Job? = null
    private var hadRows = false

    val state: StateFlow<ConflictListUiState> = combine(conflicts.observeConflicts(), conflicts.lastResolved) { rows, resolved ->
        ConflictListUiState(
            rows = rows.map { it.toListRow() },
            resolvedName = resolved,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConflictListUiState())

    init {
        viewModelScope.launch {
            conflicts.observeConflicts().collect { rows ->
                // Cuando ya no queda ninguno (y había), se sale: el aviso de la Bóveda ya desapareció.
                if (hadRows && rows.isEmpty() && conflicts.lastResolved.value != null) _effects.send(ConflictListEffect.Close)
                hadRows = rows.isNotEmpty()
            }
        }
        viewModelScope.launch {
            // «{nombre} quedó resuelta» dura 2,5 s.
            conflicts.lastResolved.collect { name ->
                noticeJob?.cancel()
                if (name != null) {
                    noticeJob = launch {
                        delay(NOTICE_MILLIS)
                        conflicts.clearResolved()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        // El aviso «{nombre} quedó resuelta» no debe quedarse en memoria si la pantalla se cierra antes de que acabe.
        conflicts.clearResolved()
    }

    fun onRowClick(row: ConflictListRow) {
        viewModelScope.launch { _effects.send(ConflictListEffect.Open(row.id)) }
    }

    private fun ConflictRow.toListRow() = ConflictListRow(id, name, name?.let { initialOf(it) } ?: "?", localAt)

    companion object {
        const val NOTICE_MILLIS = 2_500L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
