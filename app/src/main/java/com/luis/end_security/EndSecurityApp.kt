package com.luis.end_security

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.luis.end_security.core.security.SecretClipboardController
import com.luis.end_security.core.session.AutoLocker
import com.luis.end_security.core.sync.SyncScheduler
import com.luis.end_security.core.sync.SyncWorker
import com.luis.end_security.feature.auth.data.PendingAuth
import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.vault.data.DeletionUndoController
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

    /** El borrado con «Deshacer» pendiente se escribe al pasar a segundo plano (DEC-56). */
    @Inject
    lateinit var deletionUndo: DeletionUndoController

    /** Al volver a primer plano borra lo copiado si su temporizador no llegó a correr (DEC-25). */
    @Inject
    lateinit var secretClipboard: SecretClipboardController

    /** Sincroniza al desbloquear, al guardar, al volver a primer plano y al volver la red (tarea 12). */
    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        // Autobloqueo al pasar toda la app a segundo plano (DEC-26).
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLocker)
        ProcessLifecycleOwner.get().lifecycle.addObserver(deletionUndo)
        ProcessLifecycleOwner.get().lifecycle.addObserver(secretClipboard)
        ProcessLifecycleOwner.get().lifecycle.addObserver(syncScheduler)
        // Cada 15 min con red, aunque la app esté cerrada; sin la `vault_key` (DEC-47).
        SyncWorker.schedule(this)
    }

    @Suppress("DEPRECATION") // Los niveles siguen llegando; DEC-26 usa TRIM_MEMORY_BACKGROUND.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        autoLocker.onTrimMemory(level)
        pendingAuth.onTrimMemory(level)
    }
}
