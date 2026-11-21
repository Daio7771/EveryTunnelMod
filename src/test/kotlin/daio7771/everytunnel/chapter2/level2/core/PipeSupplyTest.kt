package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.core.level.Position
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipeSupplyTest {

    private val maw = Ch2L2Maw.DEFAULT.copy(respawnSeconds = 2.0, piecesAtOnce = 1)
    private val points = (0 until 8).map { Position(it * 3.0, 25.0, 0.0) }
    private val supply = PipeSupply(Random(1))

    @Test
    fun `nothing falls while a piece lies or is held`() {
        for (now in 0L..200L) assertNull(supply.tick(now, 1, points, null, null, maw))
    }

    @Test
    fun `a piece falls respawn seconds after the last is gone, and not before`() {
        assertNull(supply.tick(100, 0, points, null, null, maw))
        assertNull(supply.tick(139, 0, points, null, null, maw))
        assertTrue(supply.tick(140, 0, points, null, null, maw) != null)
        assertNull(supply.tick(141, 1, points, null, null, maw))
    }

    @Test
    fun `it never drops on the point nearest the creature or the player, nor twice the same in a row`() {
        val creature = Position(0.0, 0.0, 2.0)
        val player = Position(21.0, 0.0, 2.0)
        var previous = -1
        var now = 0L
        repeat(200) {
            supply.tick(now, 0, points, creature, player, maw)
            now += 41
            val pick = supply.tick(now, 0, points, creature, player, maw) ?: error("no pick")
            assertNotEquals(PipeSupply.nearest(points, creature), pick)
            assertNotEquals(PipeSupply.nearest(points, player), pick)
            assertNotEquals(previous, pick)
            previous = pick
        }
    }

    @Test
    fun `several pieces at once are topped up one at a time`() {
        val two = maw.copy(piecesAtOnce = 2)
        assertNull(supply.tick(0, 1, points, null, null, two))
        assertTrue(supply.tick(41, 1, points, null, null, two) != null)
        assertEquals(null, supply.tick(42, 2, points, null, null, two))
    }
}
