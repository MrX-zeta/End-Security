package com.luis.end_security.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.luis.end_security.feature.unlock.domain.BiometricAvailability
import com.luis.end_security.feature.unlock.domain.BiometricStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.crypto.Cipher
import javax.inject.Inject

/** `BiometricManager` con **solo** `BIOMETRIC_STRONG` (DEC-09): el PIN del teléfono nunca abre la bóveda. */
class AndroidBiometricAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BiometricAvailability {
    override fun status(): BiometricStatus = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NoHardware
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NoneEnrolled
        else -> BiometricStatus.Unavailable
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricAvailabilityModule {
    @Binds
    abstract fun bindAvailability(impl: AndroidBiometricAvailability): BiometricAvailability
}

/** Textos del diálogo del sistema. */
class BiometricPromptTexts(val title: String, val subtitle: String?, val negativeButton: String)

/** Por qué terminó el diálogo sin éxito. */
enum class BiometricPromptFailure { Cancelled, Error }

/**
 * Muestra el `BiometricPrompt` con un `CryptoObject` y devuelve el `Cipher` ya autenticado. Solo `BIOMETRIC_STRONG`
 * y botón negativo (nunca credencial del dispositivo).
 */
fun FragmentActivity.authenticateWithCipher(
    cipher: Cipher,
    texts: BiometricPromptTexts,
    onSuccess: (Cipher) -> Unit,
    onFailure: (BiometricPromptFailure) -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val authenticated = result.cryptoObject?.cipher
            if (authenticated != null) onSuccess(authenticated) else onFailure(BiometricPromptFailure.Error)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            onFailure(if (cancelled) BiometricPromptFailure.Cancelled else BiometricPromptFailure.Error)
        }
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(texts.title)
        .apply { texts.subtitle?.let { setSubtitle(it) } }
        .setNegativeButtonText(texts.negativeButton)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

/** La actividad de Compose que aloja el diálogo, si hay una. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
