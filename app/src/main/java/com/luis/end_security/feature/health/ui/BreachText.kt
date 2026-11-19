package com.luis.end_security.feature.health.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.luis.end_security.R
import com.luis.end_security.feature.health.domain.BreachTimes

/** «Apareció N veces en filtraciones» (o «más de 10 000 veces»): el texto de la lista de Salud y del detalle (DEC-21). */
@Composable
internal fun breachSeenText(count: Int, detail: Boolean = false): String {
    val times = BreachTimes.of(count, LocalConfiguration.current.locales[0])
    return when {
        times.capped -> stringResource(R.string.health_breached_seen_capped, times.formatted)
        detail -> pluralStringResource(R.plurals.health_detail_breached_body, times.quantity, times.formatted)
        else -> pluralStringResource(R.plurals.health_breached_seen, times.quantity, times.formatted)
    }
}
