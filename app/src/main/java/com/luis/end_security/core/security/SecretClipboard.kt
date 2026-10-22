package com.luis.end_security.core.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luis.end_security.core.common.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** El ajuste `clipboard_clear_seconds` (KV-DATASTORE, grafo v1.5.3). */
interface ClipboardSettings {
    /** 15, 30, 60 o 120 s. */
    val clipboardClearSeconds: Flow<Int>

    companion object {
        /** DEC-25. */
        const val DEFAULT_SECONDS = 30

        val ALLOWED_SECONDS = setOf(15, 30, 60, 120)

        /** Un valor fuera de las opciones no debe desactivar la limpieza. */
        fun sanitize(seconds: Int?): Int = seconds?.takeIf { it in ALLOWED_SECONDS } ?: DEFAULT_SECONDS
    }
}

/** Copiar un dato sensible (DEC-25): se marca como sensible y se borra solo. */
interface SecretClipboard {
    /** Copia [text] y devuelve los segundos tras los que se borrará del portapapeles. */
    suspend fun copy(text: String): Int
}

/** Lo mínimo que se necesita del portapapeles del sistema; en pruebas, uno falso. */
interface ClipboardPort {
    /** Pone [text] con [label] y lo marca como sensible (`EXTRA_IS_SENSITIVE`). */
    fun set(label: String, text: String)

    /**
     * La etiqueta de lo que hay copiado, sin leer el contenido. Android 10+ solo la entrega a la app
     * que tiene el foco (o al teclado predeterminado): desde segundo plano devuelve `null`, igual que
     * con el portapapeles vacío.
     */
    fun currentLabel(): String?

    fun clear()
}

/**
 * [SecretClipboard] (DEC-25):
 * - el `ClipData` lleva `EXTRA_IS_SENSITIVE`, para que el teclado y la vista previa de Android 13+ no
 *   muestren la contraseña;
 * - a los `clipboard_clear_seconds` se borra, **solo si todavía tiene lo que copió la app**: cada
 *   copia lleva una etiqueta única y, si se puede ver que ya hay otra cosa copiada, no se toca.
 *   Con la app en segundo plano Android no deja ver qué hay (la etiqueta llega `null`); ahí no se
 *   puede comprobar y se borra, porque dejar la contraseña copiada es el riesgo mayor;
 * - copiar otra vez reinicia la cuenta.
 */
@Singleton
class SecretClipboardController @Inject constructor(
    private val port: ClipboardPort,
    private val settings: ClipboardSettings,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SecretClipboard, DefaultLifecycleObserver {

    private val lock = Any()
    private var clearJob: Job? = null

    override suspend fun copy(text: String): Int {
        val seconds = ClipboardSettings.sanitize(settings.clipboardClearSeconds.first())
        val label = LABEL_PREFIX + UUID.randomUUID()
        synchronized(lock) {
            port.set(label, text)
            clearJob?.cancel()
            clearJob = scope.launch {
                delay(seconds * 1_000L)
                // `null`: vacío, o no se puede leer desde segundo plano (ver ClipboardPort.currentLabel).
                val current = port.currentLabel()
                if (current == null || current == label) port.clear()
            }
        }
        return seconds
    }

    /**
     * La app vuelve a primer plano (la registra `EndSecurityApp`): ahora Android sí deja ver la etiqueta,
     * así que si lo copiado es de la app y su temporizador no corrió (proceso muerto o congelado en
     * segundo plano), se borra aquí.
     */
    override fun onStart(owner: LifecycleOwner) {
        if (port.currentLabel()?.startsWith(LABEL_PREFIX) == true) port.clear()
    }

    private companion object {
        const val LABEL_PREFIX = "end-security:"
    }
}

/** El portapapeles de Android. */
@Singleton
class AndroidClipboardPort @Inject constructor(
    @ApplicationContext context: Context,
) : ClipboardPort {

    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun set(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        // Literal de `ClipDescription.EXTRA_IS_SENSITIVE` (API 33); en versiones anteriores se ignora.
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(clip)
    }

    override fun currentLabel(): String? = manager.primaryClipDescription?.label?.toString()

    override fun clear() = manager.clearPrimaryClip()

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardModule {

    @Binds
    abstract fun bindClipboardPort(impl: AndroidClipboardPort): ClipboardPort

    @Binds
    abstract fun bindSecretClipboard(impl: SecretClipboardController): SecretClipboard
}
