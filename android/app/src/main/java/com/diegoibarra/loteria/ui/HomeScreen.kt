package com.diegoibarra.loteria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Rosa

@Composable
fun HomeScreen(vm: LoteriaViewModel) {
    val games by vm.games.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val minimums by vm.minimums.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val res = LocalResources.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.text(res)) } }
    LaunchedEffect(games) { games.forEach { vm.requestMinimum(it.boardCount, it.cells) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = ScreenClass(maxWidth, maxHeight)
        WallScaffold(
            screen = screen,
            title = stringResource(R.string.app_name),
            subtitle = if (games.isEmpty()) stringResource(R.string.no_games) else pluralStringResource(R.plurals.games_count, games.size, games.size),
            snackbar = snackbar,
            columns = 1,
            headerFraction = 0.3967f,
            onBack = null,
            actions = {},
            footer = listOf(FooterAction(stringResource(R.string.new_game), R.drawable.ic_add, { showNew = true }, primary = true)),
            spacing = 0.dp,
        ) {
            if (loaded && games.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { HomeEmpty() }
            }
            itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                val min = minimums[vm.minimumKey(game.boardCount, game.cells)]
                GameRow(
                    game = game,
                    minimum = min,
                    first = i == 0,
                    last = i == games.lastIndex,
                    fileFor = { vm.fileFor(game.id, it) },
                    onClick = { vm.openGame(game.id) },
                )
            }
        }
        NewGameDialog(showNew, screen, onDismiss = { showNew = false }) { name, boards, side ->
            showNew = false
            vm.createGame(name, boards, side)
        }
    }
}

@Composable
private fun GameRow(game: Game, minimum: Int?, first: Boolean, last: Boolean, fileFor: (com.diegoibarra.loteria.data.CardSpec) -> java.io.File, onClick: () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (first) 26.dp else 0.dp, topEnd = if (first) 26.dp else 0.dp,
        bottomStart = if (last) 26.dp else 0.dp, bottomEnd = if (last) 26.dp else 0.dp,
    )
    Column(Modifier.padding(top = if (first) 14.dp else 0.dp).clip(shape).background(Color.White)) {
        if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0xFFEEEEEE)))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardFan(game, fileFor, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.name, fontSize = 18.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.cards_count, game.cards.size, game.cards.size) + " · " +
                        stringResource(R.string.boards_summary, game.boardCount, game.boardSide, game.boardSide),
                    fontSize = 14.sp, color = Ink2,
                )
                if (minimum != null && game.cards.size < minimum) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_warn), null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pluralStringResource(R.plurals.missing_cards, minimum - game.cards.size, minimum - game.cards.size),
                            fontSize = 14.sp, color = Amber, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Icon(painterResource(R.drawable.ic_chev), null, tint = Ink2, modifier = Modifier.padding(12.dp).size(24.dp))
        }
    }
}

@Composable
private fun HomeEmpty() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(12.dp)).background(com.diegoibarra.loteria.ui.theme.RosaTint), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_cards), null, tint = Rosa, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.home_empty_title), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_body), fontSize = 16.sp, lineHeight = 23.sp, color = Ink2, textAlign = TextAlign.Center)
    }
}

/** Diálogo al pie: nombre, número de tableros y tamaño. */
@Composable
private fun NewGameDialog(visible: Boolean, screen: ScreenClass, onDismiss: () -> Unit, onCreate: (String, Int, Int) -> Unit) {
    var name by rememberSaveable(visible) { mutableStateOf("") }
    var boards by rememberSaveable(visible) { mutableIntStateOf(24) }
    var side by rememberSaveable(visible) { mutableIntStateOf(4) }
    OneUiDialog(
        visible = visible,
        title = stringResource(R.string.new_game),
        message = "",
        actions = listOf(
            DialogAction(stringResource(R.string.cancel), onDismiss),
            DialogAction(stringResource(R.string.create), { onCreate(name, boards, side) }, filled = true),
        ),
        onDismiss = onDismiss,
        widthFraction = screen.dialogWidthFraction,
    ) {
        GameSettingsFields(name, { name = it }, boards, { boards = it }, side, { side = it })
    }
}

/** Campos compartidos por "Nuevo juego" y Ajustes del juego. */
@Composable
fun GameSettingsFields(
    name: String, onName: (String) -> Unit,
    boards: Int, onBoards: (Int) -> Unit,
    side: Int, onSide: (Int) -> Unit,
    inDialog: Boolean = true,
) {
    Column {
        FieldLabel(stringResource(R.string.game_name))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(40)) },
            placeholder = { Text(stringResource(R.string.game_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedBorderColor = Rosa, unfocusedBorderColor = Color(0xFFDADADA),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldLabel(stringResource(R.string.boards_label))
        Stepper(boards, 1..60, onBoards, Modifier.fillMaxWidth(), container = if (inDialog) Color(0xFFF2F2F2) else Color.White)
        FieldLabel(stringResource(R.string.size_label))
        Segmented(
            listOf(stringResource(R.string.size_4), stringResource(R.string.size_3)),
            if (side == 4) 0 else 1,
            { onSide(if (it == 0) 4 else 3) },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
}
