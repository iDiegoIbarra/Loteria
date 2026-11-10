package com.diegoibarra.loteria.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.data.Card
import com.diegoibarra.loteria.ui.theme.Danger
import com.diegoibarra.loteria.ui.theme.Ground
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Rosa
import com.diegoibarra.loteria.ui.theme.RosaInk
import com.diegoibarra.loteria.ui.theme.RosaTint
import java.io.File

/** Estado y acciones de la galería, para no pasar el ViewModel a cada pieza. */
private class GalleryUi(
    val cards: List<Card>,
    val loaded: Boolean,
    val importing: Boolean,
    val selectionMode: Boolean,
    val selected: Set<String>,
    val fileFor: (Card) -> File,
    val onAdd: () -> Unit,
    val onPlay: () -> Unit,
    val onTileClick: (String) -> Unit,
    val onTileLongPress: (String) -> Unit,
    val onEnterSelection: () -> Unit,
    val onToggleAll: () -> Unit,
    val onExitSelection: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onDeleteAll: () -> Unit,
) {
    val allSelected: Boolean get() = cards.isNotEmpty() && selected.size == cards.size
}

private val FooterSpace = 102.dp

@Composable
fun GalleryScreen(vm: LoteriaViewModel) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by vm.selected.collectAsStateWithLifecycle()
    val selected = remember(selectedIds, cards) {
        val existing = cards.mapTo(HashSet()) { it.id }
        selectedIds.filterTo(HashSet()) { it in existing }
    }

    var showDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var pendingSelected by rememberSaveable { mutableIntStateOf(0) }
    var showDeleteAll by rememberSaveable { mutableStateOf(false) }
    var pendingAll by rememberSaveable { mutableIntStateOf(0) }

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it.text(resources)) }
    }

    // Photo Picker nativo: sin permisos de almacenamiento; se pueden elegir varias imágenes.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.importImages(uris)
    }

    LaunchedEffect(selectionMode, cards.isEmpty(), loaded) {
        if (selectionMode && loaded && cards.isEmpty()) vm.exitSelection()
    }
    BackHandler(enabled = selectionMode) { vm.exitSelection() }

    val ui = GalleryUi(
        cards = cards,
        loaded = loaded,
        importing = importing,
        selectionMode = selectionMode,
        selected = selected,
        fileFor = vm::fileFor,
        onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onPlay = vm::startGame,
        onTileClick = { id -> if (selectionMode) vm.toggleSelection(id) },
        onTileLongPress = vm::onCardLongPress,
        onEnterSelection = vm::enterSelection,
        onToggleAll = vm::toggleSelectAll,
        onExitSelection = vm::exitSelection,
        onDeleteSelected = {
            pendingSelected = selected.size
            showDeleteSelected = true
        },
        onDeleteAll = {
            pendingAll = cards.size
            showDeleteAll = true
        },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        val screen = ScreenClass(maxWidth, maxHeight)
        if (screen.isLandscape) SidePaneGallery(ui, screen, snackbar) else PortraitGallery(ui, screen, snackbar)

        OneUiDialog(
            visible = showDeleteSelected,
            title = pluralStringResource(R.plurals.delete_selected_title, pendingSelected, pendingSelected),
            message = stringResource(R.string.delete_selected_message),
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { showDeleteSelected = false }),
                DialogAction(stringResource(R.string.delete), {
                    showDeleteSelected = false
                    vm.deleteSelected()
                }, color = Danger),
            ),
            onDismiss = { showDeleteSelected = false },
            widthFraction = screen.dialogWidthFraction,
        )
        OneUiDialog(
            visible = showDeleteAll,
            title = stringResource(R.string.delete_all_title),
            message = pluralStringResource(R.plurals.delete_all_message, pendingAll, pendingAll),
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { showDeleteAll = false }),
                DialogAction(stringResource(R.string.delete_all), {
                    showDeleteAll = false
                    vm.deleteAll()
                }, color = Danger),
            ),
            onDismiss = { showDeleteAll = false },
            widthFraction = screen.dialogWidthFraction,
        )
    }
}

// ---------- Teléfono y tablet en vertical: muro expandible arriba, acciones abajo ----------

@Composable
private fun PortraitGallery(ui: GalleryUi, screen: ScreenClass, snackbar: SnackbarHostState) {
    // One UI: barra expandida = 39.67% del alto en teléfono y 18.78% en tablet.
    val headerHeight = if (screen.isTablet) max(screen.height * 0.1878f, 260.dp) else screen.height * 0.3967f
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        if (ui.cards.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                ExpandedWall(ui, Modifier.fillMaxWidth().height(headerHeight))
                if (ui.loaded) EmptyState(Modifier.weight(1f).fillMaxWidth()) else Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(FooterSpace + navBottom))
            }
        } else {
            val gridState = rememberLazyGridState()
            val density = LocalDensity.current
            val collapsed by remember(headerHeight) {
                derivedStateOf {
                    gridState.firstVisibleItemIndex > 0 ||
                        with(density) { gridState.firstVisibleItemScrollOffset.toDp() } > headerHeight - 110.dp
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = FooterSpace + navBottom + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    ExpandedWall(
                        ui,
                        Modifier
                            .bleedHorizontally(24.dp)
                            .height(headerHeight)
                            .padding(bottom = 0.dp),
                    )
                }
                cardItems(ui)
            }
            AnimatedVisibility(
                visible = collapsed,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CompactBar(ui)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            AnimatedVisibility(
                visible = !ui.selectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                GalleryFooter(ui)
            }
        }
    }
}

/** Ensancha un elemento de la cuadrícula para que ignore el padding lateral (muro de borde a borde). */
private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun ExpandedWall(ui: GalleryUi, modifier: Modifier) {
    Column(modifier.wallBackground()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        PapelPicado(Modifier.fillMaxWidth().height(64.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = galleryTitle(ui),
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(galleryCountLine(ui), color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
        }
        BarActions(ui, onWall = true, compact = false, modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp))
    }
}

@Composable
private fun CompactBar(ui: GalleryUi) {
    Column(Modifier.fillMaxWidth().wallBackground()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                galleryTitle(ui),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!ui.selectionMode) {
                Text(galleryCountLine(ui), color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp)
            }
            BarActions(ui, onWall = true, compact = true)
        }
    }
}

@Composable
private fun GalleryFooter(ui: GalleryUi) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Ground.copy(alpha = 0f), 0.35f to Ground))
            .padding(top = 24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = stringResource(if (ui.importing) R.string.adding_images else R.string.add_images),
                icon = R.drawable.ic_add,
                onClick = ui.onAdd,
                container = RosaTint,
                content = RosaInk,
                loading = ui.importing,
            )
            PillButton(
                text = stringResource(R.string.play),
                icon = R.drawable.ic_play,
                onClick = ui.onPlay,
                container = Rosa,
                content = Color.White,
                enabled = ui.cards.isNotEmpty(),
                fontSize = 16.sp,
                shadow = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------- Horizontal (teléfono y tablet): muro como panel lateral ----------

@Composable
private fun SidePaneGallery(ui: GalleryUi, screen: ScreenClass, snackbar: SnackbarHostState) {
    val paneWidth = screen.width * if (screen.isTablet) 0.375f else 0.34f
    val hp = if (screen.isTablet) 40.dp else 24.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(paneWidth)
                .fillMaxHeight()
                .wallBackground()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            PapelPicado(Modifier.fillMaxWidth().height(if (screen.isTablet) 72.dp else 52.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = hp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    galleryTitle(ui),
                    color = Color.White,
                    fontSize = if (screen.isTablet) 52.sp else 34.sp,
                    lineHeight = if (screen.isTablet) 58.sp else 38.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(galleryCountLine(ui), color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
            }
            AnimatedVisibility(visible = !ui.selectionMode) {
                Column(
                    Modifier.fillMaxWidth().padding(start = hp, end = hp, bottom = 24.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val h = if (screen.isTablet) 60.dp else 54.dp
                    PillButton(
                        text = stringResource(R.string.play),
                        icon = R.drawable.ic_play,
                        onClick = ui.onPlay,
                        container = Color.White,
                        content = RosaInk,
                        enabled = ui.cards.isNotEmpty(),
                        height = h,
                        fontSize = 17.sp,
                        disabledContainer = Color.White.copy(alpha = 0.3f),
                        disabledContent = Color.White.copy(alpha = 0.8f),
                        shadow = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        text = stringResource(if (ui.importing) R.string.adding_images else R.string.add_images),
                        icon = R.drawable.ic_add,
                        onClick = ui.onAdd,
                        container = Color.White.copy(alpha = 0.18f),
                        content = Color.White,
                        loading = ui.importing,
                        height = h,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                BarActions(
                    ui,
                    onWall = false,
                    compact = !screen.isTablet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                )
                if (ui.cards.isEmpty()) {
                    if (ui.loaded) EmptyState(Modifier.weight(1f).fillMaxWidth())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End)),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        horizontalArrangement = Arrangement.spacedBy(if (screen.isTablet) 14.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (screen.isTablet) 14.dp else 8.dp),
                    ) {
                        cardItems(ui)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

// ---------- Piezas compartidas ----------

@Composable
private fun galleryTitle(ui: GalleryUi): String = when {
    !ui.selectionMode -> stringResource(R.string.app_name)
    ui.selected.isEmpty() -> stringResource(R.string.select_cards)
    else -> pluralStringResource(R.plurals.selected_count, ui.selected.size, ui.selected.size)
}

@Composable
private fun galleryCountLine(ui: GalleryUi): String {
    val n = ui.cards.size
    return when {
        ui.selectionMode -> pluralStringResource(R.plurals.of_cards, n, n)
        n == 0 -> stringResource(R.string.gallery_empty_subtitle)
        else -> pluralStringResource(R.plurals.cards_count, n, n)
    }
}

/** Acciones de la barra: menú ⋮ normalmente; "Todas", "Eliminar" y cerrar en modo selección. */
@Composable
private fun BarActions(ui: GalleryUi, onWall: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val color = if (onWall) Color.White else Ink
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (ui.selectionMode) {
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .toggleable(value = ui.allSelected, role = Role.Checkbox, onValueChange = { ui.onToggleAll() })
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectCircle(
                    checked = ui.allSelected,
                    ring = if (onWall) Color.White else Ink2,
                    fill = if (onWall) Color.White else Rosa,
                    check = if (onWall) Rosa else Color.White,
                    checkedRing = if (onWall) Color.White else Rosa,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.all), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            if (!compact) Spacer(Modifier.weight(1f))
            if (ui.selected.isNotEmpty()) {
                if (compact) {
                    IconButton(onClick = ui.onDeleteSelected) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete), tint = color)
                    }
                } else {
                    TextButton(onClick = ui.onDeleteSelected) {
                        Icon(painterResource(R.drawable.ic_delete), null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            IconButton(onClick = ui.onExitSelection) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_selection), tint = color)
            }
        } else {
            if (!compact) Spacer(Modifier.weight(1f))
            if (ui.cards.isNotEmpty()) MoreMenu(ui, color)
        }
    }
}

@Composable
private fun MoreMenu(ui: GalleryUi, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options), tint = tint)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select), fontSize = 17.sp, color = Ink) },
                onClick = {
                    open = false
                    ui.onEnterSelection()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_all), fontSize = 17.sp, color = Danger) },
                onClick = {
                    open = false
                    ui.onDeleteAll()
                },
            )
        }
    }
}

private fun LazyGridScope.cardItems(ui: GalleryUi) {
    itemsIndexed(ui.cards, key = { _, card -> card.id }) { index, card ->
        CardTile(
            file = ui.fileFor(card),
            number = index + 1,
            selectionMode = ui.selectionMode,
            selected = card.id in ui.selected,
            onClick = { ui.onTileClick(card.id) },
            onLongClick = { ui.onTileLongPress(card.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    file: File,
    number: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, label = "tileScale")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val description = stringResource(R.string.card_thumb_description, number)
    val context = LocalContext.current
    val request = remember(file) { ImageRequest.Builder(context).data(file).crossfade(true).build() }

    Box(
        Modifier
            .aspectRatio(CARD_RATIO)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (selected) Modifier.border(3.dp, Rosa, shape) else Modifier)
            .clip(shape)
            .background(Color(0xFFE4E4E4))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = stringResource(R.string.select),
            )
            .semantics {
                contentDescription = description
                if (selectionMode) this.selected = selected
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionMode) {
            SelectCircle(
                checked = selected,
                ring = Color.White,
                fill = Rosa,
                check = Color.White,
                idleFill = Color.Black.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 170.dp, height = 150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = 64.dp.toPx()
                val ch = 96.dp.toPx()
                val radius = CornerRadius(12.dp.toPx())
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                listOf(-16f to -34.dp, 16f to 34.dp).forEach { (degrees, dx) ->
                    rotate(degrees, pivot = center) {
                        val topLeft = Offset(center.x - cw / 2 + dx.toPx(), center.y - ch / 2 + 12.dp.toPx())
                        drawRoundRect(Color.White, topLeft, Size(cw, ch), radius)
                        drawRoundRect(
                            Color(0xFFBDBDBD), topLeft, Size(cw, ch), radius,
                            style = Stroke(width = 2.5.dp.toPx(), pathEffect = dash),
                        )
                    }
                }
            }
            Box(
                Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(12.dp)).background(RosaTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_photos), null, tint = Rosa, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.empty_title), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            fontSize = 16.sp,
            lineHeight = 23.sp,
            color = Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

