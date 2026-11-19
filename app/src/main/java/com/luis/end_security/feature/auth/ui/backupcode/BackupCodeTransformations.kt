package com.luis.end_security.feature.auth.ui.backupcode

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import com.luis.end_security.BuildConfig
import com.luis.end_security.feature.auth.domain.BackupCodeFormat

/** El formato de los códigos del servidor de esta variante: Crockford en `demo`, el del backend real en `prod` (BR-10). */
val ActiveBackupCodeFormat: BackupCodeFormat =
    if (BuildConfig.USE_FAKE_BACKEND) BackupCodeFormat.CROCKFORD else BackupCodeFormat.SERVER

/**
 * Lo que se escribe o se pega queda en la forma del formato sin guion. En Crockford (DEC-12): mayúsculas, O → 0, I/L → 1,
 * sin espacios ni símbolos y como máximo 8; «k7qd 2m9x» se guarda «K7QD2M9X».
 */
object BackupCodeInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val normalized = ActiveBackupCodeFormat.normalize(asCharSequence())
        if (!asCharSequence().contentEquals(normalized)) replace(0, length, normalized)
    }
}

/**
 * El guion («XXXX-XXXX» o «xxxxx-xxxxx») solo se dibuja: no se guarda en el campo, así que borrar y mover el
 * cursor funcionan sobre los caracteres reales.
 */
object BackupCodeOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val group = ActiveBackupCodeFormat.groupSize
        if (length > group) insert(group, "-")
    }
}
