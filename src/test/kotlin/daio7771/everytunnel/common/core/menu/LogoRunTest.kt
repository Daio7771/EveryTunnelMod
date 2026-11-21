package daio7771.everytunnel.common.core.menu

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogoRunTest {

    private val whole = LogoRun.OUT_TICKS + LogoRun.HOLD_TICKS + LogoRun.BACK_TICKS

    /** Runs [ticks] ticks; the tick at which each run began (the words started going out). */
    private fun starts(run: LogoRun, ticks: Int): List<Int> {
        val out = mutableListOf<Int>()
        var was = false
        for (t in 1..ticks) {
            run.tick()
            if (run.running && !was) out += t
            was = run.running
        }
        return out
    }

    @Test
    fun `the words are lit and RUN is not glowing until the first run`() {
        val run = LogoRun(Random(1))
        repeat(25 * 20 - 1) {
            run.tick()
            assertFalse(run.running)
            assertEquals(1f, run.wordsLevel)
            assertEquals(0f, run.glow)
        }
    }

    @Test
    fun `the first run comes 25 to 60 seconds in`() {
        for (seed in 1..20) {
            val first = starts(LogoRun(Random(seed)), 70 * 20).first()
            assertTrue(first in 25 * 20..60 * 20 + 1, "seed $seed: first run at tick $first")
        }
    }

    @Test
    fun `a run puts the words out, leaves RUN alone and brings them back`() {
        val run = LogoRun(Random(3))
        while (!run.running) run.tick()
        val levels = mutableListOf(run.wordsLevel)
        val glows = mutableListOf(run.glow)
        repeat(whole - 1) {
            run.tick()
            levels += run.wordsLevel
            glows += run.glow
        }
        val hold = levels.subList(LogoRun.OUT_TICKS, LogoRun.OUT_TICKS + LogoRun.HOLD_TICKS)
        assertTrue(hold.all { it == 0f }, "alone: the words are out the whole hold")
        assertEquals(0f, levels[LogoRun.OUT_TICKS - 1], "the last tick of going out is out")
        assertEquals(1f, levels.last(), "the last tick of coming back is lit")
        assertTrue(levels.take(LogoRun.OUT_TICKS).any { it == 1f } && levels.take(LogoRun.OUT_TICKS).any { it < 0.5f }, "going out stutters")
        assertTrue(glows.max() > 0.9f, "RUN swells to nearly full")
        assertTrue(glows.take(LogoRun.OUT_TICKS).all { it == 0f }, "no glow while the words still flicker")
        run.tick()
        assertFalse(run.running)
        assertEquals(1f, run.wordsLevel)
        assertEquals(0f, run.glow)
    }

    @Test
    fun `runs are 45 to 120 seconds apart, counted from the end of the last`() {
        val begins = starts(LogoRun(Random(7)), 20 * 60 * 20)
        assertTrue(begins.size in 8..25, "runs in twenty minutes: ${begins.size}")
        for ((a, b) in begins.zipWithNext()) {
            val gap = b - (a + whole)
            assertTrue(gap in 45 * 20 - 1..120 * 20 + 1, "gap between runs: $gap ticks")
        }
    }

    @Test
    fun `levels stay between 0 and 1`() {
        val run = LogoRun(Random(11), LogoRun.Timing(firstMinTicks = 5, firstMaxTicks = 10, minGapTicks = 5, maxGapTicks = 10))
        repeat(2000) {
            run.tick()
            assertTrue(run.wordsLevel in 0f..1f)
            assertTrue(run.glow in 0f..1f)
        }
    }
}
