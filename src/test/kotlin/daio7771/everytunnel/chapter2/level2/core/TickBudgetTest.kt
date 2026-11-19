package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TickBudgetTest {

    @Test
    fun `a tick is closed by the next one and its handlers are summed`() {
        val budget = TickBudget()
        budget.add(1, "pump", 2_000_000)
        budget.add(1, "guard", 1_000_000)
        assertEquals(0, budget.ticks)
        budget.add(2, "pump", 4_000_000)
        assertEquals(1, budget.ticks)
        assertEquals(3.0, budget.meanMillis(), 1e-9)
        assertEquals(3.0, budget.worstMillis(), 1e-9)
    }

    @Test
    fun `it keeps only the last window of ticks`() {
        val budget = TickBudget(window = 3)
        for (t in 1L..10L) budget.add(t, "a", t * 1_000_000)
        budget.add(11, "a", 0)
        assertEquals(3, budget.ticks)
        assertEquals((8 + 9 + 10) / 3.0, budget.meanMillis(), 1e-9)
        assertEquals(10.0, budget.worstMillis(), 1e-9)
    }

    @Test
    fun `the means by handler come costliest first`() {
        val budget = TickBudget()
        for (t in 1L..4L) {
            budget.add(t, "small", 1_000_000)
            budget.add(t, "big", 3_000_000)
        }
        budget.add(5, "small", 0)
        assertEquals(listOf("big" to 3.0, "small" to 1.0), budget.meanMillisByName())
    }

    @Test
    fun `nothing held means zero`() {
        val budget = TickBudget()
        assertEquals(0.0, budget.meanMillis())
        assertEquals(emptyList(), budget.meanMillisByName())
    }
}
