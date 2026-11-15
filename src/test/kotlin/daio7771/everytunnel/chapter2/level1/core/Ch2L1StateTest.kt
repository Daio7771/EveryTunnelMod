package daio7771.everytunnel.chapter2.level1.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1StateTest {

    @Test
    fun `the states come in the order of the level`() {
        assertEquals(
            listOf(
                "ACT1_CLOCKIN", "ACT1_TASK1", "ACT1_TASK2", "ACT1_TASK3", "ACT2_FOLLOW",
                "ACT2_MEETING", "ACT3_EXPLORE", "ACT3_CUTSCENE", "ACT3_OVERTIME", "DONE",
            ),
            Ch2L1State.entries.map { it.name },
        )
        assertTrue(Ch2L1State.ACT2_FOLLOW > Ch2L1State.ACT1_TASK3)
    }

    @Test
    fun `next walks the whole level and stops at the end`() {
        var state: Ch2L1State? = Ch2L1State.START
        val seen = mutableListOf<Ch2L1State>()
        while (state != null) {
            seen += state
            state = state.next()
        }
        assertEquals(Ch2L1State.entries.toList(), seen)
        assertNull(Ch2L1State.DONE.next())
    }

    @Test
    fun `a new player starts at the clock`() {
        assertEquals(Ch2L1State.ACT1_CLOCKIN, Ch2L1State.START)
    }

    @Test
    fun `parse ignores case and takes a dash for an underscore`() {
        assertEquals(Ch2L1State.ACT3_EXPLORE, Ch2L1State.parse("ACT3_EXPLORE"))
        assertEquals(Ch2L1State.ACT3_EXPLORE, Ch2L1State.parse("act3_explore"))
        assertEquals(Ch2L1State.ACT3_EXPLORE, Ch2L1State.parse(" act3-explore "))
        assertEquals(Ch2L1State.DONE, Ch2L1State.parse("done"))
    }

    @Test
    fun `parse gives null for what is not a state`() {
        assertNull(Ch2L1State.parse(""))
        assertNull(Ch2L1State.parse("act4_dance"))
        assertNull(Ch2L1State.parse("ACT3"))
    }
}
