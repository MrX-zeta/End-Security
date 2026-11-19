package com.luis.end_security.feature.health.domain

import java.text.NumberFormat
import java.util.Locale

/**
 * Cuántas veces aparece una contraseña en las filtraciones (el número de HIBP no cuenta filtraciones, cuenta apariciones). Una sola
 * regla para la lista de Salud y para el detalle: de [BreachTimes.CAP] para arriba no se da la cifra exacta ([capped]); por debajo,
 * el número va con el separador de miles del idioma ([formatted]) y [quantity] elige «vez» o «veces».
 */
class BreachTimes private constructor(val quantity: Int, val capped: Boolean, val formatted: String) {
    companion object {
        const val CAP = 10_000

        fun of(count: Int, locale: Locale): BreachTimes {
            val format = NumberFormat.getIntegerInstance(locale)
            return if (count >= CAP) BreachTimes(CAP, true, format.format(CAP)) else BreachTimes(count, false, format.format(count))
        }
    }
}
