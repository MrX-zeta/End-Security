package com.luis.end_security.feature.auth.data

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import com.luis.end_security.feature.auth.domain.UnicodeCharacterData
import com.luis.end_security.feature.auth.domain.UnicodeVersion
import javax.inject.Inject

/** [UnicodeCharacterData] con ICU de Android (API 24+). */
class IcuUnicodeCharacterData @Inject constructor() : UnicodeCharacterData {

    override fun isDefaultIgnorable(codePoint: Int): Boolean =
        UCharacter.hasBinaryProperty(codePoint, UProperty.DEFAULT_IGNORABLE_CODE_POINT)

    override fun age(codePoint: Int): UnicodeVersion? {
        // ICU devuelve 0.0 para lo que no está asignado en su versión de Unicode.
        val age = UCharacter.getAge(codePoint)
        if (age.major == 0 && age.minor == 0) return null
        return UnicodeVersion(age.major, age.minor)
    }
}
