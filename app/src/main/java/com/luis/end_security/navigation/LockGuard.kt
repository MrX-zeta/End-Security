package com.luis.end_security.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import com.luis.end_security.feature.account.domain.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A dónde ir cuando la bóveda se bloquea. */
enum class LockDestination { Unlock, Login }

/**
 * Avisa cuando la bóveda se bloquea (autobloqueo o «Bloquear ahora», DEC-52): a Desbloquear si
 * hay una cuenta en este teléfono, o a Iniciar sesión si no. El bloqueo del arranque no cuenta:
 * solo el paso de abierta a bloqueada.
 */
@HiltViewModel
class LockGuardViewModel @Inject constructor(
    session: VaultSession,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _lockedEvents = Channel<LockDestination>(Channel.CONFLATED)
    val lockedEvents: Flow<LockDestination> = _lockedEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            var previous: VaultState = session.state.value
            session.state.collect { current ->
                if (previous is VaultState.Unlocked && current is VaultState.Locked) _lockedEvents.send(destination())
                previous = current
            }
        }
    }

    /**
     * Una pantalla protegida se dibujó con la bóveda ya bloqueada, sin el paso de abierta a
     * bloqueada: la pila que Android restaura tras matar el proceso (la llave no sobrevive).
     */
    fun onProtectedScreenLocked() {
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
    viewModel: LockGuardViewModel = hiltViewModel(),
) {
    val currentOnLocked by rememberUpdatedState(onLocked)
    LaunchedEffect(viewModel) {
        viewModel.lockedEvents.collect { currentOnLocked(it) }
    }
}
