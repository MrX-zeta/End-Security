package com.luis.end_security.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.session.AccountDeletionGate
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.account.domain.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GATE_REOPEN_MILLIS = 500L

/** A dónde ir cuando la bóveda se bloquea. */
enum class LockDestination { Unlock, Login }

/**
 * Avisa cuando la bóveda se bloquea (autobloqueo o «Bloquear ahora», DEC-52): a Desbloquear si
 * hay una cuenta en este teléfono, o a Iniciar sesión si no. El bloqueo del arranque no cuenta:
 * solo el paso de abierta a bloqueada.
 */
@HiltViewModel
class LockGuardViewModel @Inject constructor(
    private val session: VaultSession,
    private val accounts: AccountRepository,
    private val deletion: AccountDeletionGate,
) : ViewModel() {

    /**
     * ¿La bóveda está abierta? La barra inferior y los avisos solo se ven con ella abierta (tarea 26): entre que la bóveda se bloquea
     * y que se llega a Desbloquear no debe verse ni tocarse la barra.
     */
    val unlocked: StateFlow<Boolean> = session.state
        .map { it is VaultState.Unlocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value is VaultState.Unlocked)

    private val _lockedEvents = Channel<LockDestination>(Channel.CONFLATED)
    val lockedEvents: Flow<LockDestination> = _lockedEvents.receiveAsFlow()

    /** La cuenta se eliminó y la limpieza terminó: a «Cuenta eliminada» (tarea 20). */
    val accountDeleted: Flow<Unit> = deletion.deleted

    /**
     * Ya se navegó a «Cuenta eliminada»: los bloqueos vuelven a mandar a Desbloquear o a Iniciar sesión. La puerta se reabre un
     * momento después, para que el bloqueo de la propia limpieza (que el colector atiende de forma asíncrona) no la encuentre
     * ya cerrada y mande a otra pantalla.
     */
    fun onAccountDeletedHandled() {
        viewModelScope.launch {
            delay(GATE_REOPEN_MILLIS)
            deletion.end()
        }
    }

    init {
        viewModelScope.launch {
            var previous: VaultState = session.state.value
            session.state.collect { current ->
                // Eliminando la cuenta, la propia limpieza bloquea la bóveda: la navegación va a «Cuenta eliminada», no a Desbloquear.
                if (previous is VaultState.Unlocked && current is VaultState.Locked && !deletion.active) _lockedEvents.send(destination())
                previous = current
            }
        }
    }

    /**
     * Una pantalla protegida se dibujó con la bóveda ya bloqueada, sin el paso de abierta a
     * bloqueada: la pila que Android restaura tras matar el proceso (la llave no sobrevive).
     */
    fun onProtectedScreenLocked() {
        if (deletion.active) return
        viewModelScope.launch { _lockedEvents.send(destination()) }
    }

    private suspend fun destination(): LockDestination =
        if (accounts.hasLocalAccount()) LockDestination.Unlock else LockDestination.Login
}

/**
 * `LockGuard` (CLAUDE.md, `navigation/`): si la bóveda se bloquea, lleva a Desbloquear (o a
 * Iniciar sesión sin cuenta) con la pila vacía. Así no queda debajo ninguna pantalla con datos
 * de la bóveda (hallazgo 10 de la tarea 06).
 *
 * El contenido lo oculta `RequireUnlocked` en el mismo cuadro, sin esperar a esta navegación, y
 * al arrancar con la bóveda bloqueada `StartDestinationViewModel` ya elige Desbloquear.
 */
@Composable
fun LockGuard(
    onLocked: (LockDestination) -> Unit,
    onAccountDeleted: () -> Unit = {},
    viewModel: LockGuardViewModel = hiltViewModel(),
) {
    val currentOnLocked by rememberUpdatedState(onLocked)
    val currentOnAccountDeleted by rememberUpdatedState(onAccountDeleted)
    LaunchedEffect(viewModel) {
        viewModel.lockedEvents.collect { currentOnLocked(it) }
    }
    // Fuera de la pantalla de Eliminar: esa sale de la composición en cuanto la limpieza bloquea la bóveda.
    LaunchedEffect(viewModel) {
        viewModel.accountDeleted.collect {
            currentOnAccountDeleted()
            viewModel.onAccountDeletedHandled()
        }
    }
}
