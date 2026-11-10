package com.diegoibarra.loteria.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.ui.theme.DisabledContainer
import com.diegoibarra.loteria.ui.theme.DisabledContent
import com.diegoibarra.loteria.ui.theme.Ink
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Night2
import com.diegoibarra.loteria.ui.theme.NightAccent
import com.diegoibarra.loteria.ui.theme.NightInk
import com.diegoibarra.loteria.ui.theme.NightInk2
import com.diegoibarra.loteria.ui.theme.PapelColors
import com.diegoibarra.loteria.ui.theme.Rosa
import com.diegoibarra.loteria.ui.theme.RosaInk
import kotlin.math.ceil

/** Proporción de las cartas del MVP (1500×2125). Las imágenes libres se muestran completas igual. */
const val CARD_RATIO = 1500f / 2125f

/** Clase de pantalla para aplicar las reglas de One UI en teléfono y tablet. */
@Immutable
data class ScreenClass(val width: Dp, val height: Dp) {
    val isLandscape: Boolean get() = width > height
    val isTablet: Boolean get() = minOf(width, height) >= 600.dp

    /** One UI: diálogo a ancho completo en teléfono vertical, 60% en horizontal o tablet vertical, 37.5% en tablet horizontal. */
    val dialogWidthFraction: Float
        get() = when {
            isTablet && isLandscape -> 0.375f
            isTablet || isLandscape -> 0.6f
            else -> 1f
        }
}

// ---------- Muro y papel picado ----------

/** Fondo rosa mexicano con una sombra diagonal, como un muro al sol. */
fun Modifier.wallBackground(): Modifier = this
    .background(Rosa)
    .drawBehind {
        val shade = Path().apply {
            moveTo(size.width * 0.62f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.62f - size.height * 0.55f, size.height)
            close()
        }
        drawPath(shade, Color.Black.copy(alpha = 0.10f))
    }

/** Banderita de papel picado en unidades de 50×54, con recortes (relleno par-impar). */
private fun flagPath(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    moveTo(0f, 0f)
    lineTo(50f, 0f)
    lineTo(50f, 46f)
    var x = 50f
    while (x > 0f) {
        lineTo(x - 5f, 54f)
        lineTo(x - 10f, 46f)
        x -= 10f
    }
    close()

    fun circle(cx: Float, cy: Float, r: Float) = addOval(Rect(Offset(cx, cy), r))
    fun diamond(cx: Float, cy: Float, s: Float) {
        moveTo(cx, cy - s)
        lineTo(cx + s, cy)
        lineTo(cx, cy + s)
        lineTo(cx - s, cy)
        close()
    }
    circle(25f, 20f, 6f)
    diamond(25f, 9f, 3f); diamond(25f, 31f, 3f); diamond(14f, 20f, 3f); diamond(36f, 20f, 3f)
    diamond(10f, 8f, 3.5f); diamond(40f, 8f, 3.5f); diamond(10f, 32f, 3.5f); diamond(40f, 32f, 3.5f)
    listOf(10f, 20f, 30f, 40f).forEach { circle(it, 41f, 1.6f) }
}

/** Tira de papel picado colgando de un hilo. La altura del modificador define la escala. */
@Composable
fun PapelPicado(
    modifier: Modifier = Modifier,
    stringColor: Color = Color.White.copy(alpha = 0.75f),
) {
    val flag = remember { flagPath() }
    Canvas(modifier.clipToBounds()) {
        val unit = size.height / 70f
        val flagStep = 58f * unit
        val sag = 10f * unit
        val top = 4f * unit
        val w = size.width
        val string = Path().apply {
            moveTo(-4f * unit, top)
            quadraticTo(w / 2f, top + sag * 2f, w + 4f * unit, top)
        }
        drawPath(string, stringColor, style = Stroke(width = 1.5.dp.toPx()))
        val count = ceil(w / flagStep).toInt() + 1
        for (i in 0 until count) {
            val x = i * flagStep + 4f * unit
            val t = ((x + 25f * unit) / w).coerceIn(0f, 1f)
            val y = top + sag * 4f * t * (1f - t)
            withTransform({
                translate(x, y)
                rotate(if (i % 2 == 0) -2.5f else 2.5f, Offset(25f * unit, 0f))
                scale(unit, unit, Offset.Zero)
            }) {
                drawPath(flag, PapelColors[i % PapelColors.size])
            }
        }
    }
}

// ---------- Botones ----------

/** Botón píldora con 20dp de margen lateral; el texto nunca se recorta ni toca el borde. */
@Composable
fun PillButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 54.dp,
    fontSize: TextUnit = 15.sp,
    disabledContainer: Color = DisabledContainer,
    disabledContent: Color = DisabledContent,
    shadow: Boolean = false,
) {
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier.height(height),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        ),
        elevation = if (shadow && enabled) ButtonDefaults.buttonElevation(defaultElevation = 4.dp) else null,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Círculo de selección estilo One UI. */
@Composable
fun SelectCircle(
    checked: Boolean,
    ring: Color,
    fill: Color,
    check: Color,
    modifier: Modifier = Modifier,
    idleFill: Color = Color.Transparent,
    checkedRing: Color = ring,
    size: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) fill else idleFill)
            .border(2.dp, if (checked) checkedRing else ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = check,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}

// ---------- Diálogo One UI (al pie de la pantalla) ----------

@Immutable
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val color: Color? = null,
    val filled: Boolean = false,
)

/**
 * Diálogo anclado abajo, al alcance del pulgar, como en One UI. Se dibuja sobre la pantalla
 * (sin ventana aparte) para controlar el ancho según el dispositivo.
 */
@Composable
fun OneUiDialog(
    visible: Boolean,
    title: String,
    message: String,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    widthFraction: Float,
    night: Boolean = false,
    festive: Boolean = false,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (night) 0.6f else 0.42f))
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = if (night) Night2 else Color.White,
                    contentColor = if (night) NightInk else Ink,
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .semantics { paneTitle = title },
                ) {
                    Column {
                        if (festive) PapelPicado(Modifier.fillMaxWidth().height(72.dp))
                        Column(
                            Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = if (festive) 8.dp else 24.dp,
                                bottom = 10.dp,
                            ),
                        ) {
                            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                message,
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                                color = if (night) NightInk2 else Ink2,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth().height(52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                actions.forEachIndexed { i, action ->
                                    if (i > 0 && !action.filled && !actions[i - 1].filled) {
                                        VerticalDivider(
                                            Modifier.height(24.dp),
                                            color = if (night) Color(0xFF333333) else Color(0xFFE3E3E3),
                                        )
                                    }
                                    if (action.filled) {
                                        Button(
                                            onClick = action.onClick,
                                            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = Rosa, contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                        ) {
                                            Text(action.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = action.onClick,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = action.color ?: if (night) NightAccent else RosaInk,
                                            ),
                                        ) {
                                            Text(action.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
