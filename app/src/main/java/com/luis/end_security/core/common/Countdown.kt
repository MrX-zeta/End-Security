package com.luis.end_security.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Cuenta regresiva por segundos (reenviar código, bloqueo por intentos). Usa una [TimeSource]
 * inyectada para probarla con el reloj virtual de TestScope, y calcula lo que falta contra el
 * plazo, así que no acumula desfase aunque un segundo tarde en llegar.
 */
class Countdown(private val timeSource: TimeSource) {

    private var job: Job? = null

    /** Empieza (o reinicia) la cuenta; [onTick] recibe los segundos que faltan, hasta 0. */
    fun start(scope: CoroutineScope, duration: Duration, onTick: (secondsLeft: Int) -> Unit) {
        job?.cancel()
        val deadline = timeSource.markNow() + duration
        job = scope.launch {
            while (true) {
                // Antes del plazo, elapsedNow() es negativo: lo que falta es su opuesto.
                val left = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
                val secondsLeft = ceil(left / 1.seconds).toInt()
                onTick(secondsLeft)
                if (secondsLeft == 0) break
                // Espera justo hasta que cambie el número que se muestra.
                delay(left - (secondsLeft - 1).seconds)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
