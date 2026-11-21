package com.diegoibarra.loteria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.ui.theme.Ink2
import com.diegoibarra.loteria.ui.theme.Rosa
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** Selector libre: rueda (tono y saturación), brillo y código HEX. */
@Composable
fun ColorPickerDialog(
    visible: Boolean,
    initial: Long,
    title: String,
    widthFraction: Float,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val start = remember(initial, visible) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember(start) { mutableFloatStateOf(start[0]) }
    var sat by remember(start) { mutableFloatStateOf(start[1]) }
    var value by remember(start) { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hexText by remember(start) { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }

    OneUiDialog(
        visible = visible,
        title = title,
        message = "",
        actions = listOf(
            DialogAction(stringResource(R.string.cancel), onDismiss),
            DialogAction(stringResource(R.string.done), { onPick((argb.toLong() and 0xFFFFFFL) or 0xFF000000L) }, filled = true),
        ),
        onDismiss = onDismiss,
        widthFraction = widthFraction,
    ) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier.size(168.dp).pointerInput(Unit) {
                    awaitEachGesture {
                        fun pick(p: Offset) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = min(size.width, size.height) / 2f
                            val d = p - c
                            hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()) + 360) % 360).toFloat()
                            sat = (hypot(d.x, d.y) / r).coerceIn(0f, 1f)
                            hexText = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF)
                        }
                        val down = awaitFirstDown()
                        pick(down.position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { if (it.pressed) { pick(it.position); it.consume() } }
                        } while (event.changes.any { it.pressed })
                    }
                },
            ) {
                val r = size.minDimension / 2f
                drawCircle(Brush.sweepGradient(HUES), r)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                drawCircle(Color.Black.copy(alpha = 1f - value), r)
                val a = Math.toRadians(hue.toDouble())
                val p = Offset(center.x + (cos(a) * sat * r).toFloat(), center.y + (sin(a) * sat * r).toFloat())
                drawCircle(Color(argb), 11.dp.toPx(), p)
                drawCircle(Color.White, 11.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(argb))
                        .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val clean = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hexText = clean
                        if (clean.length == 6) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(0xFF000000.toInt() or clean.toInt(16), hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    prefix = { Text("#", color = Ink2) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rosa),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FieldLabel(stringResource(R.string.brightness))
        Slider(
            value = value,
            onValueChange = {
                value = it
                hexText = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, it)) and 0xFFFFFF)
            },
            colors = SliderDefaults.colors(thumbColor = Rosa, activeTrackColor = Rosa),
        )
    }
}
