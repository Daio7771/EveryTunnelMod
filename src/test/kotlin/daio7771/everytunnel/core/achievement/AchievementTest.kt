package daio7771.everytunnel.core.achievement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementTest {

    @Test
    fun `there are eight, each found by its unique id`() {
        assertEquals(8, Achievement.entries.size)
        for (achievement in Achievement.entries) assertEquals(achievement, Achievement.byId(achievement.id))
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `an unknown id gives nothing`() {
        assertNull(Achievement.byId("crossword"))
    }

    @Test
    fun `saved ids come back in list order, unknown ones dropped`() {
        val loaded = Achievement.fromIds(listOf("valves", "from_a_newer_version", "first_day", "valves"))
        assertEquals(listOf(Achievement.FIRST_DAY, Achievement.VALVES), loaded.toList())
    }

    @Test
    fun `the four puzzles are marked`() {
        assertEquals(
            setOf(Achievement.KEYCARD, Achievement.VALVES, Achievement.BOXES, Achievement.BOOKS),
            Achievement.entries.filter { it.puzzle }.toSet(),
        )
    }
}
