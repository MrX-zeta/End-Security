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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class HealthTab { Summary, Progress }

/** Cómo entra una vista de Salud (tarea 27). */
enum class EntranceMode {
    /** Ya está a la vista (volviendo de un detalle con Atrás): sin animar. */
    None,

    /** La primera vez de la sesión desbloqueada: animaciones completas (600 ms). */
    First,

    /** Una repetición (al cambiar de vista con el selector o al volver a la pestaña): más corta (400 ms). */
    Replay,
}

/**
 * Cuándo se animan las vistas de Salud: **cada vez que se entra** a una vista —al cambiar con el selector o al volver a la pestaña—, no al
 * recomponer ni al volver de un detalle (Filtradas, Repetidas…) con Atrás. La primera vez de la sesión dura 600 ms y las repeticiones 400.
 * Vive en el ViewModel, que se descarta al bloquear (se limpian las pilas guardadas).
 */
class HealthEntrance {
    private val shown = mutableSetOf<HealthTab>()
    private var skipNext = false

    /** Se va a una pantalla de detalle: la próxima vez que se vea una vista es un regreso con Atrás y no se anima. */
    fun leavingForDetail() {
        skipNext = true
    }

    /** Se llama una vez cada vez que una vista entra a la composición. */
    fun modeFor(tab: HealthTab): EntranceMode {
        if (skipNext) {
            skipNext = false
            shown += tab
            return EntranceMode.None
        }
        return if (shown.add(tab)) EntranceMode.First else EntranceMode.Replay
    }
}

enum class RankingTab { Weakest, Improved }

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
    val rankingTab: RankingTab = RankingTab.Weakest,
    val progress: ProgressSummary? = null,
    /** Cambia cada vez que «Revisar ahora» termina sin conexión (0 = nunca): la pantalla muestra «Sin conexión…» un momento. */
    val offlineNoticeId: Int = 0,
    /** «Mejoradas recientemente»: cambiadas en los últimos 30 días y hoy sanas, de la más reciente a la más antigua. */
    val improvedRecently: List<ItemHealth> = emptyList(),
) {
    /** Hay una bóveda con contraseñas que analizar. */
    val hasItems: Boolean get() = report?.items?.isNotEmpty() == true

    /** El ranking elegido. */
    val ranking: List<ItemHealth> get() = rankingFor(rankingTab)

    /** El ranking de [tab]: la pantalla lo pide por pestaña para que el cambio entre «Más fuertes» y «Más débiles» pueda animarse. */
    fun rankingFor(tab: RankingTab): List<ItemHealth> {
        val items = report?.items.orEmpty()
        return when (tab) {
            // Las 10 más débiles (largo como desempate).
            RankingTab.Weakest -> items.sortedWith(compareBy<ItemHealth> { it.strengthScore }.thenBy { it.passwordLength }).take(RANKING_SIZE)
            RankingTab.Improved -> improvedRecently
        }
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

    /** Cuándo se animan las vistas (ver [HealthEntrance]). */
    val entrance = HealthEntrance()

    private val tab = MutableStateFlow(HealthTab.Summary)
    private val rankingTab = MutableStateFlow(
        if (savedStateHandle.get<String>(RANKING_ARG) == RankingTab.Improved.name) RankingTab.Improved else RankingTab.Weakest,
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
            improvedRecently = improvedRecently(report),
        )
    }

    /** Cambiadas en los últimos [IMPROVED_DAYS] días y hoy sanas, de la más reciente a la más antigua. */
    private fun improvedRecently(report: HealthReport?): List<ItemHealth> {
        val now = Instant.ofEpochMilli(clock.wallMillis())
        val since = now.minus(Duration.ofDays(IMPROVED_DAYS))
        return report?.items.orEmpty()
            .filter { it.healthy && !it.passwordChangedAt.isBefore(since) && !it.passwordChangedAt.isAfter(now) }
            .sortedByDescending { it.passwordChangedAt }
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

        /** «Mejoradas recientemente»: cambiadas en los últimos 30 días. */
        const val IMPROVED_DAYS = 30L
    }
}
