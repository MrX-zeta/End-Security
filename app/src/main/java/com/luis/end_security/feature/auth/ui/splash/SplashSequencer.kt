package com.luis.end_security.feature.auth.ui.splash

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Tiempos del splash (SCR-46, tablero «Splash · Final»). */
object SplashTiming {
    /** El escudo termina de subir (resorte casi crítico). */
    const val SHIELD_MS = 500L

    /** La marca aparece entre 120 y 520 ms. */
    const val BRAND_DELAY_MS = 120L
    const val BRAND_MS = 400

    /** El lema aparece entre 260 y 620 ms: el final de la entrada. */
    const val TAGLINE_DELAY_MS = 260L
    const val TAGLINE_MS = 360

    /** Los puntos de carga solo aparecen si el destino tarda más de esto. */
    const val DOTS_AFTER_MS = 800L

    /** Fundido hacia la ruta cuando hay destino y terminó la entrada. */
    const val EXIT_MS = 250
}

/**
 * El orden de las fases del splash, sin dibujar nada: la pantalla le pasa cómo se ve la entrada
 * y la salida y él decide cuándo corre cada una. Así se prueba con tiempo virtual.
 *
 * - Entrada: sigue su curso aunque ya haya destino (sin esperas extra; termina con el lema).
 * - Puntos: solo si pasaron [SplashTiming.DOTS_AFTER_MS] y el destino todavía no está decidido.
 * - Salida: cuando hay destino **y** terminó la entrada.
 * - Con «Quitar animaciones» no hay entrada: se ve la composición final y se sale con un corte
 *   (la salida se llama igual para que la pantalla se quite, pero sin fundido).
 */
class SplashSequencer(private val reducedMotion: Boolean) {

    private val _showDots = MutableStateFlow(false)

    /** Los tres puntos de carga están visibles. */
    val showDots: StateFlow<Boolean> = _showDots.asStateFlow()

    /**
     * Corre las tres fases. [destinationReady] pasa a `true` cuando `StartDestinationViewModel`
     * decide la ruta. Termina cuando acabó [playExit].
     */
    suspend fun run(
        destinationReady: StateFlow<Boolean>,
        playEntry: suspend () -> Unit,
        playExit: suspend () -> Unit,
    ) = coroutineScope {
        val dots: Job = launch { trackDots(destinationReady) }
        if (!reducedMotion) playEntry()
        destinationReady.first { it }
        dots.cancel()
        _showDots.value = false
        playExit()
    }

    private suspend fun trackDots(destinationReady: StateFlow<Boolean>) {
        delay(SplashTiming.DOTS_AFTER_MS)
        destinationReady.collect { ready -> _showDots.value = !ready }
    }
}
