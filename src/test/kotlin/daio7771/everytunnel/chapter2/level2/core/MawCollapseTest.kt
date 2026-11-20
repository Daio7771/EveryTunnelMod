package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class MawCollapseTest {

    private val maw = Ch2L2Maw.DEFAULT
    private val plan = MawCollapse.plan(maw, 61)

    @Test
    fun `the same seed gives the same plan`() {
        assertEquals(plan, MawCollapse.plan(maw, 61))
        assertTrue(plan != MawCollapse.plan(maw, 62))
    }

    @Test
    fun `the first slab is 3 x 3 over the centre and is the first slab`() {
        val first = plan.first { it.kind == MawCollapse.Kind.SLAB }
        assertEquals(maw.firstSlabDelayTicks, first.delayTicks)
        assertEquals(9, first.cells.size)
        assertTrue((0 to 0) in first.cells)
        assertTrue(first.cells.all { (dx, dz) -> Math.abs(dx) <= 1 && Math.abs(dz) <= 1 })
    }

    @Test
    fun `the plan has the bits and slabs it was asked for, the bits first`() {
        val bits = plan.count { it.kind == MawCollapse.Kind.BIT }
        val slabs = plan.count { it.kind == MawCollapse.Kind.SLAB }
        assertEquals(maw.bitCount, bits)
        assertTrue(slabs in 2..maw.slabCount, "$slabs slabs")
        assertEquals(List(bits) { MawCollapse.Kind.BIT } + List(slabs) { MawCollapse.Kind.SLAB }, plan.map { it.kind })
        assertTrue(plan.filter { it.kind == MawCollapse.Kind.BIT }.all { it.cells.size == 1 && it.delayTicks < maw.firstSlabDelayTicks })
        assertTrue(plan.filter { it.kind == MawCollapse.Kind.SLAB }.drop(1).all { it.cells.size == 4 || it.cells.size == 9 })
    }

    @Test
    fun `delays only go up, from the first bit to the last slab`() {
        val delays = plan.map { it.delayTicks }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.last() <= maw.firstSlabDelayTicks + maw.slabSpreadTicks)
    }

    @Test
    fun `every cell is within the radius`() {
        val r = maw.collapseRadius
        for (piece in plan) for ((dx, dz) in piece.cells) assertTrue(dx * dx + dz * dz <= r * r, "$dx, $dz")
    }

    @Test
    fun `the mound is no higher than the maximum and tallest near the middle`() {
        for (seed in 1..40) {
            val p = MawCollapse.plan(maw, seed)
            val heights = MawCollapse.heights(p, maw.maxHeight)
            assertTrue(heights.values.all { it in 1..maw.maxHeight }, "seed $seed")
            assertTrue(heights.maxOf { it.value } >= 2, "seed $seed")
            val (middle, edge) = heights.entries.partition { (cell, _) -> cell.first * cell.first + cell.second * cell.second <= 5 }
            assertTrue(middle.map { it.value }.average() > edge.map { it.value }.average(), "seed $seed")
        }
    }

    @Test
    fun `the heights are the pieces covering each column, capped`() {
        val pieces = listOf(
            MawCollapse.Piece(0, listOf(0 to 0, 1 to 0), MawCollapse.Kind.BIT),
            MawCollapse.Piece(1, listOf(0 to 0), MawCollapse.Kind.SLAB),
            MawCollapse.Piece(2, listOf(0 to 0), MawCollapse.Kind.SLAB),
        )
        assertEquals(mapOf((0 to 0) to 3, (1 to 0) to 1), MawCollapse.heights(pieces, 5))
        assertEquals(mapOf((0 to 0) to 2, (1 to 0) to 1), MawCollapse.heights(pieces, 2))
    }

    @Test
    fun `a block's fall takes as many ticks as vanilla's physics`() {
        // From rest, vanilla: v = (v + 0.04) * 0.98 a tick; 5 blocks are covered in 17 ticks (hand-run: 0.04, 0.119, 0.234, ... 4.96 after 16, 5.2 after 17).
        assertEquals(17, MawCollapse.fallTicks(5.0, 0.0))
        assertEquals(7, MawCollapse.fallTicks(1.0, 0.0))
        // Thrown down at 0.35 a tick it is much faster.
        assertEquals(5, MawCollapse.fallTicks(2.0, 0.35))
        assertEquals(10, MawCollapse.fallTicks(5.0, 0.35))
        assertEquals(0, MawCollapse.fallTicks(0.0, 0.35))
    }

    @Test
    fun `a piece on a pile lands sooner than one on the floor`() {
        val a = MawCollapse.Piece(0, listOf(0 to 0), MawCollapse.Kind.BIT)
        val b = MawCollapse.Piece(0, listOf(0 to 0), MawCollapse.Kind.BIT)
        val landings = MawCollapse.landings(listOf(a, b), 0.35)
        assertEquals(MawCollapse.fallTicks(5.0, 0.35), landings[0])
        assertEquals(MawCollapse.fallTicks(4.0, 0.35), landings[1])
    }

    @Test
    fun `the death keys are read, and defaults stand without them`() {
        val read = Ch2L2Level.parse(
            JsonParser.parseString(
                """{"maw": {"death": {"bit_count": 3, "first_slab_delay_ticks": 8,
                    "slab_count": 3, "slab_spread_ticks": 10, "slab_start_speed": 0.5, "crush_height": 1.5, "max_height": 2}}}""",
            ).asJsonObject,
        )
        assertEquals(emptyList(), read.problems.filter { it.contains("death") }, "${read.problems}")
        val m = read.level.maw
        assertEquals(3, m.bitCount)
        assertEquals(8, m.firstSlabDelayTicks)
        assertEquals(3, m.slabCount)
        assertEquals(10, m.slabSpreadTicks)
        assertEquals(0.5, m.slabStartSpeed)
        assertEquals(1.5, m.crushHeight)
        assertEquals(2, m.maxHeight)
        val none = Ch2L2Level.parse(JsonParser.parseString("""{"maw": {"death": {}}}""").asJsonObject).level.maw
        assertEquals(Ch2L2Maw.DEFAULT.bitCount, none.bitCount)
        assertEquals(Ch2L2Maw.DEFAULT.slabStartSpeed, none.slabStartSpeed)
        assertEquals(Ch2L2Maw.DEFAULT.maxHeight, none.maxHeight)
    }

    @Test
    fun `the jar's death keys are the defaults'`() {
        val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())
        assertEquals(emptyList(), data.problems)
        val m = data.level.maw
        assertEquals(Ch2L2Maw.DEFAULT.copy(burstSeconds = m.burstSeconds).bitCount, m.bitCount)
        assertEquals(Ch2L2Maw.DEFAULT.slabCount, m.slabCount)
    }

    @Test
    fun `wherever the last pillar is and Stone Maw stands against it, the mound leaves a way from the rest of the arena (beyond where the player is thrown from) to the far door`() {
        val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())
        val rules = data.level.maw
        val arena = data.anchors.zone(rules.arenaZone)!!.box
        val door = data.anchors.point(rules.farAnchor)!!.position
        // The cell inside the arena the far door opens on: the door's x is the wall's line, the arena starts one in.
        val goal = (arena.minX to Math.floor(door.z).toInt())
        val footprints = rules.pillars.map { id ->
            val c = data.anchors.point(id)!!.position
            val cx = Math.floor(c.x).toInt()
            val cz = Math.floor(c.z).toInt()
            setOf(cx - 1 to cz - 1, cx to cz - 1, cx - 1 to cz, cx to cz)
        }
        val standing = footprints.flatten().toSet()
        val cells = (arena.minX..arena.maxX).flatMap { x -> (arena.minZ..arena.maxZ).map { z -> x to z } }
        for ((last, footprint) in footprints.withIndex()) {
            // Every cell round the pillar Stone Maw could be rammed against.
            // Every cell Stone Maw's middle can be in with its charge ended against this pillar: within the hit radius of its faces.
            val minX = footprint.minOf { it.first }
            val minZ = footprint.minOf { it.second }
            val around = cells.filter { (x, z) ->
                val dx = maxOf(minX - (x + 0.5), 0.0, (x + 0.5) - (minX + 2.0))
                val dz = maxOf(minZ - (z + 0.5), 0.0, (z + 0.5) - (minZ + 2.0))
                Math.hypot(dx, dz) <= rules.pillarHitRadius && free(x, z, standing)
            }
            assertTrue(around.isNotEmpty())
            for ((cx, cz) in around) {
                val blocked = MawCollapse.mound(plan, cx, cz) + standing
                assertFalse(goal in blocked, "pillar ${last + 1}, Maw at ($cx, $cz): the far door's cell is under the mound")
                val reach = HashSet<Pair<Int, Int>>().also { it += goal }
                val queue = ArrayDeque(listOf(goal))
                while (queue.isNotEmpty()) {
                    val (x, z) = queue.removeFirst()
                    for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val n = (x + dx) to (z + dz)
                        if (n in cells.toSet() && n !in blocked && reach.add(n)) queue += n
                    }
                }
                // Whoever stands within a little more than the radius of Stone Maw when the ceiling comes down is thrown clear of it, so the cells
                // that matter are the ones beyond that.
                val cut = cells.filter { it !in blocked && it !in reach && Math.hypot((it.first - cx).toDouble(), (it.second - cz).toDouble()) > rules.collapseRadius + 1.5 }
                if (cut.isNotEmpty()) fail("pillar ${last + 1}, Maw at ($cx, $cz): ${cut.size} cells cannot reach the far door, e.g. ${cut.first()}")
            }
        }
    }

    private fun free(x: Int, z: Int, standing: Set<Pair<Int, Int>>) = (x to z) !in standing
}
