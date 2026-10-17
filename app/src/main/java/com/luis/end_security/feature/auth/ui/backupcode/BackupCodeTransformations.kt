package com.luis.end_security.feature.auth.ui.backupcode

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import com.luis.end_security.feature.auth.domain.BackupCodeFormat

/**
 * Lo que se escribe o se pega queda en la forma de Crockford sin guion (DEC-12): mayúsculas,
 * O → 0, I/L → 1, sin espacios ni símbolos y como máximo 8. «k7qd 2m9x» se guarda «K7QD2M9X».
 */
object BackupCodeInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val normalized = BackupCodeFormat.normalize(asCharSequence())
        if (!asCharSequence().contentEquals(normalized)) replace(0, length, normalized)
    }
}

/**
 * El guion de «XXXX-XXXX» solo se dibuja: no se guarda en el campo, así que borrar y mover el
 * cursor funcionan sobre los 8 caracteres reales.
 */
object BackupCodeOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length > GROUP) insert(GROUP, "-")
    }

    private const val GROUP = 4
}
