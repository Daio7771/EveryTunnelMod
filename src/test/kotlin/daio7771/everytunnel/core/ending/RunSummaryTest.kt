package daio7771.everytunnel.core.ending

import kotlin.test.Test
import kotlin.test.assertEquals

class RunSummaryTest {

    private fun ticks(hours: Int, minutes: Int, seconds: Int) = ((hours * 60L + minutes) * 60 + seconds) * 20

    @Test
    fun `under an hour it is minutes and seconds`() {
        assertEquals("0:00", RunSummary(0, 0).time())
        assertEquals("8:45", RunSummary(ticks(0, 8, 45), 3).time())
        assertEquals("59:59", RunSummary(ticks(0, 59, 59), 0).time())
    }

    @Test
    fun `from an hour on it is hours, minutes and seconds`() {
        assertEquals("1:00:00", RunSummary(ticks(1, 0, 0), 0).time())
        assertEquals("2:05:09", RunSummary(ticks(2, 5, 9), 0).time())
        assertEquals("12:00:01", RunSummary(ticks(12, 0, 1), 0).time())
    }

    @Test
    fun `a partial second is dropped and a negative count is zero`() {
        assertEquals("0:01", RunSummary(39, 0).time())
        assertEquals("0:00", RunSummary(-5, 0).time())
    }
}
