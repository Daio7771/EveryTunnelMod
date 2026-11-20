package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MawEscapeTest {

    private val maw = Ch2L2Maw.DEFAULT
    private val arena = MawEscape.Arena(minX = -32, minZ = 47, maxX = -13, maxZ = 66, doorX = -32, doorZ = 57)

    @Test
    fun `the plan is the same for the same seed and has the pieces the rate asks for, in time order, inside the arena`() {
        val plan = MawEscape.plan(maw, arena, 5)
        assertEquals(plan, MawEscape.plan(maw, arena, 5))
        assertEquals(Math.round(maw.escape.seconds * maw.escape.debrisPerSecond).toInt(), plan.size)
        assertEquals(plan.sortedBy { it.tick }, plan)
        assertTrue(plan.all { it.x in arena.minX..arena.maxX && it.z in arena.minZ..arena.maxZ })
    }

    @Test
    fun `every piece is announced and lands before the time is up`() {
        val plan = MawEscape.plan(maw, arena, 9)
        assertTrue(plan.all { it.tick + maw.debris.warnTicks <= MawEscape.endTick(maw) }, "$plan")
    }

    @Test
    fun `fewer pieces fall near the far door than anywhere else`() {
        val near = ArrayList<Int>()
        val far = ArrayList<Int>()
        val r = maw.escape.doorClearRadius
        for (seed in 1..60) {
            for (d in MawEscape.plan(maw, arena, seed)) {
                (if (Math.hypot((d.x - arena.doorX).toDouble(), (d.z - arena.doorZ).toDouble()) <= r) near else far) += 1
            }
        }
        val nearCells = (arena.minX..arena.maxX).sumOf { x -> (arena.minZ..arena.maxZ).count { z -> Math.hypot((x - arena.doorX).toDouble(), (z - arena.doorZ).toDouble()) <= r } }
        val total = (arena.maxX - arena.minX + 1) * (arena.maxZ - arena.minZ + 1)
        val nearDensity = near.size.toDouble() / nearCells
        val farDensity = far.size.toDouble() / (total - nearCells)
        assertTrue(nearDensity < farDensity / 2, "near $nearDensity, far $farDensity")
    }

    @Test
    fun `whoever is still in the arena when the time is up is lost, and no one before it or outside it`() {
        val end = MawEscape.endTick(maw)
        assertEquals(160, end)
        assertFalse(MawEscape.isLost(maw, end - 1, inArena = true))
        assertTrue(MawEscape.isLost(maw, end, inArena = true))
        assertFalse(MawEscape.isLost(maw, end + 50, inArena = false))
    }
}
