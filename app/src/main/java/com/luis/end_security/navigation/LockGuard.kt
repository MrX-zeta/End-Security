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
    accounts: AccountRepository,
) : ViewModel() {

    private val _lockedEvents = Channel<LockDestination>(Channel.CONFLATED)
    val lockedEvents: Flow<LockDestination> = _lockedEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            var previous: VaultState = session.state.value
            session.state.collect { current ->
                if (previous is VaultState.Unlocked && current is VaultState.Locked) {
                    _lockedEvents.send(if (accounts.hasLocalAccount()) LockDestination.Unlock else LockDestination.Login)
                }
                previous = current
            }
        }
    }
}

/**
 * `LockGuard` (CLAUDE.md, `navigation/`): si la bóveda se bloquea, lleva a Desbloquear (o a
 * Iniciar sesión sin cuenta) con la pila vacía. Así no queda debajo ninguna pantalla con datos
 * de la bóveda (hallazgo 10 de la tarea 06).
 *
 * TODO(tarea 10): las pantallas con datos de la bóveda deben ocultar su contenido en cuanto el
 *  estado sea Locked (sin esperar a la navegación ni a la transición) y sus ViewModel vaciar los
 *  elementos descifrados; y al restaurar la pila tras la muerte del proceso, un destino protegido
 *  con la bóveda bloqueada debe ir aquí también.
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
