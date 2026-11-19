package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The flood's stages as `flood.json` holds them (what each adds, how it rises over time) and what the level does with them at a start. */
class Ch2L2FloodTest {

    private val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())
    private val flood = data.flood

    @Test
    fun `the jar's flood reads without a problem, three stages in order, each with its trigger zone`() {
        assertEquals(emptyList(), data.problems)
        assertEquals(listOf(1, 2, 3), flood.stages.map { it.id })
        for (stage in flood.stages) assertNotNull(data.anchors.zone(stage.trigger), "${stage.id}: ${stage.trigger}")
        assertEquals(3.0, flood.stage(1)!!.delaySeconds)
        assertEquals(60, flood.stage(1)!!.delayTicks)
        assertEquals(160, flood.stage(1)!!.totalTicks)
        assertEquals("sealed", flood.stage(1)!!.gates["vestibule_gate"])
        assertEquals(flood.stage(2), flood.stageOf("zone_rising_walkways"))
    }

    @Test
    fun `a stage keeps only what it adds to the one before's water, never a cell twice`() {
        val seen = HashSet<FloodCell>()
        var total = 0
        for (stage in flood.stages) {
            val cells = stage.layers.flatMap { it.cells }
            assertTrue(cells.isNotEmpty(), "stage ${stage.id}")
            assertTrue(cells.none { it in seen }, "stage ${stage.id} places a cell an earlier one placed")
            seen += cells
            total += cells.size
            assertTrue(cells.all { it.y < stage.level }, "stage ${stage.id}: water at or over y ${stage.level}")
        }
        assertEquals(seen.size, total)
        assertEquals(seen.size, flood.allCells.size)
        // The cumulative sizes are the generator's own counts (flood.json's `cells`, which the checks' leak proof is made over).
        assertEquals(2746, flood.stage(1)!!.cellCount)
        assertEquals(6177, flood.stage(1)!!.cellCount + flood.stage(2)!!.cellCount)
        assertEquals(15342, total)
    }

    @Test
    fun `layers rise bottom up, the first at once, the last by the end, each placed once`() {
        for (stage in flood.stages) {
            assertEquals(stage.layers.map { it.y }, stage.layers.map { it.y }.sorted(), "stage ${stage.id}")
            assertEquals(1, stage.layersDue(0), "stage ${stage.id}")
            assertEquals(0, stage.layersDue(-1))
            assertEquals(stage.layers.size, stage.layersDue(stage.totalTicks - 1), "stage ${stage.id}")
            assertEquals(stage.layers.size, stage.layersDue(stage.totalTicks * 5))
            var last = 0
            for (tick in 0 until stage.totalTicks) {
                val due = stage.layersDue(tick)
                assertTrue(due >= last && due - last <= 1 || stage.layers.size > stage.totalTicks, "stage ${stage.id} tick $tick: $last -> $due")
                last = due
            }
            assertEquals(stage.cellCount, stage.cellsOf(stage.layersDue(stage.totalTicks)).size)
            assertEquals(stage.layers.first().cells.size, stage.cellsOf(1).size)
        }
    }

    @Test
    fun `at a start the water is drained until the station, which keeps all of it, and the flood goes back to the guard's end`() {
        assertEquals(0, flood.resume(Ch2L2State.ARRIVED).water)
        assertEquals(Ch2L2State.CATACOMBS, flood.resume(Ch2L2State.CATACOMBS).state)
        assertEquals(0, flood.resume(Ch2L2State.GUARD_DEAD).water)
        for (inside in listOf(Ch2L2State.FLOOD_1, Ch2L2State.FLOOD_2, Ch2L2State.FLOOD_3)) {
            val resume = flood.resume(inside)
            assertEquals(0, resume.water, "$inside")
            assertEquals(Ch2L2State.GUARD_DEAD, resume.state, "$inside")
        }
        for (after in listOf(Ch2L2State.STATION, Ch2L2State.MAW, Ch2L2State.REUNION, Ch2L2State.DONE)) {
            val resume = flood.resume(after)
            assertEquals(3, resume.water, "$after")
            assertEquals(after, resume.state)
        }
    }

    @Test
    fun `the exit door opens from the station on, the ending only from the reunion, and the jar's tunables and checkpoint are in place`() {
        assertTrue(!Ch2L2Flood.exitDoorAllowed(Ch2L2State.FLOOD_3))
        assertTrue(Ch2L2Flood.exitDoorAllowed(Ch2L2State.STATION))
        assertTrue(!Ch2L2Flood.endingAllowed(Ch2L2State.STATION))
        assertTrue(!Ch2L2Flood.endingAllowed(Ch2L2State.MAW_DEAD))
        assertTrue(Ch2L2Flood.endingAllowed(Ch2L2State.REUNION))
        assertTrue(Ch2L2Flood.endingAllowed(Ch2L2State.DONE))
        val level = data.level
        val rules = level.flood
        assertEquals("pipe_burst", rules.pipeAnchor)
        assertEquals(listOf(0.35, 0.6, 0.9), rules.rumbleVolume)
        assertEquals(0.9, rules.rumbleAt(3))
        assertEquals(0.9, rules.rumbleAt(7))
        assertEquals("zone_exit_door", level.maw.exitZone)
        assertNotNull(level.checkpoint(level.ending.checkpoint))
        assertNotNull(data.anchors.point(rules.pipeAnchor))
        assertNotNull(data.anchors.point("exit_hatch"))
    }

    @Test
    fun `a flood file that is not an object or has no stages is reported`() {
        assertTrue(Ch2L2Flood.parse("[1]").problems.isNotEmpty())
        assertTrue(Ch2L2Flood.parse("""{"nothing": 1}""").problems.isNotEmpty())
        val one = Ch2L2Flood.parse("""{"stages": [{"id": 1, "trigger": "z", "delay_seconds": 0, "seconds": 2, "level": 0, "water": [[0,-1,0,1,-1,1]]}]}""")
        assertEquals(emptyList(), one.problems)
        assertEquals(4, one.flood.stage(1)!!.cellCount)
    }
}
