package com.diegoibarra.loteria.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.diegoibarra.loteria.data.CardKind
import com.diegoibarra.loteria.data.CardSpec
import com.diegoibarra.loteria.data.Game
import com.diegoibarra.loteria.data.GameRepository
import java.io.File

enum class PdfContent { BOARDS, CARDS, BOTH }

/** Dónde se imprime: tamaño de hoja, margen seguro y rebase (mm). */
enum class PdfTarget(val widthMm: Float, val heightMm: Float, val safeMm: Float, val bleedMm: Float, val label: String) {
    HOME_LETTER(215.9f, 279.4f, 6.35f, 0f, "Casa_Carta"),
    HOME_A4(210f, 297f, 6.35f, 0f, "Casa_A4"),
    PRINT_LETTER(215.9f, 279.4f, 5f, 3f, "Imprenta_Carta"),
}

/**
 * Genera los PDF con PdfDocument. Cada carta se decodifica una sola vez y se reutiliza en todas
 * las páginas, así el PDF incrusta cada imagen una vez.
 */
class PdfExporter(private val context: Context) {

    private val repo = GameRepository.get(context)
    private val pt = 72f / 25.4f

    fun export(game: Game, content: PdfContent, target: PdfTarget): File {
        val typeface = CardTemplate.typeface(context)
        val doc = PdfDocument()
        val cache = HashMap<String, Bitmap>()
        try {
            var page = 1
            if (content != PdfContent.CARDS && game.boards.isNotEmpty()) {
                game.boards.forEachIndexed { i, board ->
                    drawPage(doc, target, page++) { canvas, area ->
                        drawBoard(canvas, area, game, board, i + 1, cache, typeface)
                    }
                }
            }
            if (content != PdfContent.BOARDS) {
                game.cards.chunked(9).forEachIndexed { sheet, chunk ->
                    drawPage(doc, target, page++) { canvas, area ->
                        drawCardSheet(canvas, area, game, chunk, sheet * 9, cache, typeface)
                    }
                }
            }
            val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val what = when (content) { PdfContent.BOARDS -> "Tableros"; PdfContent.CARDS -> "Cartas"; PdfContent.BOTH -> "Tableros_y_Cartas" }
            val safeName = game.name.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "Loteria" }
            val file = File(dir, "${safeName}_${what}_${target.label}.pdf")
            file.outputStream().use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
            cache.values.forEach { it.recycle() }
        }
    }

    /** area = rectángulo seguro (en puntos) dentro de la hoja final, ya sin rebase. */
    private fun drawPage(doc: PdfDocument, t: PdfTarget, number: Int, body: (Canvas, RectF) -> Unit) {
        val bleed = t.bleedMm * pt
        val w = t.widthMm * pt + 2 * bleed
        val h = t.heightMm * pt + 2 * bleed
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), number).create())
        val safe = t.safeMm * pt
        val area = RectF(bleed + safe, bleed + safe, w - bleed - safe, h - bleed - safe)
        body(page.canvas, area)
        doc.finishPage(page)
    }

    private fun bitmapFor(game: Game, card: CardSpec, cache: HashMap<String, Bitmap>): Bitmap? =
        cache[card.id] ?: CardTemplate.decode(repo.renderFile(game.id, card), 720)?.let {
            val scaled = Bitmap.createScaledBitmap(it, 520, (520 / CARD_RATIO).toInt(), true)
            if (scaled !== it) it.recycle()
            scaled.also { b -> cache[card.id] = b }
        }

    private fun drawCard(canvas: Canvas, rect: RectF, game: Game, card: CardSpec, cache: HashMap<String, Bitmap>, typeface: Typeface) {
        val bmp = bitmapFor(game, card, cache) ?: return
        val radius = 2.4f * pt
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(bmp, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (card.kind == CardKind.TEMPLATE && card.design != null) {
            canvas.translate(rect.left, rect.top)
            canvas.scale(rect.width() / CardTemplate.W, rect.height() / CardTemplate.H)
            CardTemplate.drawNumber(canvas, game.numberOf(card.id), card.design.ink.toInt(), typeface)
        }
        canvas.restore()
    }

    private fun drawBoard(
        canvas: Canvas, area: RectF, game: Game, board: List<String>, number: Int,
        cache: HashMap<String, Bitmap>, typeface: Typeface,
    ) {
        val side = game.boardSide
        val gap = 3f * pt
        val footer = 5f * pt
        val availH = area.height() - footer
        val cardH = minOf((availH - (side - 1) * gap) / side, ((area.width() - (side - 1) * gap) / side) / CARD_RATIO)
        val cardW = cardH * CARD_RATIO
        val gridW = side * cardW + (side - 1) * gap
        val gridH = side * cardH + (side - 1) * gap
        val left = area.centerX() - gridW / 2
        val top = area.top + (availH - gridH) / 2
        val byId = game.cards.associateBy { it.id }
        board.forEachIndexed { i, id ->
            val card = byId[id] ?: return@forEachIndexed
            val x = left + (i % side) * (cardW + gap)
            val y = top + (i / side) * (cardH + gap)
            drawCard(canvas, RectF(x, y, x + cardW, y + cardH), game, card, cache, typeface)
        }
        footerText(canvas, area, "Tabla %02d de %d".format(number, game.boards.size))
    }

    /** Cartas para recortar: 3 × 3 por hoja, con línea de corte gris. */
    private fun drawCardSheet(
        canvas: Canvas, area: RectF, game: Game, cards: List<CardSpec>, offset: Int,
        cache: HashMap<String, Bitmap>, typeface: Typeface,
    ) {
        val gap = 4f * pt
        val footer = 5f * pt
        val availH = area.height() - footer
        val cardH = minOf((availH - 2 * gap) / 3, ((area.width() - 2 * gap) / 3) / CARD_RATIO)
        val cardW = cardH * CARD_RATIO
        val left = area.centerX() - (3 * cardW + 2 * gap) / 2
        val top = area.top + (availH - (3 * cardH + 2 * gap)) / 2
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.4f; color = 0xFFBDBDBD.toInt() }
        cards.forEachIndexed { i, card ->
            val x = left + (i % 3) * (cardW + gap)
            val y = top + (i / 3) * (cardH + gap)
            val r = RectF(x, y, x + cardW, y + cardH)
            drawCard(canvas, r, game, card, cache, typeface)
            canvas.drawRoundRect(r, 2.4f * pt, 2.4f * pt, cut)
        }
        footerText(canvas, area, "Cartas ${offset + 1}–${offset + cards.size} de ${game.cards.size}")
    }

    private fun footerText(canvas: Canvas, area: RectF, text: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5C5C5C.toInt()
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, area.centerX(), area.bottom - 1.2f * pt, p)
    }

    private companion object {
        const val CARD_RATIO = 1500f / 2125f
    }
}
