package com.diegoibarra.loteria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diegoibarra.loteria.EditorViewModel
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.Route
import com.diegoibarra.loteria.data.CardDesign
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.render.CardTemplate
import com.diegoibarra.loteria.ui.theme.Anil
import com.diegoibarra.loteria.ui.theme.Cempasuchil
import com.diegoibarra.loteria.ui.theme.Danger
import com.diegoibarra.loteria.ui.theme.Ground
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Jacaranda
import com.diegoibarra.loteria.ui.theme.Naranja
import com.diegoibarra.loteria.ui.theme.Night
import com.diegoibarra.loteria.ui.theme.Night2
import com.diegoibarra.loteria.ui.theme.NightInk
import com.diegoibarra.loteria.ui.theme.NightInk2
import com.diegoibarra.loteria.ui.theme.Rosa
import com.diegoibarra.loteria.ui.theme.RosaInk
import com.diegoibarra.loteria.ui.theme.RosaTint
import com.diegoibarra.loteria.ui.theme.Turquesa
import com.diegoibarra.loteria.ui.theme.Verde
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Elementos con color personalizable (la leyenda y el número no lo son). */
private enum class Element(val label: Int, val short: Int) {
    OUTER(R.string.el_outer, R.string.el_outer_short),
    INNER(R.string.el_inner, R.string.el_inner_short),
    ZIG(R.string.el_zig, R.string.el_zig),
}

private val APP_COLORS = listOf(Rosa, Cempasuchil, Verde, Anil, Naranja, Jacaranda, Turquesa, Color(CardTemplate.CREAM))

private fun CardDesign.colorOf(e: Element): Long = when (e) { Element.OUTER -> outer; Element.INNER -> inner; Element.ZIG -> zig }

private fun CardDesign.withColor(e: Element, c: Long): CardDesign = when (e) {
    Element.OUTER -> copy(outer = c)
    Element.INNER -> copy(inner = c)
    Element.ZIG -> copy(zig = c, ink = CardTemplate.inkFor(c.toInt()))
}

private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

/** Distancia (en unidades de carta) a la que la imagen se "pega" al centro. */
private const val SNAP = 45f

@Composable
fun EditorScreen(vm: LoteriaViewModel, editor: EditorViewModel) {
    val game by vm.current.collectAsStateWithLifecycle()
    val g = game ?: return
    val drafts by editor.drafts.collectAsStateWithLifecycle()
    val index by editor.index.collectAsStateWithLifecycle()
    val loading by editor.loading.collectAsStateWithLifecycle()
    val image by editor.image.collectAsStateWithLifecycle()
    val pngTip by editor.showPngTip.collectAsStateWithLifecycle()
    val minimums by vm.minimums.collectAsStateWithLifecycle()
    val minimum = minimums[vm.minimumKey(g.boardCount, g.cells)]
    LaunchedEffect(g.boardCount, g.cells) { vm.requestMinimum(g.boardCount, g.cells) }

    var element by rememberSaveable { mutableStateOf(Element.OUTER) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loading, drafts.isEmpty()) { if (!loading && drafts.isEmpty() && !saving) vm.go(Route.GAME) }
    LaunchedEffect(pngTip) { if (pngTip) { delay(7000); editor.dismissPngTip() } }

    val newCount = drafts.count { !it.existing }
    val close = { if (newCount > 0) confirmDiscard = true else { editor.cancel(); vm.go(Route.GAME) } }
    BackHandler(enabled = !showPicker && !confirmSave && !confirmDiscard) { close() }

    val draft = drafts.getOrNull(index)
    val number = draft?.let { d -> if (d.existing) g.numberOf(d.id) else g.cards.size + drafts.filter { !it.existing }.indexOf(d) + 1 } ?: 1
    val doSave = {
        saving = true
        scope.launch {
            val added = editor.save()
            if (added > 0) vm.notifyAdded(added)
            vm.go(Route.GAME)
        }
        Unit
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        val screen = ScreenClass(maxWidth, maxHeight)
        if (loading || draft == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Rosa)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preparing_cards), color = Ink2, fontSize = 16.sp)
            }
            return@BoxWithConstraints
        }
        val state = EditorState(
            design = draft.design, image = image, number = number, element = element,
            onElement = { element = it }, onDesign = { change -> editor.update(change) },
            onCustom = { showPicker = true },
        )
        val title = if (drafts.size > 1) stringResource(R.string.card_i_of_n, index + 1, drafts.size)
        else stringResource(if (draft.existing) R.string.edit_card else R.string.new_card)
        val saveLabel = if (newCount > 1) pluralStringResource(R.plurals.save_n_cards, newCount, newCount) else stringResource(R.string.save_card)
        val onSave = { if (newCount > 0) confirmSave = true else doSave() }

        if (screen.isLandscape) {
            TwoPaneEditor(state, title, saveLabel, index, drafts.size, saving, close, editor::goTo, onSave, pngTip, editor::dismissPngTip)
        } else {
            PhoneEditor(state, title, saveLabel, index, drafts.size, saving, close, editor::goTo, onSave, pngTip, editor::dismissPngTip)
        }

        ColorPickerDialog(
            visible = showPicker,
            initial = draft.design.colorOf(element),
            title = stringResource(element.label),
            widthFraction = screen.dialogWidthFraction,
            onDismiss = { showPicker = false },
            onPick = { c ->
                showPicker = false
                editor.update { it.withColor(element, c) }
            },
        )
        val total = g.cards.size + newCount
        OneUiDialog(
            visible = confirmSave,
            title = stringResource(R.string.save_changes_title),
            message = if (minimum != null && total < minimum) {
                pluralStringResource(R.plurals.save_below_minimum, newCount, newCount, minimum - total, g.boardCount)
            } else {
                pluralStringResource(R.plurals.regenerate_after_add, newCount, newCount, g.boardCount, total)
            },
            actions = listOf(
                DialogAction(stringResource(R.string.cancel), { confirmSave = false }),
                DialogAction(
                    stringResource(if (minimum != null && total < minimum) R.string.save else R.string.save_and_shuffle),
                    { confirmSave = false; doSave() }, filled = true,
                ),
            ),
            onDismiss = { confirmSave = false },
            widthFraction = screen.dialogWidthFraction,
        )
        OneUiDialog(
            visible = confirmDiscard,
            title = pluralStringResource(R.plurals.discard_title, newCount, newCount),
            message = stringResource(R.string.discard_message),
            actions = listOf(
                DialogAction(stringResource(R.string.keep_editing), { confirmDiscard = false }),
                DialogAction(stringResource(R.string.discard), {
                    confirmDiscard = false
                    editor.cancel()
                    vm.go(Route.GAME)
                }, color = Danger),
            ),
            onDismiss = { confirmDiscard = false },
            widthFraction = screen.dialogWidthFraction,
        )
    }
}

private class EditorState(
    val design: CardDesign,
    val image: android.graphics.Bitmap?,
    val number: Int,
    val element: Element,
    val onElement: (Element) -> Unit,
    val onDesign: ((CardDesign) -> CardDesign) -> Unit,
    val onCustom: () -> Unit,
)

// ---------- Teléfono: vista previa arriba, panel abajo ----------

@Composable
private fun PhoneEditor(
    s: EditorState, title: String, saveLabel: String, index: Int, total: Int, saving: Boolean,
    onClose: () -> Unit, onGoTo: (Int) -> Unit, onSave: () -> Unit, pngTip: Boolean, onTipDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            EditorTopBar(title, index, total, onClose, onGoTo, dark = false)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    CardPreview(s, Modifier.width(260.dp))
                }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) { DesignPanel(s, chips = true) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index > 0) {
                    PillButton(stringResource(R.string.previous), R.drawable.ic_back, { onGoTo(index - 1) }, RosaTint, RosaInk)
                }
                PillButton(saveLabel, R.drawable.ic_check, onSave, Rosa, Color.White, Modifier.weight(1f), loading = saving, shadow = true, fontSize = 16.sp)
            }
        }
        PngTip(pngTip, onTipDismiss, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp, end = 24.dp, bottom = 96.dp))
    }
}

// ---------- Tablet / horizontal: carta a la izquierda, capas a la derecha ----------

@Composable
private fun TwoPaneEditor(
    s: EditorState, title: String, saveLabel: String, index: Int, total: Int, saving: Boolean,
    onClose: () -> Unit, onGoTo: (Int) -> Unit, onSave: () -> Unit, pngTip: Boolean, onTipDismiss: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.52f).fillMaxHeight().background(Night2)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                EditorTopBar(title, index, total, onClose, onGoTo, dark = true)
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(contentAlignment = Alignment.Center) {
                        val w = minOf(maxWidth, maxHeight * CARD_RATIO)
                        CardPreview(s, Modifier.width(w))
                    }
                }
                Text(
                    stringResource(R.string.gesture_hint), color = NightInk2, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
            PngTip(pngTip, onTipDismiss, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp, end = 24.dp, top = 68.dp))
        }
        Column(
            Modifier.weight(0.48f).fillMaxHeight().background(Ground).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(stringResource(R.string.card_design), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                DesignPanel(s, chips = false)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index > 0) PillButton(stringResource(R.string.previous), R.drawable.ic_back, { onGoTo(index - 1) }, RosaTint, RosaInk)
                if (index < total - 1) PillButton(stringResource(R.string.next), R.drawable.ic_chev, { onGoTo(index + 1) }, Color.White, Ink)
                PillButton(saveLabel, R.drawable.ic_check, onSave, Rosa, Color.White, Modifier.weight(1f), loading = saving, shadow = true, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun EditorTopBar(title: String, index: Int, total: Int, onClose: () -> Unit, onGoTo: (Int) -> Unit, dark: Boolean) {
    val fg = if (dark) NightInk else Ink
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = fg) }
        Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (index < total - 1) {
            PillButton(
                stringResource(R.string.next), R.drawable.ic_chev, { onGoTo(index + 1) },
                if (dark) Color(0xFF333333) else RosaTint, if (dark) NightInk else RosaInk, height = 44.dp, fontSize = 14.sp,
            )
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/** Carta en vivo: pellizcar cambia el tamaño y arrastrar mueve la imagen; guías al centrar. */
@Composable
private fun CardPreview(s: EditorState, modifier: Modifier) {
    val context = LocalContext.current
    val typeface = remember { CardTemplate.typeface(context) }
    var rawX by remember(s.image) { mutableFloatStateOf(s.design.cx) }
    var rawY by remember(s.image) { mutableFloatStateOf(s.design.cy) }
    var guides by remember { mutableIntStateOf(0) }
    val snappedX = abs(s.design.cx - CardTemplate.centerX) < 0.5f
    val snappedY = abs(s.design.cy - CardTemplate.centerY) < 0.5f
    LaunchedEffect(guides) { if (guides > 0) { delay(900); guides = 0 } }
    val description = stringResource(R.string.preview_description, s.number, s.design.legend)

    Canvas(
        modifier
            .aspectRatio(CARD_RATIO)
            .shadow(14.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .pointerInput(s.image) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val k = CardTemplate.W / size.width
                    val cxC = centroid.x * k
                    val cyC = centroid.y * k
                    s.onDesign { d ->
                        val width = (d.width * zoom).coerceIn(120f, 7000f)
                        val applied = width / d.width
                        rawX = cxC + (rawX - cxC) * applied + pan.x * k
                        rawY = cyC + (rawY - cyC) * applied + pan.y * k
                        val x = if (abs(rawX - CardTemplate.centerX) < SNAP) CardTemplate.centerX else rawX
                        val y = if (abs(rawY - CardTemplate.centerY) < SNAP) CardTemplate.centerY else rawY
                        d.copy(cx = x, cy = y, width = width)
                    }
                    guides++
                }
            },
    ) {
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save()
            c.scale(size.width / CardTemplate.W, size.height / CardTemplate.H)
            CardTemplate.draw(c, s.design, s.image, typeface)
            CardTemplate.drawNumber(c, s.number, s.design.ink.toInt(), typeface)
            if (guides > 0) CardTemplate.drawGuides(c, snappedX, snappedY)
            c.restore()
        }
    }
}

@Composable
private fun DesignPanel(s: EditorState, chips: Boolean) {
    if (chips) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Element.entries.forEach { e -> ElementChip(e, s.design.colorOf(e), e == s.element) { s.onElement(e) } }
        }
    } else {
        Element.entries.forEach { e -> ElementRow(e, s.design.colorOf(e), e == s.element) { s.onElement(e) } }
    }
    val current = s.design.colorOf(s.element)
    if (s.design.palette.isNotEmpty()) {
        FieldLabel(stringResource(R.string.from_image))
        SwatchRow(s.design.palette, current, onCustom = null) { c -> s.onDesign { it.withColor(s.element, c) } }
    }
    FieldLabel(stringResource(R.string.loteria_colors))
    SwatchRow(APP_COLORS.map { it.toArgbLong() }, current, onCustom = s.onCustom) { c ->
        s.onDesign { it.withColor(s.element, c) }
    }
    FieldLabel(stringResource(R.string.legend))
    val context = LocalContext.current
    val typeface = remember { CardTemplate.typeface(context) }
    val lines = remember(s.design.legend) { CardTemplate.legendLines(s.design.legend, typeface).first.size }
    OutlinedTextField(
        value = s.design.legend,
        onValueChange = { v -> s.onDesign { it.copy(legend = v.uppercase().take(CardTemplate.LEGEND_MAX)) } },
        placeholder = { Text(stringResource(R.string.legend_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        supportingText = {
            Text(
                stringResource(R.string.legend_counter, s.design.legend.length, CardTemplate.LEGEND_MAX) +
                    if (lines > 0) " · " + pluralStringResource(R.plurals.lines, lines, lines) else "",
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = Rosa, unfocusedBorderColor = Color(0xFFDADADA),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Color.toArgbLong(): Long {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val gr = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (a.toLong() shl 24) or (r.toLong() shl 16) or (gr.toLong() shl 8) or b.toLong()
}

@Composable
private fun ElementChip(e: Element, color: Long, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(if (on) Ink else Color.White)
            .border(if (on) 0.dp else 1.dp, Color(0xFFDADADA), CircleShape)
            .selectable(on, role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(color.toInt())).border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(e.short), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) Color.White else Ink)
    }
}

@Composable
private fun ElementRow(e: Element, color: Long, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) Color(0xFFFCEAF3) else Color.Transparent)
            .selectable(on, role = Role.Tab, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color.toInt())).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(e.label), fontSize = 17.sp, color = Ink)
            Text(hex(color), fontSize = 13.sp, color = Ink2)
        }
        Icon(painterResource(R.drawable.ic_chev), null, tint = Ink2, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SwatchRow(colors: List<Long>, current: Long, onCustom: (() -> Unit)?, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { c ->
            val on = (c and 0xFFFFFF) == (current and 0xFFFFFF)
            Swatch(Color(c.toInt()), on, hex(c)) { onPick(c or 0xFF000000L) }
        }
        if (onCustom != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.sweepGradient(HUES))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.custom_color), onClick = onCustom),
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).then(if (on) Modifier.border(2.5.dp, Ink, CircleShape) else Modifier).padding(if (on) 5.dp else 0.dp)
            .clip(CircleShape).background(color).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    )
}

@Composable
private fun PngTip(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B2B2B)).clickable(onClick = onDismiss).padding(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_photos), null, tint = Color(0xFFFFC266), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.png_tip), color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}
