package com.luis.end_security.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.core.session.VaultSession
import com.luis.end_security.core.session.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class VaultStateViewModel @Inject constructor(session: VaultSession) : ViewModel() {
    val state: StateFlow<VaultState> = session.state
}

/**
 * Envuelve el contenido de toda pantalla con datos de la bóveda (hallazgo 3 de la tarea 09): si la
 * bóveda está bloqueada no dibuja nada en ese mismo cuadro, sin esperar a que `LockGuard` navegue
 * a Desbloquear. Así no se ve la bóveda durante la transición ni al volver tras el autobloqueo.
 *
 * Además avisa con [onLocked]: si la pantalla se restauró ya bloqueada (muerte del proceso),
 * `LockGuard` no ve ningún cambio de estado y nadie más la sacaría de aquí.
 */
@Composable
fun RequireUnlocked(
    onLocked: () -> Unit,
    viewModel: VaultStateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state is VaultState.Unlocked) {
        content()
    } else {
        val currentOnLocked by rememberUpdatedState(onLocked)
        LaunchedEffect(Unit) { currentOnLocked() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}
