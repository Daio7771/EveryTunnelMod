package daio7771.everytunnel.systems

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollapseScheduleTest {

    @Test
    fun `the rubble fills steadily between its start and end, and all of it`() {
        val total = 36
        assertEquals(0, CollapseSchedule.rubbleBy(CollapseSchedule.RUBBLE_START - 1, total))
        assertTrue(CollapseSchedule.rubbleBy(CollapseSchedule.RUBBLE_START, total) > 0)
        val counts = (CollapseSchedule.RUBBLE_START..CollapseSchedule.RUBBLE_END).map { CollapseSchedule.rubbleBy(it, total) }
        assertEquals(counts.sorted(), counts, "never fewer than before")
        assertEquals(total, CollapseSchedule.rubbleBy(CollapseSchedule.RUBBLE_END, total))
        assertEquals(total, CollapseSchedule.rubbleBy(CollapseSchedule.LENGTH, total))
    }

    @Test
    fun `the shake builds, peaks while the rubble falls and dies away`() {
        assertEquals(0f, CollapseSchedule.shake(0f))
        assertTrue(CollapseSchedule.shake(10f) in 0.01f..0.35f)
        assertEquals(1f, CollapseSchedule.shake(40f))
        assertTrue(CollapseSchedule.shake(100f) < CollapseSchedule.shake(70f))
        assertEquals(0f, CollapseSchedule.shake(CollapseSchedule.LENGTH.toFloat()))
    }
}
