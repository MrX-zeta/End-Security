package com.luis.end_security.core.common

import android.os.SystemClock

/**
 * Los dos relojes del teléfono, inyectados para poder probar lo que depende de ellos:
 * - [wallMillis]: hora del sistema; el usuario la puede cambiar.
 * - [elapsedRealtimeMillis]: tiempo desde que se encendió el teléfono; no se puede cambiar, pero
 *   vuelve a empezar al reiniciar.
 */
interface DeviceClock {
    fun wallMillis(): Long

    fun elapsedRealtimeMillis(): Long
}

object AndroidDeviceClock : DeviceClock {
    override fun wallMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
