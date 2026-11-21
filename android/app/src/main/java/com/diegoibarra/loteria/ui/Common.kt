package com.diegoibarra.loteria.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.data.CardKind
import com.diegoibarra.loteria.data.CardSpec
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.render.CardTemplate
import com.diegoibarra.loteria.ui.theme.Ground
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Rosa
import com.diegoibarra.loteria.ui.theme.RosaInk
import com.diegoibarra.loteria.ui.theme.RosaTint
import java.io.File

val Amber = Color(0xFFB26A00)
val AmberTint = Color(0xFFFFF1D6)
val AmberInk = Color(0xFF5A3A00)

/** Dibuja el número de la carta encima (solo cartas de plantilla; las importadas ya lo traen). */
@Composable
fun Modifier.cardNumber(card: CardSpec, number: Int): Modifier {
    val context = LocalContext.current
    val typeface = remember { CardTemplate.typeface(context) }
    val ink = card.design?.ink?.toInt()
    if (card.kind != CardKind.TEMPLATE || ink == null) return this
    return drawWithContent {
        drawContent()
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save()
            c.scale(size.width / CardTemplate.W, size.height / CardTemplate.H)
            CardTemplate.drawNumber(c, number, ink, typeface)
            c.restore()
        }
    }
}

/** Carta renderizada con su número dibujado encima (cambia al renumerar sin volver a renderizar). */
@Composable
fun CardImage(
    file: File,
    card: CardSpec,
    number: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.cardNumber(card, number),
    )
}

/** Tablero: cuadrícula de cartas sobre papel blanco. */
@Composable
fun BoardGrid(game: Game, board: List<String>, fileFor: (CardSpec) -> File, modifier: Modifier = Modifier, gap: Dp = 3.dp) {
    val byId = remember(game) { game.cards.associateBy { it.id } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        board.chunked(game.boardSide).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { id ->
                    val card = byId[id]
                    Box(Modifier.weight(1f).aspectRatio(CARD_RATIO).clip(RoundedCornerShape(gap)).background(Color(0xFFE4E4E4))) {
                        if (card != null) CardImage(fileFor(card), card, game.numberOf(id), Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Abanico de tres cartas para identificar un juego. */
@Composable
fun CardFan(game: Game, fileFor: (CardSpec) -> File, height: Dp) {
    val cards = game.cards.take(3)
    Box(Modifier.size(width = height * 1.15f, height = height)) {
        if (cards.isEmpty()) {
            Box(
                Modifier.align(Alignment.Center).size(height * 0.62f, height * 0.88f).clip(RoundedCornerShape(8.dp)).background(RosaTint),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_photos), null, tint = Rosa, modifier = Modifier.size(height * 0.3f)) }
        }
        cards.forEachIndexed { i, card ->
            CardImage(
                fileFor(card),
                card,
                i + 1,
                Modifier
                    .padding(start = height * 0.2f * i, top = if (i == 1) 0.dp else height * 0.06f)
                    .height(height * 0.88f)
                    .aspectRatio(CARD_RATIO)
                    .layout { m, c ->
                        val p = m.measure(c)
                        layout(p.width, p.height) { p.placeWithLayer(0, 0) { rotationZ = (i - 1) * 8f } }
                    }
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

// ---------- Muro con scroll: el esqueleto de Inicio y Detalle ----------

@Immutable
data class FooterAction(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

@Immutable
data class NavItem(val label: String, @DrawableRes val icon: Int)

/**
 * En vertical: muro expandido (One UI) que se desplaza con el contenido y colapsa a barra compacta;
 * acciones en el footer y barra inferior opcional. En horizontal: el muro es un panel lateral con
 * la navegación y las acciones; el contenido va a la derecha.
 */
@Composable
fun WallScaffold(
    screen: ScreenClass,
    title: String,
    subtitle: String,
    snackbar: SnackbarHostState,
    columns: Int,
    headerFraction: Float,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.(onWall: Boolean) -> Unit,
    footer: List<FooterAction>,
    nav: List<NavItem> = emptyList(),
    navSelected: Int = 0,
    onNav: (Int) -> Unit = {},
    showFooter: Boolean = true,
    spacing: Dp = 8.dp,
    content: LazyGridScope.() -> Unit,
) {
    if (screen.isLandscape) {
        SidePaneLayout(screen, title, subtitle, snackbar, columns, onBack, actions, footer, nav, navSelected, onNav, showFooter, content)
        return
    }
    val headerHeight = if (screen.isTablet) max(screen.height * 0.1878f, 260.dp) else screen.height * headerFraction
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = (if (showFooter && footer.isNotEmpty()) 90.dp else 0.dp) + (if (nav.isNotEmpty()) 72.dp else navBottom) + 16.dp
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val collapsed by remember(headerHeight) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                with(density) { gridState.firstVisibleItemScrollOffset.toDp() } > headerHeight - 110.dp
        }
    }
    Box(Modifier.fillMaxSize().background(Ground)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomSpace),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            item(key = "wall", span = { GridItemSpan(maxLineSpan) }) {
                ExpandedWall(title, subtitle, onBack, actions, Modifier.bleed(24.dp).height(headerHeight))
            }
            content()
        }
        AnimatedVisibility(collapsed, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().wallBackground()) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) BackButton(onBack, Color.White) else Spacer(Modifier.width(16.dp))
                    Text(
                        title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    actions(true)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            if (showFooter && footer.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(0f to Ground.copy(alpha = 0f), 0.35f to Ground))
                        .padding(top = 24.dp)
                        .then(if (nav.isEmpty()) Modifier.navigationBarsPadding() else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        footer.forEach { a ->
                            PillButton(
                                text = a.label, icon = a.icon, onClick = a.onClick, enabled = a.enabled, loading = a.loading,
                                container = if (a.primary) Rosa else RosaTint,
                                content = if (a.primary) Color.White else RosaInk,
                                shadow = a.primary, fontSize = if (a.primary) 16.sp else 15.sp,
                                modifier = if (a.primary || footer.size == 1) Modifier.weight(1f) else Modifier,
                            )
                        }
                    }
                }
            }
            if (nav.isNotEmpty()) BottomNav(nav, navSelected, onNav)
        }
    }
}

@Composable
private fun SidePaneLayout(
    screen: ScreenClass,
    title: String,
    subtitle: String,
    snackbar: SnackbarHostState,
    columns: Int,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.(onWall: Boolean) -> Unit,
    footer: List<FooterAction>,
    nav: List<NavItem>,
    navSelected: Int,
    onNav: (Int) -> Unit,
    showFooter: Boolean,
    content: LazyGridScope.() -> Unit,
) {
    val hp = if (screen.isTablet) 40.dp else 24.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(Modifier.fillMaxSize().background(Ground)) {
        Column(
            Modifier
                .width(screen.width * if (screen.isTablet) 0.375f else 0.36f)
                .fillMaxHeight()
                .wallBackground()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box {
                PapelPicado(Modifier.fillMaxWidth().height(if (screen.isTablet) 72.dp else 52.dp))
                if (onBack != null) Box(Modifier.padding(start = 8.dp, top = if (screen.isTablet) 64.dp else 44.dp)) { BackButton(onBack, Color.White) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = hp), verticalArrangement = Arrangement.Center) {
                Text(
                    title, color = Color.White, fontSize = if (screen.isTablet) 48.sp else 32.sp,
                    lineHeight = if (screen.isTablet) 54.sp else 36.sp, fontWeight = FontWeight.Light, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
                if (nav.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    nav.forEachIndexed { i, item ->
                        val on = i == navSelected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .selectable(on, role = Role.Tab) { onNav(i) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(item.icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(item.label, color = Color.White, fontSize = 17.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
            if (showFooter && footer.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(start = hp, end = hp, bottom = 24.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    footer.sortedByDescending { it.primary }.forEach { a ->
                        PillButton(
                            text = a.label, icon = a.icon, onClick = a.onClick, enabled = a.enabled, loading = a.loading,
                            container = if (a.primary) Color.White else Color.White.copy(alpha = 0.18f),
                            content = if (a.primary) RosaInk else Color.White,
                            disabledContainer = Color.White.copy(alpha = 0.3f), disabledContent = Color.White.copy(alpha = 0.8f),
                            height = if (screen.isTablet) 60.dp else 54.dp, fontSize = 16.sp, shadow = a.primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) { actions(false) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End)),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp + navBottom),
                    horizontalArrangement = Arrangement.spacedBy(if (screen.isTablet) 14.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (screen.isTablet) 14.dp else 8.dp),
                    content = content,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun ExpandedWall(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.(onWall: Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.wallBackground()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        PapelPicado(Modifier.fillMaxWidth().height(64.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackButton(onBack, Color.White)
            Spacer(Modifier.weight(1f))
            actions(true)
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit, tint: Color) {
    androidx.compose.material3.IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_back), androidx.compose.ui.res.stringResource(R.string.back), tint = tint)
    }
}

/** Ensancha un elemento de la cuadrícula para que ignore el padding lateral (muro de borde a borde). */
fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun BottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp)) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).selectable(on, role = Role.Tab) { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(CircleShape).background(if (on) RosaTint else Color.Transparent).padding(horizontal = 18.dp, vertical = 3.dp),
                ) { Icon(painterResource(item.icon), null, tint = if (on) RosaInk else Ink2, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) RosaInk else Ink2)
            }
        }
    }
}

/** Aviso ámbar: faltan cartas para generar los tableros. */
@Composable
fun MissingCardsBanner(have: Int, minimum: Int, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(AmberTint).padding(14.dp)) {
        Icon(painterResource(R.drawable.ic_warn), null, tint = Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.missing_cards, minimum - have, minimum - have),
                color = AmberInk, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            Text(detail, color = AmberInk, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Amber.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(have.toFloat() / minimum).clip(CircleShape).background(Amber))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                androidx.compose.ui.res.stringResource(R.string.missing_progress, have, minimum),
                color = AmberInk, fontSize = 13.sp,
            )
        }
    }
}

/** Selector segmentado tipo One UI. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(Color(0xFFE6E6E6)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (on) Ink else Ink2, maxLines = 1)
            }
        }
    }
}

/** Contador con − y + para números pequeños (tableros). */
@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier, container: Color = Color.White) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(container).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (enabled) RosaTint else Color(0xFFF0F0F0))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 22.sp, color = if (enabled) RosaInk else Ink2) }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink2, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}
