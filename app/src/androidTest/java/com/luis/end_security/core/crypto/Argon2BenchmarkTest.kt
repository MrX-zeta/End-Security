package com.luis.end_security.core.crypto

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Medición de Argon2id para elegir los parámetros (PEND-01). Sin interfaz: corre con
 *
 * ```
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luis.end_security.core.crypto.Argon2BenchmarkTest
 * ```
 *
 * y deja la tabla en logcat (etiqueta `Argon2Benchmark`) y en la salida de la instrumentación.
 * La contraseña y la sal son de prueba; no se registra ninguna llave.
 */
@RunWith(AndroidJUnit4::class)
class Argon2BenchmarkTest {

    private data class Config(val memoryMiB: Int, val iterations: Int)

    private val configs = listOf(Config(19, 2), Config(46, 1), Config(64, 3))
    private val password = "contraseña de prueba para medir".toByteArray()
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun medir_argon2id() {
        // Un APK depurable corre más lento en ART: hay que saberlo al comparar números.
        val appInfo = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo
        val debuggable = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val lines = mutableListOf(
            "Teléfono: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                " · Runtime.maxMemory() = ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MiB" +
                " · APK depurable: ${if (debuggable) "sí" else "no"}",
            "| Memoria | t | p | Mediana (ms) | Peor (ms) | Corridas (ms) |",
            "|---|---|---|---|---|---|",
        )
        var measured = 0
        configs.forEach { config ->
            val params = KdfParams(memoryKiB = config.memoryMiB * 1024, iterations = config.iterations, parallelism = 1)
            val row = try {
                runOnce(params) // calentamiento
                val runs = List(RUNS) { runOnce(params) }
                measured++
                val sorted = runs.sorted()
                "| ${config.memoryMiB} MiB | ${config.iterations} | 1 | ${sorted[RUNS / 2]} | ${sorted.last()} | ${runs.joinToString(", ")} |"
            } catch (_: OutOfMemoryError) {
                "| ${config.memoryMiB} MiB | ${config.iterations} | 1 | OutOfMemoryError | — | — |"
            }
            lines += row
        }
        lines.forEach { Log.i(TAG, it) }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("stream", lines.joinToString("\n", prefix = "\n", postfix = "\n")) },
        )
        assertTrue("Ninguna configuración se pudo medir", measured > 0)
    }

    private fun runOnce(params: KdfParams): Long {
        val start = SystemClock.elapsedRealtimeNanos()
        val key = Argon2idKdf.derive(password, salt, params)
        val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
        key.wipe()
        return elapsedMillis
    }

    private companion object {
        const val TAG = "Argon2Benchmark"
        const val RUNS = 5
    }
}
