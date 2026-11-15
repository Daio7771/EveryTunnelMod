package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ch2PlayerProgressTest {

    @Test
    fun `a player begins Chapter 2's Level 1 at the clock and a new game takes them back there`() {
        val progress = Ch2PlayerProgress()
        assertEquals(Ch2L1State.ACT1_CLOCKIN, progress.l1State)
        progress.l1State = Ch2L1State.ACT3_EXPLORE
        assertTrue("ACT3_EXPLORE" in progress.describe())
        progress.clear()
        assertEquals(Ch2L1State.ACT1_CLOCKIN, progress.l1State)
    }

    @Test
    fun `playing Chapter 2's Level 1 is a flag that a new game clears`() {
        val progress = Ch2PlayerProgress()
        assertFalse(progress.l1Active)
        progress.l1Active = true
        assertTrue("active" in progress.describe())
        progress.clear()
        assertFalse(progress.l1Active)
    }

    @Test
    fun `counters keep only what is not zero and clear by prefix`() {
        val progress = Ch2PlayerProgress()
        progress.setL1Counter("heard_a1", 1)
        progress.setL1Counter("heard_a2", 1)
        progress.setL1Counter("tubes", 5)
        progress.setL1Counter("broom", 0)
        assertEquals(mapOf("heard_a1" to 1, "heard_a2" to 1, "tubes" to 5), progress.l1CounterMap)
        progress.clearL1Counters("heard_")
        assertEquals(mapOf("tubes" to 5), progress.l1CounterMap)
        progress.clear()
        assertEquals(0, progress.l1Counter("tubes"))
    }
}
