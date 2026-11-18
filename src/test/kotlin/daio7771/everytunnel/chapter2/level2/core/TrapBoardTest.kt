package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level2.core.TrapBoard.Outcome
import daio7771.everytunnel.chapter2.level2.core.TrapBoard.Phase
import kotlin.test.Test
import kotlin.test.assertEquals

class TrapBoardTest {

    private fun board() = TrapBoard(listOf("a", "b"), rearmTicks = 60, failChance = 0.25)

    private fun armed(board: TrapBoard, id: String = "a", at: Long = 0) {
        board.pull(id, at)
        assertEquals(listOf(id), board.tick(at + 60))
    }

    @Test
    fun `a pulled trap charges, then arms`() {
        val b = board()
        assertEquals(Phase.SPENT, b.phase("a"))
        assertEquals(true, b.pull("a", 0))
        assertEquals(Phase.CHARGING, b.phase("a"))
        assertEquals(emptyList(), b.tick(59))
        assertEquals(listOf("a"), b.tick(60))
        assertEquals(Phase.ARMED, b.phase("a"))
        assertEquals(false, b.pull("a", 61))
    }

    @Test
    fun `the roll is made once on stepping in and standing on it never rolls again`() {
        val b = board()
        armed(b)
        assertEquals(Outcome.STUN, b.step("a", true, 0.9, stunActive = false))
        assertEquals(Phase.SPENT, b.phase("a"))
        assertEquals(Outcome.NONE, b.step("a", true, 0.0, stunActive = false))
        // Rearmed while the Guard still stands on the plate: it must step off and on again.
        armed(b, at = 100)
        assertEquals(Outcome.NONE, b.step("a", true, 0.0, stunActive = false))
        assertEquals(Phase.ARMED, b.phase("a"))
        b.step("a", false, 0.0, stunActive = false)
        assertEquals(Outcome.FAIL, b.step("a", true, 0.1, stunActive = false))
    }

    @Test
    fun `a failed roll consumes the activation too, and failures chain on other traps`() {
        val b = board()
        armed(b, "a")
        armed(b, "b", at = 10)
        assertEquals(Outcome.FAIL, b.step("a", true, 0.2, stunActive = false))
        assertEquals(Phase.SPENT, b.phase("a"))
        assertEquals(Outcome.FAIL, b.step("b", true, 0.0, stunActive = false))
        assertEquals(Phase.SPENT, b.phase("b"))
    }

    @Test
    fun `a trap entered while not armed does nothing and is not remembered`() {
        val b = board()
        b.pull("a", 0)
        assertEquals(Outcome.NONE, b.step("a", true, 0.9, stunActive = false))
        b.tick(60)
        assertEquals(Outcome.NONE, b.step("a", true, 0.9, stunActive = false))
        b.step("a", false, 0.0, stunActive = false)
        assertEquals(Outcome.STUN, b.step("a", true, 0.9, stunActive = false))
    }

    @Test
    fun `an entry during a stun does nothing and leaves the trap armed`() {
        val b = board()
        armed(b)
        assertEquals(Outcome.NONE, b.step("a", true, 0.9, stunActive = true))
        assertEquals(Phase.ARMED, b.phase("a"))
        b.step("a", false, 0.0, stunActive = false)
        assertEquals(Outcome.STUN, b.step("a", true, 0.9, stunActive = false))
    }

    @Test
    fun `the roll at the fail chance is a success, below it a failure`() {
        val b = board()
        armed(b)
        assertEquals(Outcome.STUN, b.step("a", true, 0.25, stunActive = false))
    }

    @Test
    fun `reset spends every trap`() {
        val b = board()
        armed(b)
        b.reset()
        assertEquals(Phase.SPENT, b.phase("a"))
        assertEquals(Outcome.NONE, b.step("a", true, 0.9, stunActive = false))
    }
}
