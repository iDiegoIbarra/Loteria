package com.diegoibarra.loteria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diegoibarra.loteria.LoteriaViewModel
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.ui.theme.Night
import com.diegoibarra.loteria.ui.theme.NightInk
import com.diegoibarra.loteria.ui.theme.NightInk2

@Composable
fun BoardViewerScreen(vm: LoteriaViewModel, onExport: () -> Unit) {
    val game by vm.current.collectAsStateWithLifecycle()
    val g = game ?: return
    if (g.boards.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val start by vm.viewerIndex.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, g.boards.lastIndex)) { g.boards.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { vm.openViewer(it) } }
    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = NightInk) }
            Text(
                stringResource(R.string.board_n_of, pager.currentPage + 1, g.boards.size),
                color = NightInk, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onExport) { Icon(painterResource(R.drawable.ic_share), stringResource(R.string.export_pdf), tint = NightInk) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !zoomed, beyondViewportPageCount = 1) { page ->
            ZoomableBoard(onZoomChange = { if (page == pager.currentPage) zoomed = it }) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    val w = min(maxWidth, maxHeight * CARD_RATIO)
                    Box(Modifier.width(w).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp)) {
                        BoardGrid(g, g.boards[page], { vm.fileFor(g.id, it) }, gap = 5.dp)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.viewer_hint), color = NightInk2, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
    }
}

/** Pellizcar para acercar (1×–4×), arrastrar cuando está ampliado, doble toque para restablecer. */
@Composable
private fun ZoomableBoard(onZoomChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                    onZoomChange(false)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val pan = event.calculatePan()
                            val maxX = size.width * (scale - 1) / 2
                            val maxY = size.height * (scale - 1) / 2
                            offset = if (scale <= 1.01f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            onZoomChange(scale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) { content() }
}
