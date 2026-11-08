package com.luis.end_security.feature.auth.domain

/**
 * [UnicodeCharacterData] para pruebas en la JVM, donde no está ICU de Android.
 * - Ignorables: la tabla `Default_Ignorable_Code_Point` de DerivedCoreProperties.txt.
 * - Edad: la de [ages] si está; si no, «sin asignar» cuando la JVM no conoce el punto de código
 *   y 1.1 en otro caso.
 */
class FakeUnicodeCharacterData(
    private val ages: Map<Int, UnicodeVersion> = emptyMap(),
) : UnicodeCharacterData {

    override fun isDefaultIgnorable(codePoint: Int): Boolean = DEFAULT_IGNORABLE.any { codePoint in it }

    override fun age(codePoint: Int): UnicodeVersion? =
        ages[codePoint] ?: if (Character.getType(codePoint) == Character.UNASSIGNED.toInt()) null else UnicodeVersion(1, 1)

    private companion object {
        val DEFAULT_IGNORABLE = listOf(
            0x00AD..0x00AD,
            0x034F..0x034F,
            0x061C..0x061C,
            0x115F..0x1160,
            0x17B4..0x17B5,
            0x180B..0x180F,
            0x200B..0x200F,
            0x202A..0x202E,
            0x2060..0x206F,
            0x3164..0x3164,
            0xFE00..0xFE0F,
            0xFEFF..0xFEFF,
            0xFFA0..0xFFA0,
            0xFFF0..0xFFF8,
            0x1BCA0..0x1BCA3,
            0x1D173..0x1D17A,
            0xE0000..0xE0FFF,
        )
    }
}
