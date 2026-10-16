package com.luis.end_security.feature.auth.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.BlobShape

/**
 * Contenido de cada página. Las posiciones salen de Figma (frames de 412 × 917, nodos
 * 19:5, 19:52 y 19:99) y están medidas desde el borde inferior de la barra superior,
 * dentro de un lienzo de [IllustrationCanvasWidth] × [IllustrationCanvasHeight].
 */
internal enum class OnboardingPage(
    @param:StringRes val titleBold: Int,
    @param:StringRes val titleRegular: Int,
    @param:StringRes val body: Int,
    @param:DrawableRes val icon: Int,
    @param:StringRes val iconDescription: Int,
    val illustration: Illustration,
) {
    // SCR-01
    Store(
        titleBold = R.string.onboarding_page1_title_bold,
        titleRegular = R.string.onboarding_page1_title_regular,
        body = R.string.onboarding_page1_body,
        icon = R.drawable.ic_key_40,
        iconDescription = R.string.onboarding_page1_illustration,
        illustration = Illustration(
            back = BlobPlacement(BlobShape.C, x = (-80).dp, y = 43.dp, width = 400.dp, height = 360.dp, grain = true),
            front = BlobPlacement(BlobShape.A, x = 270.dp, y = 233.dp, width = 220.dp, height = 210.dp),
            discX = 150.dp,
            discY = 161.dp,
        ),
    ),

    // SCR-02
    Private(
        titleBold = R.string.onboarding_page2_title_bold,
        titleRegular = R.string.onboarding_page2_title_regular,
        body = R.string.onboarding_page2_body,
        icon = R.drawable.ic_lock_40,
        iconDescription = R.string.onboarding_page2_illustration,
        illustration = Illustration(
            back = BlobPlacement(BlobShape.B, x = (-90).dp, y = 73.dp, width = 330.dp, height = 330.dp, grain = true),
            front = BlobPlacement(BlobShape.C, x = 250.dp, y = 13.dp, width = 220.dp, height = 190.dp),
            discX = 172.dp,
            discY = 161.dp,
        ),
    ),

    // SCR-03
    Alerts(
        titleBold = R.string.onboarding_page3_title_bold,
        titleRegular = R.string.onboarding_page3_title_regular,
        body = R.string.onboarding_page3_body,
        icon = R.drawable.ic_shield_check_40,
        iconDescription = R.string.onboarding_page3_illustration,
        illustration = Illustration(
            // En Figma la caja girada 12° ocupa (−166.69, 100) con 422.82 × 407.42;
            // aquí va la caja sin girar con el mismo centro.
            back = BlobPlacement(
                BlobShape.A,
                x = (-135.28).dp,
                y = 36.71.dp,
                width = 360.dp,
                height = 340.dp,
                grain = true,
                rotation = 12f,
            ),
            front = BlobPlacement(BlobShape.C, x = 236.dp, y = 153.dp, width = 230.dp, height = 210.dp),
            discX = 196.dp,
            discY = 229.dp,
        ),
    ),
}

/** Dos manchas como máximo por pantalla y solo una con grano (DEC-50). */
internal data class Illustration(
    val back: BlobPlacement,
    val front: BlobPlacement,
    val discX: Dp,
    val discY: Dp,
)

internal data class BlobPlacement(
    val shape: BlobShape,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val grain: Boolean = false,
    val rotation: Float = 0f,
)

internal val IllustrationCanvasWidth = 412.dp
internal val IllustrationCanvasHeight = 451.dp
