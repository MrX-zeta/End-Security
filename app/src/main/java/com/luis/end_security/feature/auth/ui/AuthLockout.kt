package com.luis.end_security.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luis.end_security.R

/**
 * Bloqueo por intentos (DEC-32) en Iniciar sesión, 2FA y código de respaldo. El servidor dice
 * cuántos minutos esperar; cada ViewModel cuenta los segundos con su `Countdown`.
 */

/** Minutos que se muestran, hacia arriba: con 59 s todavía dice «1 min». */
fun lockoutMinutesLeft(secondsLeft: Int): Int = (secondsLeft + 59) / 60

/** «Demasiados intentos. Vuelve a intentarlo en N min.» */
@Composable
fun lockoutMessage(secondsLeft: Int): String =
    stringResource(R.string.auth_lockout, lockoutMinutesLeft(secondsLeft))
