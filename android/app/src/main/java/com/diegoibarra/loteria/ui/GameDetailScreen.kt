package com.diegoibarra.loteria.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diegoibarra.loteria.EditorViewModel
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.Route
import com.diegoibarra.loteria.data.CardKind
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.render.PdfContent
import com.diegoibarra.loteria.render.PdfTarget
import com.diegoibarra.loteria.ui.theme.Danger
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Rosa
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val TAB_CARDS = 0
private const val TAB_BOARDS = 1
private const val TAB_SETTINGS = 2

@Composable
fun GameDetailScreen(vm: LoteriaViewModel, editor: EditorViewModel) {
    val game by vm.current.collectAsStateWithLifecycle()
    val g = game ?: return
    val tab by vm.tab.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by vm.selected.collectAsStateWithLifecycle()
    val minimums by vm.minimums.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val selected = remember(selectedIds, g) { g.cards.map { it.id }.toSet().let { ids -> selectedIds.filterTo(HashSet()) { it in ids } } }
    val minimum = minimums[vm.minimumKey(g.boardCount, g.cells)]
    LaunchedEffect(g.boardCount, g.cells) { vm.requestMinimum(g.boardCount, g.cells) }

    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.text(res)) } }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showMissing by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }

    // Ajustes (elevados aquí para que sus diálogos cubran la pantalla).
    var setName by rememberSaveable(g.id) { mutableStateOf(g.name) }
    var setBoards by rememberSaveable(g.id) { mutableIntStateOf(g.boardCount) }
    var setSide by rememberSaveable(g.id) { mutableIntStateOf(g.boardSide) }
    var confirmRegenerate by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteGame by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(setBoards, setSide) { vm.requestMinimum(setBoards, setSide * setSide) }
    val setMinimum = minimums[vm.minimumKey(setBoards, setSide * setSide)]

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            editor.startNew(g.id, uris)
            vm.go(Route.EDITOR)
        }
    }
    val createCards = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val below = minimum != null && g.cards.size < minimum

    LaunchedEffect(export) {
        val done = export as? LoteriaViewModel.ExportState.Done ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", done.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, done.file.name))
        vm.exportHandled()
        showExport = false
    }

    BackHandler(enabled = selectionMode) { vm.exitSelection() }
    val exportRequest by vm.exportRequest.collectAsStateWithLifecycle()
    LaunchedEffect(exportRequest) {
        if (exportRequest) {
            showExport = true
            vm.exportRequestHandled()
        }
    }
    LaunchedEffect(selectionMode, g.cards.isEmpty()) { if (selectionMode && g.cards.isEmpty()) vm.exitSelection() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = ScreenClass(maxWidth, maxHeight)
        val nav = listOf(
            NavItem(stringResource(R.string.tab_cards), R.drawable.ic_cards),
            NavItem(stringResource(R.string.tab_boards), R.drawable.ic_grid),
            NavItem(stringResource(R.string.tab_settings), R.drawable.ic_settings),
        )
        val summary = pluralStringResource(R.plurals.cards_count, g.cards.size, g.cards.size) + " · " +
            stringResource(R.string.boards_summary, g.boardCount, g.boardSide, g.boardSide)
        val (title, subtitle) = when {
            selectionMode -> (if (selected.isEmpty()) stringResource(R.string.select_cards) else pluralStringResource(R.plurals.selected_count, selected.size, selected.size)) to
                pluralStringResource(R.plurals.of_cards, g.cards.size, g.cards.size)
            tab == TAB_BOARDS -> g.name to (g.boardsAt?.let { stringResource(R.string.boards_generated, g.boards.size, g.boardSide, g.boardSide, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))) } ?: summary)
            tab == TAB_SETTINGS -> g.name to stringResource(R.string.game_settings)
            else -> g.name to summary
        }
        val play = FooterAction(stringResource(R.string.play), R.drawable.ic_play, {
            if (below) showMissing = true else vm.startPlay()
        }, primary = true, enabled = g.cards.isNotEmpty())
        val footer = when (tab) {
            TAB_CARDS -> if (g.cards.isEmpty() || below) {
                listOf(FooterAction(stringResource(R.string.create_cards), R.drawable.ic_add, createCards, primary = true))
            } else {
                listOf(FooterAction(stringResource(R.string.create_cards), R.drawable.ic_add, createCards), play)
            }
            TAB_BOARDS -> listOf(
                FooterAction(stringResource(R.string.export_pdf), R.drawable.ic_pdf, { if (g.boards.isEmpty()) showMissing = true else showExport = true }),
                play,
            )
            else -> emptyList()
        }

        WallScaffold(
            screen = screen,
            title = title,
            subtitle = subtitle,
            snackbar = snackbar,
            columns = when (tab) { TAB_CARDS -> 4; TAB_BOARDS -> if (screen.isTablet) 3 else 2; else -> 1 },
            headerFraction = 0.33f,
            onBack = if (selectionMode) null else vm::back,
            actions = { onWall ->
                if (selectionMode) SelectionActions(onWall, g, selected, vm, onDelete = { confirmDelete = true })
                else if (tab == TAB_CARDS && g.cards.isNotEmpty()) CardsMenu(onWall, vm::enterSelection)
            },
            footer = footer,
            nav = if (selectionMode) emptyList() else nav,
            navSelected = tab,
            onNav = vm::setTab,
            showFooter = !selectionMode,
            spacing = if (tab == TAB_BOARDS) 12.dp else 8.dp,
        ) {
            when (tab) {
                TAB_CARDS -> cardsTab(g, minimum, below, selectionMode, selected, vm, onEdit = { card ->
                    if (card.kind == CardKind.TEMPLATE) {
                        editor.startEdit(g.id, card)
                        vm.go(Route.EDITOR)
                    } else {
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.legacy_card_note)) }
                    }
                })
                TAB_BOARDS -> boardsTab(g, minimum, vm)
                else -> item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsTab(
                        g, setName, { setName = it }, setBoards, { setBoards = it }, setSide, { setSide = it }, setMinimum,
                        onSave = {
                            if (setBoards != g.boardCount || setSide != g.boardSide) confirmRegenerate = true
                            else vm.saveSettings(setName, setBoards, setSide)
                        },
                        onDelete = { confirmDeleteGame = true },
                    )
                }
            }
        }

        OneUiDialog(
            visible = confirmRegenerate,
            title = stringResource(R.string.save_changes_title),
            message = if (setMinimum != null && g.cards.size < setMinimum) {
                stringResource(R.string.settings_below_minimum, setMinimum, g.cards.size)
            } else {
                pluralStringResource(R.plurals.regenerate_settings, setBoards, setBoards, g.cards.size)
            },
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { confirmRegenerate = false }),
                DialogAction(stringResource(R.string.save_and_shuffle), {
                    confirmRegenerate = false
                    vm.saveSettings(setName, setBoards, setSide)
                }, filled = true),
            ),
            onDismiss = { confirmRegenerate = false },
            widthFraction = screen.dialogWidthFraction,
        )
        OneUiDialog(
            visible = confirmDeleteGame,
            title = stringResource(R.string.delete_game_title, g.name),
            message = stringResource(R.string.delete_game_message),
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { confirmDeleteGame = false }),
                DialogAction(stringResource(R.string.delete), {
                    confirmDeleteGame = false
                    vm.deleteGame()
                }, color = Danger),
            ),
            onDismiss = { confirmDeleteGame = false },
            widthFraction = screen.dialogWidthFraction,
        )

        val remaining = g.cards.size - selected.size
        OneUiDialog(
            visible = confirmDelete,
            title = pluralStringResource(R.plurals.delete_selected_title, selected.size, selected.size),
            message = if (minimum != null && remaining < minimum) {
                pluralStringResource(R.plurals.delete_below_minimum, remaining, remaining, minimum - remaining)
            } else {
                pluralStringResource(R.plurals.regenerate_after_delete, remaining, g.boardCount, remaining)
            },
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { confirmDelete = false }),
                DialogAction(stringResource(R.string.delete), { confirmDelete = false; vm.deleteSelected() }, color = Danger),
            ),
            onDismiss = { confirmDelete = false },
            widthFraction = screen.dialogWidthFraction,
        )
        if (minimum != null) {
            OneUiDialog(
                visible = showMissing,
                title = pluralStringResource(R.plurals.missing_cards, (minimum - g.cards.size).coerceAtLeast(1), (minimum - g.cards.size).coerceAtLeast(1)),
                message = stringResource(R.string.missing_explained, g.boardCount, g.boardSide, g.boardSide, minimum, g.cards.size),
                actions = listOf(
                    DialogAction(stringResource(R.string.fewer_boards), { showMissing = false; vm.setTab(TAB_SETTINGS) }),
                    DialogAction(stringResource(R.string.create_cards), { showMissing = false; createCards() }, filled = true),
                ),
                onDismiss = { showMissing = false },
                widthFraction = screen.dialogWidthFraction,
            )
        }
        ExportDialog(showExport, screen, export is LoteriaViewModel.ExportState.Working, onDismiss = { showExport = false }) { content, target ->
            vm.exportPdf(content, target)
        }
    }
}

@Composable
private fun SelectionActions(onWall: Boolean, g: Game, selected: Set<String>, vm: LoteriaViewModel, onDelete: () -> Unit) {
    val color = if (onWall) Color.White else Ink
    val all = g.cards.isNotEmpty() && selected.size == g.cards.size
    Row(
        Modifier.height(48.dp).clip(CircleShape).toggleable(all, role = Role.Checkbox) { vm.toggleSelectAll() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectCircle(all, ring = if (onWall) Color.White else Ink2, fill = if (onWall) Color.White else Rosa, check = if (onWall) Rosa else Color.White)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.all), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    if (selected.isNotEmpty()) {
        TextButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.delete), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
    IconButton(onClick = vm::exitSelection) {
        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_selection), tint = color)
    }
}

@Composable
private fun CardsMenu(onWall: Boolean, onSelect: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options), tint = if (onWall) Color.White else Ink)
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp), containerColor = Color.White) {
            DropdownMenuItem(text = { Text(stringResource(R.string.select), fontSize = 17.sp, color = Ink) }, onClick = { open = false; onSelect() })
        }
    }
}

// ---------- Cartas ----------

private fun LazyGridScope.cardsTab(
    g: Game, minimum: Int?, below: Boolean, selectionMode: Boolean, selected: Set<String>,
    vm: LoteriaViewModel, onEdit: (com.diegoibarra.loteria.data.CardSpec) -> Unit,
) {
    if (below && minimum != null && !selectionMode) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MissingCardsBanner(
                g.cards.size, minimum,
                stringResource(R.string.missing_banner_detail, g.boardCount, g.boardSide, g.boardSide, minimum),
                Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
    }
    if (g.cards.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { CardsEmpty() }
    }
    itemsIndexed(g.cards, key = { _, c -> c.id }) { i, card ->
        CardTile(
            g, card, i + 1, selectionMode, card.id in selected, vm,
            onClick = { if (selectionMode) vm.toggleSelection(card.id) else onEdit(card) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    g: Game, card: com.diegoibarra.loteria.data.CardSpec, number: Int, selectionMode: Boolean, selected: Boolean,
    vm: LoteriaViewModel, onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, label = "tile")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(R.string.card_thumb_description, number)
    Box(
        Modifier
            .aspectRatio(CARD_RATIO)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.border(3.dp, Rosa, shape) else Modifier)
            .clip(shape)
            .background(Color(0xFFE4E4E4))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.onCardLongPress(card.id)
                },
                onLongClickLabel = stringResource(R.string.select),
            )
            .semantics {
                contentDescription = description
                if (selectionMode) this.selected = selected
            },
    ) {
        CardImage(vm.fileFor(g.id, card), card, number, Modifier.fillMaxSize())
        if (selectionMode) {
            SelectCircle(
                selected, ring = Color.White, fill = Rosa, check = Color.White,
                idleFill = Color.Black.copy(alpha = 0.25f), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

@Composable
private fun CardsEmpty() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(12.dp)).background(com.diegoibarra.loteria.ui.theme.RosaTint), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_photos), null, tint = Rosa, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.empty_title), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body), fontSize = 16.sp, lineHeight = 23.sp, color = Ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ---------- Tableros ----------

private fun LazyGridScope.boardsTab(g: Game, minimum: Int?, vm: LoteriaViewModel) {
    if (g.boards.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (minimum != null && g.cards.size < minimum) {
                MissingCardsBanner(
                    g.cards.size, minimum,
                    stringResource(R.string.missing_banner_detail, g.boardCount, g.boardSide, g.boardSide, minimum),
                    Modifier.padding(top = 12.dp),
                )
            } else {
                Text(stringResource(R.string.boards_pending), color = Ink2, fontSize = 16.sp, modifier = Modifier.padding(vertical = 24.dp))
            }
        }
    }
    itemsIndexed(g.boards, key = { i, _ -> "b$i" }) { i, board ->
        Column(
            Modifier
                .padding(top = if (i < 3) 12.dp else 0.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable { vm.openViewer(i) }
                .padding(8.dp),
        ) {
            BoardGrid(g, board, { vm.fileFor(g.id, it) }, gap = 3.dp)
            Text(
                stringResource(R.string.board_n, i + 1), fontSize = 12.sp, color = Ink2,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---------- Ajustes ----------

@Composable
private fun SettingsTab(
    g: Game, name: String, onName: (String) -> Unit, boards: Int, onBoards: (Int) -> Unit,
    side: Int, onSide: (Int) -> Unit, minimum: Int?, onSave: () -> Unit, onDelete: () -> Unit,
) {
    val dirty = boards != g.boardCount || side != g.boardSide || name.trim() != g.name
    Column(Modifier.padding(top = 4.dp, bottom = 24.dp)) {
        GameSettingsFields(name, onName, boards, onBoards, side, onSide, inDialog = false)
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (minimum == null) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Rosa)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.computing_minimum), fontSize = 15.sp, color = Ink2)
            } else {
                Text(
                    stringResource(R.string.minimum_info, boards, side, side, minimum, g.cards.size),
                    fontSize = 15.sp, lineHeight = 21.sp, color = Ink2, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(if (g.cards.size >= minimum) R.drawable.ic_check else R.drawable.ic_warn), null,
                    tint = if (g.cards.size >= minimum) Color(0xFF00A35C) else Amber, modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PillButton(
            text = stringResource(R.string.save_changes), icon = R.drawable.ic_check, enabled = dirty,
            onClick = onSave, container = Rosa, content = Color.White, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = stringResource(R.string.delete_game), icon = R.drawable.ic_delete, onClick = onDelete,
            container = Color.White, content = Danger, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------- Exportar ----------

@Composable
private fun ExportDialog(
    visible: Boolean, screen: ScreenClass, working: Boolean,
    onDismiss: () -> Unit, onExport: (PdfContent, PdfTarget) -> Unit,
) {
    var content by rememberSaveable { mutableIntStateOf(0) }
    var target by rememberSaveable { mutableIntStateOf(0) }
    OneUiDialog(
        visible = visible,
        title = stringResource(R.string.export_pdf),
        message = "",
        actions = listOf(
            DialogAction(stringResource(R.string.cancel), onDismiss),
            DialogAction(stringResource(if (working) R.string.exporting else R.string.share_pdf), {
                if (!working) onExport(PdfContent.entries[content], PdfTarget.entries[target])
            }, filled = true),
        ),
        onDismiss = onDismiss,
        widthFraction = screen.dialogWidthFraction,
    ) {
        FieldLabel(stringResource(R.string.export_content))
        Segmented(
            listOf(stringResource(R.string.tab_boards), stringResource(R.string.tab_cards), stringResource(R.string.both)),
            content, { content = it }, Modifier.fillMaxWidth(),
        )
        FieldLabel(stringResource(R.string.export_where))
        listOf(
            R.string.target_home_letter to R.string.target_home_letter_hint,
            R.string.target_home_a4 to R.string.target_home_a4_hint,
            R.string.target_print to R.string.target_print_hint,
        ).forEachIndexed { i, (t, s) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(target == i, role = Role.RadioButton) { target = i }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (target == i) Rosa else Ink2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (target == i) Box(Modifier.size(12.dp).clip(CircleShape).background(Rosa)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(t), fontSize = 17.sp, color = Ink)
                    Text(stringResource(s), fontSize = 13.sp, color = Ink2)
                }
            }
        }
        if (working) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Rosa)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.exporting_detail), fontSize = 14.sp, color = Ink2)
            }
        }
    }
}
