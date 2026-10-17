package com.luis.end_security.feature.auth.data

import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.nulabinc.zxcvbn.Zxcvbn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * zxcvbn4j: todo ocurre en memoria, con sus diccionarios empaquetados; no hay red.
 * `measure` copia la contraseña a un búfer propio que `Strength.wipe()` llena de ceros.
 */
@Singleton
class ZxcvbnPasswordStrengthEstimator @Inject constructor() : PasswordStrengthEstimator {

    // Cargar los diccionarios tarda; se hace la primera vez que se estima, fuera del hilo principal.
    private val zxcvbn by lazy { Zxcvbn() }

    @Synchronized
    override fun score(password: CharSequence): Int {
        if (password.isEmpty()) return 0
        val strength = zxcvbn.measure(password)
        return try {
            strength.score
        } finally {
            strength.wipe()
        }
    }
}
