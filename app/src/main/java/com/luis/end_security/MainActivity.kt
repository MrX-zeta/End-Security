package com.luis.end_security

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.navigation.AppNavHost
import com.luis.end_security.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo en depuración: `adb shell am start -n com.luis.end_security/.MainActivity
        // --es debug_route unlock` abre Desbloquear sin pasar por el acceso.
        val debugStartRoute = if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_DEBUG_ROUTE)?.takeIf { it in Routes.DEBUG_START_ROUTES }
        } else {
            null
        }
        setContent {
            EndSecurityTheme {
                AppNavHost(debugStartRoute = debugStartRoute)
            }
        }
    }

    private companion object {
        const val EXTRA_DEBUG_ROUTE = "debug_route"
    }
}
