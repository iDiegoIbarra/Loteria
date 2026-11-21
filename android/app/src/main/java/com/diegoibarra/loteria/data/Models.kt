package com.diegoibarra.loteria.data

import kotlinx.serialization.Serializable

/** IMAGE: imagen terminada (cartas migradas, con su número ya impreso). TEMPLATE: creada en el editor. */
@Serializable
enum class CardKind { IMAGE, TEMPLATE }

/**
 * Diseño de una carta de plantilla, en unidades de carta (1500 × 2125).
 * cx, cy y width ubican la imagen dentro del zigzag; los colores son ARGB.
 */
@Serializable
data class CardDesign(
    val outer: Long,
    val inner: Long,
    val zig: Long,
    val ink: Long,
    val legend: String,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val palette: List<Long> = emptyList(),
)

@Serializable
data class CardSpec(
    val id: String,
    val kind: CardKind,
    /** Archivo renderizado (sin número) dentro de games/<juego>/render. */
    val render: String,
    /** Imagen original dentro de games/<juego>/src (solo TEMPLATE). */
    val source: String? = null,
    val design: CardDesign? = null,
    val version: Int = 1,
    val addedAt: Long = 0,
)

@Serializable
data class Game(
    val id: String,
    val name: String,
    val boardCount: Int = 24,
    /** Lado del tablero: 4 (4×4) o 3 (3×3). */
    val boardSide: Int = 4,
    val cards: List<CardSpec> = emptyList(),
    /** Cada tablero es la lista de ids de carta en orden de casillas (fila por fila). */
    val boards: List<List<String>> = emptyList(),
    val boardsAt: Long? = null,
    val createdAt: Long = 0,
) {
    val cells: Int get() = boardSide * boardSide

    /** Número visible de cada carta: su posición en la baraja (se renumera al eliminar). */
    fun numberOf(cardId: String): Int = cards.indexOfFirst { it.id == cardId } + 1
}
