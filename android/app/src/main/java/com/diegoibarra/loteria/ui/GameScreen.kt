package com.diegoibarra.loteria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.data.Card
import com.diegoibarra.loteria.ui.theme.Night
import com.diegoibarra.loteria.ui.theme.Night3
import com.diegoibarra.loteria.ui.theme.NightInk
import com.diegoibarra.loteria.ui.theme.NightInk2
import com.diegoibarra.loteria.ui.theme.Rosa
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

@Composable
fun GameScreen(vm: LoteriaViewModel) {
    val deck by vm.deck.collectAsStateWithLifecycle()
    val seed by vm.deckSeed.collectAsStateWithLifecycle()

    KeepScreenOn()

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        val screen = ScreenClass(maxWidth, maxHeight)
        if (deck.isNotEmpty()) {
            // Cada nueva baraja recrea el pager y vuelve a la primera carta.
            key(seed) {
                GameContent(
                    deck = deck,
                    initialPage = vm.currentPage,
                    screen = screen,
                    fileFor = vm::fileFor,
                    onPageSettled = vm::onPageSettled,
                    onRestart = vm::restart,
                    onExit = vm::exitGame,
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun GameContent(
    deck: List<Card>,
    initialPage: Int,
    screen: ScreenClass,
    fileFor: (Card) -> File,
    onPageSettled: (Int) -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val total = deck.size
    // Una página extra al final detecta el gesto "pasar de la última carta".
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, total)) { total + 1 }
    val scope = rememberCoroutineScope()
    var showExit by rememberSaveable { mutableStateOf(false) }
    val atEnd by remember { derivedStateOf { pagerState.settledPage == total } }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }
    BackHandler(enabled = !showExit && !atEnd) { showExit = true }

    val current = (pagerState.currentPage + 1).coerceAtMost(total)
    val askExit = { showExit = true }

    if (screen.isLandscape && !screen.isTablet) {
        // Teléfono horizontal: contador a la izquierda, botones a la derecha al alcance del pulgar.
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.width(196.dp).fillMaxHeight().padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                Counter(current, total, large = false, alignStart = true)
            }
            CardPager(pagerState, deck, fileFor, Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.width(196.dp).fillMaxHeight().padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                GameButton(R.string.restart, R.drawable.ic_refresh, onRestart, Modifier.fillMaxWidth())
                GameButton(R.string.exit, R.drawable.ic_exit, askExit, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Counter(
                current,
                total,
                large = screen.isTablet,
                modifier = Modifier.fillMaxWidth().height(if (screen.isTablet) 112.dp else 92.dp),
            )
            CardPager(pagerState, deck, fileFor, Modifier.weight(1f).fillMaxWidth())
            if (screen.isTablet) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    GameButton(R.string.restart, R.drawable.ic_refresh, onRestart, Modifier.width(260.dp), tall = true)
                    GameButton(R.string.exit, R.drawable.ic_exit, askExit, Modifier.width(260.dp), tall = true)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GameButton(R.string.restart, R.drawable.ic_refresh, onRestart, Modifier.weight(1f))
                    GameButton(R.string.exit, R.drawable.ic_exit, askExit, Modifier.weight(1f))
                }
            }
        }
    }

    OneUiDialog(
        visible = showExit,
        title = stringResource(R.string.exit_title),
        message = stringResource(R.string.exit_message),
        actions = listOf(
            DialogAction(stringResource(R.string.cancel), { showExit = false }),
            DialogAction(stringResource(R.string.exit), {
                showExit = false
                onExit()
            }),
        ),
        onDismiss = { showExit = false },
        widthFraction = screen.dialogWidthFraction,
        night = true,
    )
    OneUiDialog(
        visible = atEnd && !showExit,
        title = stringResource(R.string.end_title),
        message = pluralStringResource(R.plurals.end_message, total, total),
        actions = listOf(
            DialogAction(stringResource(R.string.exit), onExit),
            DialogAction(stringResource(R.string.restart), onRestart, filled = true),
        ),
        // Cerrar el aviso regresa a la última carta.
        onDismiss = { scope.launch { pagerState.animateScrollToPage(total - 1) } },
        widthFraction = screen.dialogWidthFraction,
        night = true,
        festive = true,
    )
}

@Composable
private fun GameButton(label: Int, icon: Int, onClick: () -> Unit, modifier: Modifier, tall: Boolean = false) {
    PillButton(
        text = stringResource(label),
        icon = icon,
        onClick = onClick,
        container = Night3,
        content = NightInk,
        height = if (tall) 60.dp else 54.dp,
        fontSize = if (tall) 17.sp else 15.sp,
        modifier = modifier,
    )
}

@Composable
private fun Counter(
    current: Int,
    total: Int,
    large: Boolean,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    val progress by animateFloatAsState(current.toFloat() / total, label = "progress")
    val description = stringResource(R.string.card_position, current, total)
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        val tabular = TextStyle(fontFeatureSettings = "tnum")
        Row {
            Text(
                "$current",
                style = tabular,
                color = NightInk,
                fontSize = if (large) 46.sp else 34.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                " / $total",
                style = tabular,
                color = NightInk2,
                fontSize = if (large) 28.sp else 22.sp,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(if (large) 200.dp else 132.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E2E2E)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Rosa))
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

/** Carta grande al centro; las vecinas asoman atenuadas para invitar a deslizar. */
@Composable
private fun CardPager(state: PagerState, deck: List<Card>, fileFor: (Card) -> File, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val minPeek = 24.dp
        val gap = 16.dp
        val cardWidth = min(maxWidth - minPeek * 2, maxHeight * CARD_RATIO)
        // Páginas a ancho completo (un gesto = una carta); las vecinas se acercan dibujándolas
        // desplazadas para que asomen junto a la carta actual.
        val pull = (maxWidth - cardWidth - gap).coerceAtLeast(0.dp)
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            // Una carta por gesto: un deslizamiento rápido nunca se salta una carta.
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
            key = { if (it < deck.size) deck[it].id else "fin" },
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = minPeek)
                    .graphicsLayer {
                        val signed = (page - state.currentPage) - state.currentPageOffsetFraction
                        val offset = signed.absoluteValue.coerceIn(0f, 1f)
                        translationX = -signed.coerceIn(-1.5f, 1.5f) * pull.toPx()
                        alpha = lerp(1f, 0.3f, offset)
                        scaleX = lerp(1f, 0.88f, offset)
                        scaleY = scaleX
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (page < deck.size) GameCard(fileFor(deck[page]), page + 1, deck.size)
            }
        }
    }
}

@Composable
private fun GameCard(file: File, position: Int, total: Int) {
    val context = LocalContext.current
    var ratio by remember(file) { mutableFloatStateOf(CARD_RATIO) }
    var failed by remember(file) { mutableStateOf(false) }
    val description = stringResource(R.string.card_position, position, total)
    // Tamaño acotado: fotos de 200 MP no saturan la memoria y se ven nítidas en la Tab.
    val request = remember(file) {
        ImageRequest.Builder(context).data(file).size(2560).scale(Scale.FIT).build()
    }
    if (failed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_broken_image), null, tint = NightInk2, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.image_error), color = NightInk2, fontSize = 15.sp)
        }
        return
    }
    // ContentScale.Fit: la imagen completa, con esquinas redondeadas sobre su propio contorno.
    AsyncImage(
        model = request,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
        },
        onError = { failed = true },
        modifier = Modifier.aspectRatio(ratio).clip(RoundedCornerShape(18.dp)),
    )
}
