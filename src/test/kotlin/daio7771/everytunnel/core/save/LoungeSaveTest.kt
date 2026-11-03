package daio7771.everytunnel.core.save

import daio7771.everytunnel.core.puzzle.OrderPuzzle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoungeSaveTest {

    @Test
    fun `it comes back as it went`() {
        val save = LoungeSave(
            records = listOf(2, 0, 3, 1), recordsDone = 2, reel = listOf(1), darts = listOf(19, 7, 18), dartsFailures = 4,
            breakers = listOf(true, false, true, false, true), powered = true, counter = 5, filmSeen = true,
            safes = listOf(LoungeSave.Safe(-34, 10, 153, listOf(8, 20, 33), 2, true)), lootTaken = listOf("level_4/ch1_l4_loot_3"),
        )
        assertEquals(save, LoungeSave.fromText(save.toText()))
    }

    @Test
    fun `nothing usable gives nothing`() {
        assertNull(LoungeSave.fromText(null))
        assertNull(LoungeSave.fromText(""))
        assertNull(LoungeSave.fromText("{not json"))
    }

    @Test
    fun `a puzzle restored goes on from there`() {
        val puzzle = OrderPuzzle(listOf(4, 5, 6))
        puzzle.restore(2, 3)
        assertEquals(listOf(4, 5), puzzle.placed)
        assertEquals(6, puzzle.hint)
        assertEquals(OrderPuzzle.Result.SOLVED, puzzle.attempt(6))
        assertTrue(puzzle.solved)
    }
}
