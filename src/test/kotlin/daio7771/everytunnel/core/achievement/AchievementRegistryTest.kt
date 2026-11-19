package daio7771.everytunnel.core.achievement

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class AchievementRegistryTest {

    private class Fake(override val id: String, override val chapter: Int, override val puzzle: Boolean = false) : AchievementDef

    @AfterTest
    fun clean() = AchievementRegistry.clearAdded()

    @Test
    fun `alone it is the first tab with the eleven of the first chapter`() {
        assertEquals(listOf(1), AchievementRegistry.chapters())
        assertEquals(Achievement.entries, AchievementRegistry.inChapter(1))
        assertEquals(Achievement.entries, AchievementRegistry.all())
    }

    @Test
    fun `a chapter's achievements add a tab after the first, in order, found by id`() {
        val a = Fake("later_a", 2)
        val b = Fake("later_b", 2, puzzle = true)
        AchievementRegistry.register(listOf(a, b))
        assertEquals(listOf(1, 2), AchievementRegistry.chapters())
        assertEquals(listOf(a, b), AchievementRegistry.inChapter(2))
        assertSame(b, AchievementRegistry.byId("later_b"))
        assertSame(Achievement.VALVES, AchievementRegistry.byId("valves"))
        assertEquals(Achievement.entries + listOf(a, b), AchievementRegistry.all())
        assertEquals(AchievementRegistry.all().size, AchievementRegistry.all().map { it.id }.toSet().size)
    }

    @Test
    fun `registering the same definition again changes nothing, another with its id fails`() {
        val a = Fake("later_a", 2)
        AchievementRegistry.register(listOf(a))
        AchievementRegistry.register(listOf(a))
        assertEquals(1, AchievementRegistry.inChapter(2).size)
        assertFailsWith<IllegalArgumentException> { AchievementRegistry.register(listOf(Fake("later_a", 2))) }
        assertFailsWith<IllegalArgumentException> { AchievementRegistry.register(listOf(Fake("first_day", 2))) }
    }

    @Test
    fun `the first chapter's tab is the base game's alone`() {
        assertFailsWith<IllegalArgumentException> { AchievementRegistry.register(listOf(Fake("sneaky", 1))) }
        assertNull(AchievementRegistry.byId("sneaky"))
    }

    @Test
    fun `saved ids of every chapter come back in list order and unknown ones are left out`() {
        val a = Fake("later_a", 2)
        AchievementRegistry.register(listOf(a))
        val loaded = AchievementRegistry.fromIds(listOf("later_a", "from_a_newer_version", "valves", "first_day"))
        assertEquals(listOf<AchievementDef>(Achievement.FIRST_DAY, Achievement.VALVES, a), loaded.toList())
    }
}
