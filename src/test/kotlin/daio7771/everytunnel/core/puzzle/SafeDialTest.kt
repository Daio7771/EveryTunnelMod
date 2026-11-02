package daio7771.everytunnel.core.puzzle

import daio7771.everytunnel.core.puzzle.SafeDial.Event
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeDialTest {

    private fun SafeDial.turnTo(number: Int, turn: Int): Event {
        var event = turn(turn)
        while (position != number) event = turn(turn)
        return event
    }

    @Test
    fun `a tick on each number, a click on the right one`() {
        val dial = SafeDial(listOf(3, 30, 10))
        assertEquals(Event.TICK, dial.turn(1))
        assertEquals(Event.TICK, dial.turn(1))
        assertEquals(Event.CLICK, dial.turn(1))
    }

    @Test
    fun `changing direction on the click fixes it, and the last one opens`() {
        val dial = SafeDial(listOf(3, 30, 10))
        assertEquals(Event.CLICK, dial.turnTo(3, 1))
        assertEquals(Event.CLICK, dial.turnTo(30, -1))
        assertEquals(1, dial.step)
        assertEquals(Event.SOLVED, dial.turnTo(10, 1))
        assertTrue(dial.solved)
    }

    @Test
    fun `changing direction on a wrong number starts over, and is a failure once something is fixed`() {
        val dial = SafeDial(listOf(3, 30, 10))
        dial.turnTo(5, 1)
        dial.turn(-1)
        assertEquals(0, dial.failures)
        dial.turnTo(3, -1)
        dial.turn(1)
        assertEquals(1, dial.step)
        assertEquals(Event.TICK, dial.turn(1))
        assertEquals(Event.RESET, dial.turn(-1))
        assertEquals(0, dial.step)
        assertEquals(1, dial.failures)
    }

    @Test
    fun `going past the click and round again still works`() {
        val dial = SafeDial(listOf(3, 30, 10))
        dial.turnTo(3, 1)
        dial.turn(1)
        assertEquals(Event.CLICK, dial.turnTo(3, 1))
    }

    @Test
    fun `it trembles on the right number only after 3 failures`() {
        val dial = SafeDial(listOf(1, 30, 10), failures = 2)
        dial.turn(1)
        assertFalse(dial.trembling)
        val worn = SafeDial(listOf(1, 30, 10), failures = 3)
        assertFalse(worn.trembling)
        worn.turn(1)
        assertTrue(worn.trembling)
    }

    @Test
    fun `drawn numbers are apart from each other and from the start`() {
        val random = Random(2)
        repeat(200) {
            val code = SafeDial.draw(random)
            assertEquals(3, code.size)
            var last = 0
            for (number in code) {
                val gap = Math.floorMod(number - last, 40)
                assertTrue(minOf(gap, 40 - gap) >= 5)
                last = number
            }
        }
    }
}
