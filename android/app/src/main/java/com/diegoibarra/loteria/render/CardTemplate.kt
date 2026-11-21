package com.diegoibarra.loteria.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.diegoibarra.loteria.R
import com.diegoibarra.loteria.data.CardDesign
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Molde de las cartas, medido de las cartas originales (1500 × 2125):
 * marco exterior de color, marco interior crema y zigzag ondulado donde vive la imagen.
 */
object CardTemplate {
    const val W = 1500f
    const val H = 2125f
    const val CREAM = 0xFFF7EFE2
    const val LEGEND_MAX = 30
    private const val OUTER = 100f
    private const val INK_DARK = 0xFF1A1A1A
    private const val INK_LIGHT = 0xFFFFFFFF

    val zigRect = RectF(200f, 225f, 1300f, 1900f)
    /** Área para la imagen por defecto: el zigzag menos la franja del número y de la leyenda. */
    private val imageBox = RectF(270f, 430f, 1230f, 1590f)
    val centerX = W / 2
    val centerY = (zigRect.top + zigRect.bottom) / 2

    val zigPath: Path by lazy { wavyRect(zigRect, amp = 22f, len = 100f) }

    private fun wavyRect(r: RectF, amp: Float, len: Float): Path = Path().apply {
        val nx = (r.width() / len).roundToInt()
        val ny = (r.height() / len).roundToInt()
        val lx = r.width() / nx
        val ly = r.height() / ny
        moveTo(r.left, r.top)
        var x = r.left
        repeat(nx) {
            quadTo(x + lx / 4, r.top - amp, x + lx / 2, r.top)
            quadTo(x + 3 * lx / 4, r.top + amp, x + lx, r.top)
            x += lx
        }
        var y = r.top
        repeat(ny) {
            quadTo(r.right + amp, y + ly / 4, r.right, y + ly / 2)
            quadTo(r.right - amp, y + 3 * ly / 4, r.right, y + ly)
            y += ly
        }
        repeat(nx) {
            quadTo(x - lx / 4, r.bottom + amp, x - lx / 2, r.bottom)
            quadTo(x - 3 * lx / 4, r.bottom - amp, x - lx, r.bottom)
            x -= lx
        }
        repeat(ny) {
            quadTo(r.left - amp, y - ly / 4, r.left, y - ly / 2)
            quadTo(r.left + amp, y - 3 * ly / 4, r.left, y - ly)
            y -= ly
        }
        close()
    }

    @Volatile private var lexend: Typeface? = null

    fun typeface(context: Context): Typeface =
        lexend ?: (ResourcesCompat.getFont(context, R.font.lexend_regular) ?: Typeface.DEFAULT).also { lexend = it }

    /** Centro y ancho por defecto: la imagen completa dentro del área sobre la leyenda. */
    fun defaultPlacement(imageAspect: Float): Triple<Float, Float, Float> {
        val w = minOf(imageBox.width(), imageBox.height() * imageAspect)
        return Triple(imageBox.centerX(), imageBox.centerY(), w)
    }

    /** Dibuja la carta completa (sin número) en un canvas ya escalado a unidades de carta. */
    fun draw(canvas: Canvas, design: CardDesign, image: Bitmap?, typeface: Typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = design.outer.toInt()
        canvas.drawRect(0f, 0f, W, H, paint)
        paint.color = design.inner.toInt()
        canvas.drawRect(OUTER, OUTER, W - OUTER, H - OUTER, paint)
        paint.color = design.zig.toInt()
        canvas.drawPath(zigPath, paint)
        if (image != null) {
            val h = design.width * image.height / image.width
            canvas.save()
            canvas.clipPath(zigPath)
            val dst = RectF(design.cx - design.width / 2, design.cy - h / 2, design.cx + design.width / 2, design.cy + h / 2)
            canvas.drawBitmap(image, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        drawLegend(canvas, design.legend, design.ink.toInt(), typeface)
    }

    /** Guías rosas punteadas cuando la imagen queda centrada en X y/o Y. */
    fun drawGuides(canvas: Canvas, vertical: Boolean, horizontal: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE4007C.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 7f
            pathEffect = DashPathEffect(floatArrayOf(30f, 24f), 0f)
        }
        if (vertical) canvas.drawLine(centerX, zigRect.top, centerX, zigRect.bottom, p)
        if (horizontal) canvas.drawLine(zigRect.left, centerY, zigRect.right, centerY, p)
    }

    fun drawNumber(canvas: Canvas, number: Int, ink: Int, typeface: Typeface) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 82f
            this.typeface = typeface
        }
        canvas.drawText("# $number", 300f, 410f, p)
    }

    /** Leyenda en mayúsculas, centrada, hasta 2 líneas; el tamaño baja hasta que cabe. */
    fun legendLines(text: String, typeface: Typeface): Pair<List<String>, Float> {
        val t = text.trim().uppercase()
        if (t.isEmpty()) return emptyList<String>() to 0f
        val p = Paint().apply { this.typeface = typeface }
        val maxW = zigRect.width() - 120f
        val words = t.split(' ').filter { it.isNotEmpty() }
        var size = 104f
        while (size >= 48f) {
            p.textSize = size
            if (p.measureText(t) <= maxW) return listOf(t) to size
            if (words.size > 1 && size <= 92f) {
                val best = (1 until words.size)
                    .map { words.take(it).joinToString(" ") to words.drop(it).joinToString(" ") }
                    .minBy { (a, b) -> max(p.measureText(a), p.measureText(b)) }
                if (max(p.measureText(best.first), p.measureText(best.second)) <= maxW) {
                    return listOf(best.first, best.second) to size
                }
            }
            size -= 4f
        }
        return listOf(t) to 48f
    }

    private fun drawLegend(canvas: Canvas, text: String, ink: Int, typeface: Typeface) {
        val (lines, size) = legendLines(text, typeface)
        if (lines.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = size
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        val lineH = size * 1.14f
        var baseline = 1752f - lineH * lines.size / 2 + size * 0.82f
        lines.forEach {
            canvas.drawText(it, W / 2, baseline, p)
            baseline += lineH
        }
    }

    /** Renderiza la carta a 1500 × 2125 (sin número: se dibuja aparte porque cambia al renumerar). */
    fun render(design: CardDesign, image: Bitmap?, typeface: Typeface): Bitmap {
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), design, image, typeface)
        return bmp
    }

    fun inkFor(zig: Int): Long = if (ColorUtils.calculateLuminance(zig) > 0.42) INK_DARK else INK_LIGHT

    private fun opaque(c: Int): Long = (c.toLong() and 0xFFFFFFL) or 0xFF000000L

    /**
     * Colores automáticos: exterior = color dominante, zigzag = secundario en tono suave,
     * interior crema fijo y tinta blanca o negra según el contraste con el zigzag.
     */
    fun autoDesign(image: Bitmap, legend: String = ""): CardDesign {
        val swatches = paletteOf(image)
        val usable = swatches.filter { c -> hsl(c).let { it[1] > 0.12f && it[2] in 0.14f..0.86f } }.ifEmpty { swatches }
        val outer = usable.firstOrNull() ?: 0xFFE4007C.toInt()
        val second = usable.drop(1).firstOrNull { hueDistance(it, outer) > 28f || abs(hsl(it)[2] - hsl(outer)[2]) > 0.25f }
            ?: usable.getOrNull(1) ?: outer
        val zig = ColorUtils.blendARGB(second, Color.WHITE, 0.55f)
        val (cx, cy, w) = defaultPlacement(image.width.toFloat() / image.height)
        return CardDesign(
            outer = opaque(outer),
            inner = CREAM,
            zig = opaque(zig),
            ink = inkFor(zig),
            legend = legend,
            cx = cx,
            cy = cy,
            width = w,
            palette = swatches.take(6).map(::opaque),
        )
    }

    /** Colores de la imagen ordenados por presencia, ignorando píxeles transparentes. */
    fun paletteOf(image: Bitmap): List<Int> {
        val small = Bitmap.createScaledBitmap(image, 120, max(1, 120 * image.height / image.width), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val solid = px.filter { Color.alpha(it) > 200 }.toIntArray()
        if (solid.isEmpty()) return emptyList()
        val strip = Bitmap.createBitmap(solid, solid.size, 1, Bitmap.Config.ARGB_8888)
        val palette = Palette.from(strip).maximumColorCount(16).clearFilters().generate()
        return palette.swatches.sortedByDescending { it.population }.map { it.rgb or 0xFF000000.toInt() }
    }

    private fun hsl(c: Int) = FloatArray(3).also { ColorUtils.colorToHSL(c, it) }

    private fun hueDistance(a: Int, b: Int): Float {
        val d = abs(hsl(a)[0] - hsl(b)[0])
        return minOf(d, 360f - d)
    }

    /** Decodifica con submuestreo para no cargar fotos gigantes en memoria. */
    fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun hasTransparency(image: Bitmap): Boolean {
        if (!image.hasAlpha()) return false
        val s = Bitmap.createScaledBitmap(image, 64, max(1, 64 * image.height / image.width), false)
        val px = IntArray(s.width * s.height)
        s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        return px.any { Color.alpha(it) < 250 }
    }
}
