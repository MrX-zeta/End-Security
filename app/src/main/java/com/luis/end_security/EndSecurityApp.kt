package com.luis.end_security

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.luis.end_security.core.session.AutoLocker
import com.luis.end_security.feature.auth.data.PendingAuth
import com.luis.end_security.feature.auth.domain.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EndSecurityApp : Application() {

    @Inject
    lateinit var autoLocker: AutoLocker

    @Inject
    lateinit var pendingAuth: PendingAuth

    /** Se crea al arrancar para que siempre haya quien escuche `session_revoked` (cierre local). */
    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        // Autobloqueo al pasar toda la app a segundo plano (DEC-26).
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLocker)
    }

    @Suppress("DEPRECATION") // Los niveles siguen llegando; DEC-26 usa TRIM_MEMORY_BACKGROUND.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        autoLocker.onTrimMemory(level)
        pendingAuth.onTrimMemory(level)
    }
}
