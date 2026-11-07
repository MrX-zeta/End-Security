package com.luis.end_security.feature.health.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.common.DeviceClock
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.health.domain.HealthProgressCalculator
import com.luis.end_security.feature.health.domain.HealthReport
import com.luis.end_security.feature.health.domain.HealthRepository
import com.luis.end_security.feature.health.domain.ItemHealth
import com.luis.end_security.feature.health.domain.ProgressSummary
import com.luis.end_security.feature.health.domain.SnapshotPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class HealthTab { Summary, Progress }

enum class RankingTab { Strongest, Weakest }

/**
 * Lo que muestran las pantallas de Salud (SCR-22…28, SCR-40). Nada de esto lleva contraseñas: solo ids, nombres,
 * niveles, largos y fechas. [isLoading] es el estado «Revisando filtraciones…» (SCR-40): mientras no hay análisis o es
 * la primera revisión de HIBP.
 */
class HealthUiState(
    val isLoading: Boolean = true,
    val report: HealthReport? = null,
    val checking: Boolean = false,
    val lastChecked: Instant? = null,
    val offline: Boolean = false,
    val tab: HealthTab = HealthTab.Summary,
    val rankingTab: RankingTab = RankingTab.Strongest,
    val progress: ProgressSummary? = null,
    /** Cambia cada vez que «Revisar ahora» termina sin conexión (0 = nunca): la pantalla muestra «Sin conexión…» un momento. */
    val offlineNoticeId: Int = 0,
) {
    /** Hay una bóveda con contraseñas que analizar. */
    val hasItems: Boolean get() = report?.items?.isNotEmpty() == true

    /** Los 10 primeros del ranking elegido (largo como desempate). */
    val ranking: List<ItemHealth> get() = rankingFor(rankingTab)

    /** El ranking de [tab]: la pantalla lo pide por pestaña para que el cambio entre «Más fuertes» y «Más débiles» pueda animarse. */
    fun rankingFor(tab: RankingTab): List<ItemHealth> {
        val items = report?.items.orEmpty()
        val sorted = when (tab) {
            RankingTab.Strongest -> items.sortedWith(compareByDescending<ItemHealth> { it.strengthScore }.thenByDescending { it.passwordLength })
            RankingTab.Weakest -> items.sortedWith(compareBy<ItemHealth> { it.strengthScore }.thenBy { it.passwordLength })
        }
        return sorted.take(RANKING_SIZE)
    }

    override fun toString(): String = "HealthUiState(loading=$isLoading, report=$report)"

    companion object {
        const val RANKING_SIZE = 10
    }
}

/**
 * Salud de la bóveda (SCR-22…28, SCR-40). El selector «Resumen | Progreso» y «Más fuertes | Más débiles» viven aquí,
 * en memoria, y no suman pasos al historial (DEC-56). Se vacía solo al bloquear (el repositorio deja de entregar el análisis).
 */
@HiltViewModel
class HealthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val health: HealthRepository,
    private val session: VaultSession,
    private val clock: DeviceClock,
) : ViewModel() {

    private val tab = MutableStateFlow(HealthTab.Summary)
    private val rankingTab = MutableStateFlow(
        if (savedStateHandle.get<String>(RANKING_ARG) == RankingTab.Weakest.name) RankingTab.Weakest else RankingTab.Strongest,
    )

    private val offlineNotices = MutableStateFlow(0)

    val state: StateFlow<HealthUiState> = combine(
        health.state, health.observeProgress(), tab, rankingTab, offlineNotices,
    ) { state, snapshots, tab, ranking, notices ->
        build(state.report, snapshots, tab, ranking, state.checking, state.lastCheckedAt, state.offline, notices)
    }
        // Con la bóveda bloqueada el estado queda vacío aunque nadie lo esté mirando (INV-07): con `saveState` este ViewModel vive
        // mientras la bóveda esté abierta, y con `WhileSubscribed` el último análisis descifrado se quedaba en memoria al bloquear.
        .combine(session.state) { ui, vault -> if (vault is VaultState.Locked) HealthUiState() else ui }
        .stateIn(viewModelScope, SharingStarted.Lazily, HealthUiState())

    init {
        // La primera vez que se abre Salud: análisis local y, si nunca se revisó, también HIBP (SCR-40).
        viewModelScope.launch {
            if (session.state.value is VaultState.Unlocked) health.open()
        }
    }

    private fun build(
        report: HealthReport?,
        snapshots: List<SnapshotPoint>,
        tab: HealthTab,
        ranking: RankingTab,
        checking: Boolean,
        lastChecked: Instant?,
        offline: Boolean,
        offlineNoticeId: Int,
    ): HealthUiState {
        val today = Instant.ofEpochMilli(clock.wallMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
        // «Revisando filtraciones…» mientras no hay análisis, o durante la primera revisión (nunca se había revisado).
        val loading = report == null || (checking && lastChecked == null && report.items.isNotEmpty())
        return HealthUiState(
            isLoading = loading,
            report = report,
            checking = checking,
            lastChecked = lastChecked,
            offline = offline,
            tab = tab,
            rankingTab = ranking,
            progress = report?.let { HealthProgressCalculator.compute(snapshots, it, today) },
            offlineNoticeId = offlineNoticeId,
        )
    }

    fun onTab(selected: HealthTab) {
        tab.value = selected
    }

    fun onRankingTab(selected: RankingTab) {
        rankingTab.value = selected
    }

    /**
     * «Revisar ahora»: vuelve a preguntarle a HIBP por todas (OP-10). Si termina sin conexión se avisa («Sin conexión. Te mostramos la
     * última revisión.») y lo que no se pudo revisar sigue como «sin revisar todavía».
     */
    fun onCheckNow() {
        viewModelScope.launch {
            health.checkBreaches(force = true)
            if (health.state.value.offline) offlineNotices.update { it + 1 }
        }
    }

    companion object {
        const val RANKING_ARG = "tab"
    }
}
