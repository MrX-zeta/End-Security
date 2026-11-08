package com.luis.end_security.feature.auth.data

import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.nulabinc.zxcvbn.WipeableString
import com.nulabinc.zxcvbn.Zxcvbn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * zxcvbn4j: todo ocurre en memoria, con sus diccionarios empaquetados; no hay red.
 *
 * `Strength.wipe()` llena de ceros la contraseña que recibió `measure` si es mutable
 * (`StringBuilder` o `WipeableString`), así que se mide sobre una copia propia: la entrada del
 * llamador nunca se modifica. La copia y lo que `wipe()` alcanza se borran al terminar. Limitación
 * (DEC-27): zxcvbn crea por dentro subcadenas y `String` temporales (diccionarios, expresiones
 * regulares) que no se pueden borrar; quedan en memoria hasta el recolector de basura.
 */
@Singleton
class ZxcvbnPasswordStrengthEstimator @Inject constructor() : PasswordStrengthEstimator {

    // Cargar los diccionarios tarda; se hace la primera vez que se estima, fuera del hilo principal.
    private val zxcvbn by lazy { Zxcvbn() }

    @Synchronized
    override fun score(password: CharSequence): Int {
        if (password.isEmpty()) return 0
        val copy = WipeableString(password)
        try {
            val strength = zxcvbn.measure(copy)
            try {
                return strength.score
            } finally {
                strength.wipe()
            }
        } finally {
            copy.wipe()
        }
    }
}
