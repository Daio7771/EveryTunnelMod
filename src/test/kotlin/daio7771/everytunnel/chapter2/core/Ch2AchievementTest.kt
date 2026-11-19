package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.core.achievement.AchievementRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Ch2AchievementTest {

    @AfterTest
    fun clean() = AchievementRegistry.clearAdded()

    @Test
    fun `the ten are the second tab, with unique ids and one sprite each`() {
        AchievementRegistry.register(Ch2Achievement.entries)
        assertEquals(listOf(1, 2), AchievementRegistry.chapters())
        assertEquals(Ch2Achievement.entries, AchievementRegistry.inChapter(2))
        assertEquals(10, Ch2Achievement.entries.size)
        assertEquals(AchievementRegistry.all().size, AchievementRegistry.all().map { it.id }.toSet().size)
        assertEquals(10, Ch2Achievement.entries.map { it.icon }.toSet().size)
        assertSame(Ch2Achievement.EXPLORER, AchievementRegistry.byId("explorer"))
        assertTrue(Ch2Achievement.entries.none { it.puzzle })
    }

    @Test
    fun `five of them are hidden`() {
        assertEquals(
            setOf(Ch2Achievement.STATIC, Ch2Achievement.FAMILIAR_FACE, Ch2Achievement.WASTE_OF_TIME, Ch2Achievement.EXPLORER, Ch2Achievement.BAD_AIM),
            Ch2Achievement.entries.filter { it.hidden }.toSet(),
        )
    }

    @Test
    fun `a gaze is earned after its ticks, once, and looking away or moving starts it again`() {
        val gaze = StillGaze(5)
        repeat(4) { assertFalse(gaze.tick(true, 1.0, 2.0, 3.0)) }
        assertTrue(gaze.tick(true, 1.0, 2.0, 3.0))
        assertFalse(gaze.tick(true, 1.0, 2.0, 3.0))

        repeat(4) { gaze.tick(true, 1.0, 2.0, 3.0) }
        assertFalse(gaze.tick(false, 1.0, 2.0, 3.0))
        repeat(4) { assertFalse(gaze.tick(true, 1.0, 2.0, 3.0)) }
        assertTrue(gaze.tick(true, 1.0, 2.0, 3.0))

        repeat(4) { gaze.tick(true, 1.0, 2.0, 3.0) }
        assertFalse(gaze.tick(true, 2.0, 2.0, 3.0))
        repeat(3) { assertFalse(gaze.tick(true, 2.0, 2.0, 3.0)) }
        assertTrue(gaze.tick(true, 2.0, 2.0, 3.0))
    }
}
