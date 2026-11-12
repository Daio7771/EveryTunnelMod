package daio7771.everytunnel.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickStatsTest {

    private fun ms(n: Int) = n * 1_000_000L

    @Test
    fun `nothing recorded says so`() {
        assertEquals("no ticks recorded", TickStats().summary())
    }

    @Test
    fun `averages, maximums, slow ticks and the share of the hooks`() {
        val stats = TickStats()
        // Two ticks of 10 ms and 70 ms; one hook took 2 + 6 ms, another 1 + 1.
        stats.recordHook("Slow", ms(2)); stats.recordHook("Fast", ms(1)); stats.recordTick(ms(10))
        stats.recordHook("Slow", ms(6)); stats.recordHook("Fast", ms(1)); stats.recordTick(ms(70))
        assertEquals(
            "2 ticks | tick avg 40.00 ms, max 70.00 ms, 1 over 50 ms | mod hooks avg 5.00 ms (12%) | top: Slow 4.00/6.00, Fast 1.00/1.00",
            stats.summary(),
        )
    }

    @Test
    fun `the top list is cut and ordered by total cost`() {
        val stats = TickStats()
        for ((name, cost) in listOf("A" to 1, "B" to 5, "C" to 3)) stats.recordHook(name, ms(cost))
        stats.recordTick(ms(10))
        val top = stats.summary(top = 2).substringAfter("top: ")
        assertEquals("B 5.00/5.00, C 3.00/3.00", top)
    }

    @Test
    fun `reset forgets everything`() {
        val stats = TickStats()
        stats.recordHook("A", ms(1)); stats.recordTick(ms(1))
        stats.reset()
        assertEquals(0, stats.ticks)
        assertTrue(stats.summary().startsWith("no ticks"))
    }
}
