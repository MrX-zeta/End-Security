package com.luis.end_security.feature.auth.domain

/**
 * Propiedades de Unicode que necesita la política de la contraseña maestra (DEC-31) y que Kotlin
 * puro no trae. En la app las da ICU de Android; en las pruebas, una implementación falsa.
 */
interface UnicodeCharacterData {

    /** Propiedad `Default_Ignorable_Code_Point`: caracteres que no se dibujan. */
    fun isDefaultIgnorable(codePoint: Int): Boolean

    /**
     * Versión de Unicode en que se asignó [codePoint], o `null` si no está asignado (o este
     * teléfono todavía no lo conoce).
     */
    fun age(codePoint: Int): UnicodeVersion?
}

data class UnicodeVersion(val major: Int, val minor: Int) : Comparable<UnicodeVersion> {

    override fun compareTo(other: UnicodeVersion): Int =
        compareValuesBy(this, other, UnicodeVersion::major, UnicodeVersion::minor)

    companion object {
        /** La versión de Unicode de Android 9 (minSdk 28): todos los teléfonos la conocen. */
        val UNICODE_10 = UnicodeVersion(10, 0)
    }
}
