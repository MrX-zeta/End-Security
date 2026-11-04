package com.luis.end_security.feature.activity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.ApiResult
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.common.TimeZoneProvider
import com.luis.end_security.feature.activity.domain.ActivityDay
import com.luis.end_security.feature.activity.domain.ActivityEvent
import com.luis.end_security.feature.activity.domain.ActivityRules
import com.luis.end_security.feature.activity.domain.SessionApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class ActivityPhase { Loading, Loaded, Offline }

/**
 * Estado de «Actividad reciente» (SCR-34). [events] es la lista plana ya sin duplicados; [days] la agrupa por día local. Nada
 * secreto vive aquí: tipo, éxito, nombre del dispositivo y hora.
 */
data class ActivityUiState(
    val phase: ActivityPhase = ActivityPhase.Loading,
    val events: List<ActivityEvent> = emptyList(),
    val days: List<ActivityDay> = emptyList(),
    /** «Hoy» según el reloj del teléfono; cambia a medianoche y al volver a la pantalla. */
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val hasMore: Boolean = false,
    /** Pidiendo la página siguiente: indicador pequeño al final. */
    val loadingMore: Boolean = false,
    /** La página siguiente falló: no se vuelve a pedir sola; el pie ofrece reintentar. */
    val loadMoreFailed: Boolean = false,
)

sealed interface ActivityAction {
    data object Retry : ActivityAction

    /** Se llegó al final de la lista. */
    data object LoadMore : ActivityAction

    /** Pasó la medianoche local o se volvió a la pantalla: «Hoy» y «Ayer» se recalculan. */
    data object DayChanged : ActivityAction
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val api: SessionApi,
    private val clock: DeviceClock,
    private val timeZone: TimeZoneProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState(today = today()))
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        loadFirstPage()
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.wallMillis()).atZone(timeZone.zone()).toLocalDate()

    private fun loadFirstPage() {
        job?.cancel()
        _state.update { it.copy(phase = ActivityPhase.Loading, loadMoreFailed = false, loadingMore = false) }
        job = viewModelScope.launch {
            when (val result = api.listActivity(before = null, limit = ActivityRules.PAGE_SIZE)) {
                is ApiResult.Success -> _state.update {
                    withEvents(it.copy(phase = ActivityPhase.Loaded, hasMore = result.value.hasMore), result.value.events)
                }
                is ApiResult.Failure -> _state.update { it.copy(phase = ActivityPhase.Offline) }
            }
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (current.phase != ActivityPhase.Loaded || !current.hasMore || current.loadingMore || current.loadMoreFailed) return
        val last = current.events.lastOrNull() ?: return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch {
            when (val result = api.listActivity(before = last.id, limit = ActivityRules.PAGE_SIZE)) {
                is ApiResult.Success -> _state.update {
                    withEvents(it.copy(loadingMore = false, hasMore = result.value.hasMore), result.value.events, append = true)
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** Aplica [page] (primera página o siguiente, sin duplicados) y vuelve a agrupar por día local. */
    private fun withEvents(state: ActivityUiState, page: List<ActivityEvent>, append: Boolean = false): ActivityUiState {
        val events = if (append) ActivityRules.appendPage(state.events, page) else ActivityRules.appendPage(emptyList(), page)
        return state.copy(
            events = events,
            days = ActivityRules.groupByDay(events, timeZone.zone()),
            today = today(),
            // Una página sin nada nuevo no puede seguir prometiendo más: evita pedir la misma sin fin.
            hasMore = state.hasMore && (events.size > state.events.size || !append),
        )
    }

    fun onAction(action: ActivityAction) {
        when (action) {
            ActivityAction.Retry -> {
                // Con la primera página ya cargada, «Reintentar» pide la siguiente; sin ella, vuelve a empezar.
                if (_state.value.phase == ActivityPhase.Loaded) {
                    _state.update { it.copy(loadMoreFailed = false) }
                    loadMore()
                } else {
                    loadFirstPage()
                }
            }
            ActivityAction.LoadMore -> loadMore()
            ActivityAction.DayChanged -> _state.update {
                it.copy(today = today(), days = ActivityRules.groupByDay(it.events, timeZone.zone()))
            }
        }
    }
}
