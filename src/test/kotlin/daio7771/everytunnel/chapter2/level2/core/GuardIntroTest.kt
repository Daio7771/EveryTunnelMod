package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuardIntroTest {

    @Test
    fun `the moments are in the order of the choreography and the whole fits the intro`() {
        val order = listOf(GuardIntro.PUNCH_START, GuardIntro.PUNCH_AT, GuardIntro.WALK_FROM, GuardIntro.WALK_TO, GuardIntro.TURN_TO, GuardIntro.POINT_TO)
        assertEquals(order.sorted(), order)
        assertEquals(GuardIntro.PUNCH_START + 20, GuardIntro.PUNCH_AT)
        // The chest's last blow lands before the default intro (10 s) ends, and the chest animation (3 s) ends with it.
        val end = Math.round(Ch2L2Guard.DEFAULT.introSeconds * 20.0)
        assertTrue(GuardIntro.chestBlows().last() < end)
        assertTrue(GuardIntro.POINT_TO + 60 <= end)
        assertEquals(5, GuardIntro.chestBlows().size)
    }

    @Test
    fun `the camera shakes in a growing tremor, jolts at the punch, then shakes a little with each blow of the chest`() {
        assertEquals(0.0, GuardIntro.shake(0.0))
        assertTrue(GuardIntro.shake(25.0) in 0.1..0.3)
        assertEquals(1.5, GuardIntro.shake(GuardIntro.PUNCH_AT.toDouble()), 0.001)
        assertEquals(0.0, GuardIntro.shake(GuardIntro.PUNCH_AT + 40.0))
        val blow = GuardIntro.chestBlows().first().toDouble()
        assertEquals(0.0, GuardIntro.shake(blow - 1.0))
        assertEquals(0.5, GuardIntro.shake(blow), 0.001)
        assertTrue(GuardIntro.shake(blow + 8.0) < 0.05)
    }

    @Test
    fun `the flash is only at the punch`() {
        assertEquals(0.0, GuardIntro.flash(GuardIntro.PUNCH_AT - 1.0))
        assertEquals(0.5, GuardIntro.flash(GuardIntro.PUNCH_AT.toDouble()), 0.001)
        assertEquals(0.0, GuardIntro.flash(GuardIntro.PUNCH_AT + 30.0))
    }

    @Test
    fun `the cinematic is in first person - every shot stays at the player's eyes, and it lasts as long as the intro`() {
        val total = Math.round(Ch2L2Guard.DEFAULT.introSeconds * 20.0).toInt()
        val eyes = doubleArrayOf(8.5, 3.5, 1.62)
        val scene = GuardIntro.timeline(total, eyes).cutscene
        assertEquals(total, scene.totalTicks)
        assertEquals(GuardIntro.FLY_IN, scene.flyInTicks)
        assertEquals(4, scene.shots.size)
        for (shot in scene.shots) {
            assertEquals(eyes[0], shot.from.forward)
            assertEquals(eyes[1], shot.from.right)
            assertEquals(eyes[2], shot.from.up)
            assertEquals(shot.from, shot.to)
        }
    }

    @Test
    fun `a shorter intro still has all its shots`() {
        val scene = GuardIntro.timeline(100, doubleArrayOf(8.0, 3.0, 1.62)).cutscene
        assertEquals(4, scene.shots.size)
        assertTrue(scene.shots.all { it.ticks > 0 })
    }
}
