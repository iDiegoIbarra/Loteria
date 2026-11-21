package com.diegoibarra.loteria.data

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Reparto balanceado de cartas en tableros (mismo algoritmo que tools/tableros/tableros.py):
 * cada carta aparece un número parejo de veces, ningún tablero se repite y dos tableros
 * comparten como máximo la mitad de sus casillas.
 */
object Boards {

    fun maxOverlap(cells: Int): Int = cells / 2

    /** Genera [count] tableros de [cells] casillas, o null si no hay reparto posible. */
    fun generate(
        ids: List<String>,
        count: Int,
        cells: Int,
        random: Random = Random.Default,
        attempts: Int = 400,
    ): List<List<String>>? {
        if (count <= 0 || ids.size < cells) return null
        // Primero semillas al azar (variedad); luego la secuencia con la que se validó el mínimo,
        // así nunca falla un juego que la validación declaró suficiente.
        val seeds = List(attempts) { random.nextLong() } + validationSeeds(ids.size, count, cells)
        for (seed in seeds) {
            val result = attempt(ids.size, count, cells, Random(seed)) ?: continue
            val shuffle = Random(seed xor 0x5DEECE66DL)
            return result.map { board -> board.map { ids[it] }.shuffled(shuffle) }
        }
        return null
    }

    private fun validationSeeds(n: Int, count: Int, cells: Int): List<Long> {
        val rnd = Random(n * 7919L + count * 31L + cells)
        return List(160) { rnd.nextLong() }
    }

    private val minimumCache = ConcurrentHashMap<Long, Int>()

    /** Menor número de cartas con el que se pueden generar los tableros (búsqueda binaria). */
    fun minimumCards(count: Int, cells: Int): Int {
        val key = count.toLong() shl 32 or cells.toLong()
        minimumCache[key]?.let { return it }
        val result = when {
            count <= 1 -> cells
            else -> {
                fun ok(n: Int) = feasible(n, count, cells)
                var lo = cells
                var hi = cells * 2
                while (!ok(hi)) {
                    lo = hi
                    hi *= 2
                    if (hi > 4096) return hi.also { minimumCache[key] = it }
                }
                while (lo + 1 < hi) {
                    val mid = (lo + hi) / 2
                    if (ok(mid)) hi = mid else lo = mid
                }
                hi
            }
        }
        minimumCache[key] = result
        return result
    }

    private fun feasible(n: Int, count: Int, cells: Int): Boolean =
        validationSeeds(n, count, cells).any { attempt(n, count, cells, Random(it)) != null }

    /** Un intento voraz: llena tablero por tablero respetando cuotas y el máximo de coincidencias. */
    internal fun attempt(n: Int, count: Int, cells: Int, rng: Random): List<IntArray>? {
        val maxOv = maxOverlap(cells)
        val total = count * cells
        val base = total / n
        val extra = total % n
        val quota = IntArray(n) { base }
        (0 until n).shuffled(rng).take(extra).forEach { quota[it]++ }

        val members = ArrayList<BooleanArray>(count)
        val boards = ArrayList<IntArray>(count)
        val keys = DoubleArray(n)
        for (b in 0 until count) {
            val remaining = count - b
            for (i in 0 until n) keys[i] = rng.nextDouble()
            val order = (0 until n).sortedWith(
                compareByDescending<Int> { if (quota[it] >= remaining) 1 else 0 }
                    .thenByDescending { quota[it] }
                    .thenBy { keys[it] },
            )
            val overlap = IntArray(members.size)
            val board = IntArray(cells)
            var size = 0
            for (c in order) {
                if (size == cells) break
                if (quota[c] == 0) continue
                var allowed = true
                for (p in members.indices) {
                    if (overlap[p] + (if (members[p][c]) 1 else 0) > maxOv) {
                        allowed = false
                        break
                    }
                }
                if (!allowed) continue
                for (p in members.indices) if (members[p][c]) overlap[p]++
                board[size++] = c
            }
            if (size < cells) return null
            val mask = BooleanArray(n)
            board.forEach {
                quota[it]--
                mask[it] = true
            }
            members += mask
            boards += board
        }
        return if (quota.any { it > 0 }) null else boards
    }
}
