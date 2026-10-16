package com.luis.end_security.feature.auth.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.end_security.R
import com.luis.end_security.core.designsystem.component.NeumorphicDisc
import com.luis.end_security.core.designsystem.component.OrganicBlob
import com.luis.end_security.core.designsystem.component.OutlinedSmallButton
import com.luis.end_security.core.designsystem.component.PageIndicator
import com.luis.end_security.core.designsystem.component.PrimaryButton
import com.luis.end_security.core.designsystem.component.TextLink
import com.luis.end_security.core.designsystem.component.TwoWeightTitle
import com.luis.end_security.core.designsystem.theme.EndSecurityTheme
import com.luis.end_security.core.designsystem.theme.SystemBarsAppearance
import com.luis.end_security.core.designsystem.theme.rememberReducedMotion

// Medidas de Figma: barra superior a 13 de la barra de estado con 44 de alto, bloque de
// texto desde y = 548 y pie de 108 (botón 56 + 4 + enlace 48) que termina a 26 del borde.
private val HeaderTopPadding = 13.dp
private val HeaderHeight = 44.dp
private val TextBlockMinHeight = 235.dp
private val FooterHeight = 108.dp
private val FooterBottomPadding = 8.dp
private val ContentHorizontalPadding = 32.dp

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.NavigateToLogin -> currentOnFinished()
            }
        }
    }
    OnboardingScreen(state = state, onAction = viewModel::onAction)
}

/** SCR-01…SCR-03. Siempre en tema oscuro (DEC-51), sin importar el modo del sistema. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    EndSecurityTheme(darkTheme = true) {
        SystemBarsAppearance(darkBackground = true)
        val pages = OnboardingPage.entries
        val pagerState = rememberPagerState(initialPage = state.currentPage) { pages.size }
        val reducedMotion = rememberReducedMotion()
        val currentOnAction by rememberUpdatedState(onAction)

        // El ViewModel decide la página; el pager solo la refleja.
        LaunchedEffect(state.currentPage) {
            if (pagerState.currentPage != state.currentPage) {
                if (reducedMotion) {
                    pagerState.scrollToPage(state.currentPage)
                } else {
                    pagerState.animateScrollToPage(state.currentPage)
                }
            }
        }
        // Y el deslizamiento del usuario se informa al ViewModel cuando el pager se detiene.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                currentOnAction(OnboardingAction.PageSettled(page))
            }
        }
        BackHandler(enabled = state.currentPage > 0) { onAction(OnboardingAction.Previous) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // TalkBack: barra superior → página → pie, aunque se dibujen en otro orden.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 0f
                    },
                key = { index -> pages[index].name },
            ) { index ->
                OnboardingPageContent(
                    page = pages[index],
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                )
            }
            OnboardingHeader(
                showSkip = !state.isLastPage,
                reducedMotion = reducedMotion,
                onSkip = { onAction(OnboardingAction.Finish) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
            )
            OnboardingFooter(
                state = state,
                reducedMotion = reducedMotion,
                onAction = onAction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    },
            )
        }
    }
}

@Composable
private fun OnboardingHeader(
    showSkip: Boolean,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, top = HeaderTopPadding)
            .height(HeaderHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnimatedVisibility(
            visible = showSkip,
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut(),
        ) {
            OutlinedSmallButton(text = stringResource(R.string.onboarding_skip), onClick = onSkip)
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(HeaderTopPadding + HeaderHeight))
        // La ilustración conserva las medidas de Figma y se centra en el espacio libre;
        // en pantallas más bajas que el diseño se recorta por arriba y por abajo.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingIllustration(page)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TextBlockMinHeight)
                .padding(horizontal = ContentHorizontalPadding),
        ) {
            TwoWeightTitle(
                boldLine = stringResource(page.titleBold),
                regularLine = stringResource(page.titleRegular),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(page.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FooterHeight + FooterBottomPadding))
    }
}

@Composable
private fun OnboardingIllustration(page: OnboardingPage) {
    val illustration = page.illustration
    Box(Modifier.requiredSize(IllustrationCanvasWidth, IllustrationCanvasHeight)) {
        PlacedBlob(illustration.back)
        PlacedBlob(illustration.front)
        NeumorphicDisc(
            icon = page.icon,
            contentDescription = stringResource(page.iconDescription),
            modifier = Modifier.offset(x = illustration.discX, y = illustration.discY),
        )
    }
}

@Composable
private fun PlacedBlob(placement: BlobPlacement) {
    OrganicBlob(
        shape = placement.shape,
        grain = placement.grain,
        modifier = Modifier
            .offset(x = placement.x, y = placement.y)
            .requiredSize(placement.width, placement.height)
            .rotate(placement.rotation),
    )
}

@Composable
private fun OnboardingFooter(
    state: OnboardingUiState,
    reducedMotion: Boolean,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.isLastPage,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn() togetherWith fadeOut()
            } using SizeTransform(clip = false) // sin recortar la sombra del botón
        },
        contentAlignment = Alignment.BottomCenter,
        label = "pieOnboarding",
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ContentHorizontalPadding)
            .padding(bottom = FooterBottomPadding)
            .height(FooterHeight),
    ) { isLastPage ->
        if (isLastPage) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = { onAction(OnboardingAction.Finish) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextLink(
                    text = stringResource(R.string.onboarding_have_account),
                    onClick = { onAction(OnboardingAction.Finish) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageIndicator(pageCount = state.pageCount, currentPage = state.currentPage)
                    PrimaryButton(
                        text = stringResource(R.string.onboarding_next),
                        onClick = { onAction(OnboardingAction.Next) },
                        modifier = Modifier.widthIn(min = 168.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "Onboarding 1 (SCR-01)", device = "spec:width=412dp,height=917dp")
@Composable
private fun OnboardingPage1Preview() {
    OnboardingScreen(state = OnboardingUiState(currentPage = 0), onAction = {})
}

@Preview(name = "Onboarding 2 (SCR-02)", device = "spec:width=412dp,height=917dp")
@Composable
private fun OnboardingPage2Preview() {
    OnboardingScreen(state = OnboardingUiState(currentPage = 1), onAction = {})
}

@Preview(name = "Onboarding 3 (SCR-03)", device = "spec:width=412dp,height=917dp")
@Composable
private fun OnboardingPage3Preview() {
    OnboardingScreen(state = OnboardingUiState(currentPage = 2), onAction = {})
}

@Preview(name = "Onboarding · pantalla baja", device = "spec:width=360dp,height=740dp")
@Composable
private fun OnboardingSmallScreenPreview() {
    OnboardingScreen(state = OnboardingUiState(currentPage = 0), onAction = {})
}
