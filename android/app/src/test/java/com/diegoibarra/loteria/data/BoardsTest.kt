package com.diegoibarra.loteria.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardsTest {

    private fun check(cards: Int, count: Int, cells: Int) {
        val ids = (1..cards).map { "c$it" }
        val boards = Boards.generate(ids, count, cells, Random(42))
        assertNotNull("sin reparto para $cards cartas", boards)
        boards!!
        assertEquals(count, boards.size)
        boards.forEach { assertEquals(cells, it.toSet().size) }
        val sets = boards.map { it.toSet() }
        assertEquals("tableros repetidos", count, sets.toSet().size)
        for (i in sets.indices) for (j in i + 1 until sets.size) {
            assertTrue("comparten de más", (sets[i] intersect sets[j]).size <= cells / 2)
        }
        val uses = ids.map { id -> sets.count { id in it } }
        assertTrue("reparto disparejo: $uses", uses.max() - uses.min() <= 1)
    }

    @Test fun originalDeck() = check(54, 24, 16)

    @Test fun threeByThree() = check(30, 24, 9)

    @Test fun moreCardsThanSlots() = check(200, 6, 16)

    @Test fun minimumIsFeasibleAndTight() {
        val min = Boards.minimumCards(24, 16)
        for (n in min..min + 12) check(n, 24, 16)
        assertNull(Boards.generate((1 until min - 3).map { "c$it" }, 24, 16, Random(1)))
    }
}
