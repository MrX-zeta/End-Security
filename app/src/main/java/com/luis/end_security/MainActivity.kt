package com.luis.end_security

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.navigation.AppNavHost
import com.luis.end_security.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Compose ya dibujó su primer cuadro: el splash del sistema puede irse. */
    @Volatile
    private var firstFrameDrawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes de super.onCreate (SCR-46, A): pasa del tema de arranque al tema de la app.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Solo hasta el primer cuadro de Compose, no hasta decidir el destino: lo que sigue lo
        // dibuja el splash propio con el escudo en el mismo lugar, así que no hay salto.
        splash.setKeepOnScreenCondition { !firstFrameDrawn }
        // Sin animar: la continuidad la da el escudo de Compose, que ya ocupa el mismo lugar.
        splash.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        // Solo en depuración: `adb shell am start -n com.luis.end_security/.MainActivity
        // --es debug_route unlock` abre Desbloquear sin pasar por el acceso.
        val debugStartRoute = if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_DEBUG_ROUTE)?.takeIf { it in Routes.DEBUG_START_ROUTES }
        } else {
            null
        }
        setContent {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                firstFrameDrawn = true
            }
            EndSecurityTheme {
                AppNavHost(debugStartRoute = debugStartRoute)
            }
        }
    }

    private companion object {
        const val EXTRA_DEBUG_ROUTE = "debug_route"
    }
}
