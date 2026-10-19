package com.luis.end_security

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.luis.end_security.core.session.AutoLocker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EndSecurityApp : Application() {

    @Inject
    lateinit var autoLocker: AutoLocker

    override fun onCreate() {
        super.onCreate()
        // Autobloqueo al pasar toda la app a segundo plano (DEC-26).
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLocker)
    }
}
