package com.luis.end_security.navigation

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController

/**
 * Lo que tarda una navegación en terminar, en ms del reloj de animación y con un margen de un cuadro (16 ms): se avanza cuadro a
 * cuadro hasta que la pantalla de destino llega a `RESUMED` (Navigation la deja en `STARTED` mientras dura la transición) y ya no
 * queda otra entrada en transición. `waitForIdle` agrega unos 80 ms de espera de reposo y no sirve para medir una animación.
 */
internal fun ComposeTestRule.settleMillis(navController: NavHostController, action: () -> Unit, maxMillis: Long = 1_500): Long {
    mainClock.autoAdvance = false
    try {
        val start = mainClock.currentTime
        action()
        var guard = 0
        do {
            mainClock.advanceTimeByFrame()
            guard++
            val entry = runOnUiThread { navController.currentBackStackEntry }
            val resumed = entry?.lifecycle?.currentState == Lifecycle.State.RESUMED
            val settled = resumed && runOnUiThread { navController.visibleEntries.value.size } <= 1
            if (settled) break
        } while (mainClock.currentTime - start < maxMillis && guard < 400)
        return mainClock.currentTime - start
    } finally {
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
