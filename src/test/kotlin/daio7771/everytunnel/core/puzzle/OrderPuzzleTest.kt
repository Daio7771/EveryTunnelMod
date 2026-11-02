package daio7771.everytunnel.core.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderPuzzleTest {

    @Test
    fun `the right order solves it`() {
        val puzzle = OrderPuzzle(listOf(0, 1, 2, 3))
        assertEquals(OrderPuzzle.Result.RIGHT, puzzle.attempt(0))
        assertEquals(OrderPuzzle.Result.RIGHT, puzzle.attempt(1))
        assertEquals(OrderPuzzle.Result.RIGHT, puzzle.attempt(2))
        assertFalse(puzzle.solved)
        assertEquals(OrderPuzzle.Result.SOLVED, puzzle.attempt(3))
        assertTrue(puzzle.solved)
    }

    @Test
    fun `a restart keeps the failures`() {
        val puzzle = OrderPuzzle(listOf(0, 1))
        puzzle.attempt(1)
        puzzle.attempt(0)
        puzzle.restart()
        assertEquals(emptyList(), puzzle.placed)
        assertEquals(1, puzzle.failures)
    }

    @Test
    fun `a wrong one starts it over`() {
        val puzzle = OrderPuzzle(listOf(0, 1))
        puzzle.attempt(0)
        assertEquals(listOf(0), puzzle.placed)
        assertEquals(OrderPuzzle.Result.WRONG, puzzle.attempt(0))
        assertEquals(emptyList(), puzzle.placed)
        assertEquals(1, puzzle.failures)
    }

    @Test
    fun `the hint comes after three failures and follows the progress`() {
        val puzzle = OrderPuzzle(listOf(2, 0))
        repeat(2) { puzzle.attempt(1) }
        assertNull(puzzle.hint)
        puzzle.attempt(1)
        assertEquals(2, puzzle.hint)
        puzzle.attempt(2)
        assertEquals(0, puzzle.hint)
        puzzle.attempt(0)
        assertNull(puzzle.hint, "nothing left to hint at")
    }

    @Test
    fun `reset forgets progress and failures`() {
        val puzzle = OrderPuzzle(listOf(0, 1))
        puzzle.attempt(1)
        puzzle.attempt(0)
        puzzle.reset()
        assertEquals(0, puzzle.done)
        assertEquals(0, puzzle.failures)
    }
}
