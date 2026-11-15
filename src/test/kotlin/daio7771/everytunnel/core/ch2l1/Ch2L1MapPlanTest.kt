package daio7771.everytunnel.core.ch2l1

import daio7771.everytunnel.core.cutscene.BlockSpec
import daio7771.everytunnel.core.cutscene.Cell
import java.io.File
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The map plan in the jar, as `tools/map/ch2l1/generate.py` wrote it, and how it fits the anchors beside it. */
class Ch2L1MapPlanTest {

    private val plan = Ch2L1MapPlan.load()
    private val assets = File("src/main/resources/assets/every_tunnel")
    private val anchors = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.anchors

    /** The block of every cell the fills set (a cell set twice would be a mistake of the generator). */
    private val cells: Map<Cell, String> by lazy {
        val out = HashMap<Cell, String>()
        for (f in plan.fills) for (x in f.from.x..f.to.x) for (y in f.from.y..f.to.y) for (z in f.from.z..f.to.z) {
            val previous = out.put(Cell(x, y, z), f.block)
            assertEquals(null, previous, "cell ${Cell(x, y, z)} is set twice")
        }
        out
    }

    private fun solid(x: Int, y: Int, z: Int) = Cell(x, y, z) in cells

    @Test
    fun `the plan stands at the origin the generator says and has a body`() {
        assertEquals(Cell(1024, 64, 1024), plan.origin)
        assertTrue(plan.fills.size > 100, "${plan.fills.size} fills")
        assertEquals(plan.filledCells(), cells.size.toLong())
        assertTrue(plan.clearedCells() > plan.filledCells())
    }

    @Test
    fun `every fill lies inside the box that is cleared first`() {
        val (a, b) = plan.clear
        for (f in plan.fills) {
            assertTrue(f.from.x >= a.x && f.to.x <= b.x && f.from.y >= a.y && f.to.y <= b.y && f.from.z >= a.z && f.to.z <= b.z, "fill $f is outside ${plan.clear}")
        }
    }

    @Test
    fun `every block is a vanilla one the map uses or a block of the mod with its blockstate`() {
        val vanilla = setOf("white_concrete", "light_gray_concrete", "gray_concrete")
        for (block in plan.fills.map { it.block }.toSet()) {
            val spec = BlockSpec.parse(block)
            val (namespace, name) = spec.id.split(":")
            when (namespace) {
                "minecraft" -> assertTrue(name in vanilla, "$block: not one of the vanilla blocks the map uses")
                "every_tunnel" -> assertTrue(File(assets, "blockstates/$name.json").isFile, "$block: no blockstate (run tools/blocks/generate.py)")
                else -> error("$block: unknown namespace")
            }
        }
    }

    @Test
    fun `the lamps are all ceiling lamps and the lamp fills are all in the list`() {
        assertTrue(plan.lights.isNotEmpty())
        for (c in plan.lights) assertEquals("every_tunnel:lab_ceiling_light", cells[c], "no lamp at $c")
        assertEquals(plan.lights.size, cells.values.count { it == "every_tunnel:lab_ceiling_light" })
    }

    @Test
    fun `every door is two halves, one over the other`() {
        val doors = cells.filterValues { "_door[" in it }
        assertTrue(doors.isNotEmpty())
        for ((c, block) in doors) {
            val half = if ("half=lower" in block) "lower" else "upper"
            val other = if (half == "lower") Cell(c.x, c.y + 1, c.z) else Cell(c.x, c.y - 1, c.z)
            assertEquals(block.replace("half=$half", "half=${if (half == "lower") "upper" else "lower"}"), cells[other], "the other half of the door at $c")
        }
    }

    @Test
    fun `the doors are the mod's own, the factory door for hands and the emergency door for the locked ones`() {
        val kinds = cells.values.map { BlockSpec.parse(it).id }.filter { it.endsWith("_door") }.toSet()
        assertEquals(setOf("every_tunnel:factory_door", "every_tunnel:emergency_door"), kinds)
    }

    /**
     * A door's hinge is read looking the way it faces: facing south, "left" is its cell's east edge; facing east, "left" is
     * the north edge. The leaves of a pair, taken from the lowest x (south) or z (east), hinge on the outer edges, so they
     * swing clear of the passage: right then left going south, left then right going east.
     */
    @Test
    fun `the leaves of a pair of doors are hinged on the outer edges`() {
        val leaves = cells.filter { (_, block) -> "_door[" in block && "half=lower" in block }
            .map { (c, block) -> c to BlockSpec.parse(block).states }
        assertTrue(leaves.isNotEmpty())
        for (facing in listOf("south", "east")) {
            val along: (Cell) -> Int = { if (facing == "south") it.x else it.z }
            val across: (Cell) -> Int = { if (facing == "south") it.z else it.x }
            val mine = leaves.filter { it.second["facing"] == facing }.sortedWith(compareBy({ across(it.first) }, { along(it.first) }))
            assertTrue(mine.isNotEmpty(), "no door faces $facing")
            var runStart = 0
            for ((i, leaf) in mine.withIndex()) {
                val previous = mine.getOrNull(i - 1)
                if (previous == null || across(previous.first) != across(leaf.first) || along(previous.first) != along(leaf.first) - 1) runStart = i
                val first = if (facing == "south") "right" else "left"
                val second = if (facing == "south") "left" else "right"
                assertEquals(if ((i - runStart) % 2 == 0) first else second, leaf.second["hinge"], "the hinge of the leaf at ${leaf.first} facing $facing")
            }
        }
    }

    @Test
    fun `the time clock is in the plan where its anchor says, facing into the entrance`() {
        val clocks = cells.filterValues { it.startsWith("every_tunnel:time_clock") }
        assertEquals(1, clocks.size)
        val (c, block) = clocks.entries.single()
        assertTrue("facing=north" in block)
        val anchor = anchors.point("time_clock")!!.position
        assertEquals(plan.worldX(c.x) + 0.5, anchor.x)
        assertEquals(plan.worldY(c.y).toDouble(), anchor.y)
        assertEquals(plan.worldZ(c.z) + 0.5, anchor.z)
    }

    /** The block of the plan at the world cell of an anchor (the cell its position is in), or null. */
    private fun blockAt(x: Double, y: Double, z: Double): String? =
        cells[Cell(floor(x).toInt() - plan.origin.x, floor(y).toInt() - plan.origin.y, floor(z).toInt() - plan.origin.z)]

    private fun blockOf(anchorId: String): String? = anchors.point(anchorId)!!.position.let { blockAt(it.x, it.y, it.z) }

    @Test
    fun `the blocks of the tasks and the speakers stand in the plan where their anchors say`() {
        val tasks = Ch2L1TasksParser.parse(Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.document("tasks")).tasks
        for (tube in tasks.tubes) assertTrue(blockOf(tube.anchor)!!.startsWith("every_tunnel:sample_tube"), tube.anchor)
        assertTrue(blockOf(tasks.terminalAnchor)!!.startsWith("every_tunnel:noname_terminal"))
        assertTrue(blockOf(tasks.analyzerAnchor)!!.startsWith("every_tunnel:analyzer"))
        assertTrue(blockOf(tasks.broomAnchor)!!.startsWith("every_tunnel:leaning_broom"))
        val dirt = anchors.path(tasks.dirtPath)!!
        assertEquals(10, dirt.points.size)
        for ((i, p) in dirt.points.withIndex()) {
            assertTrue(blockAt(p.x, p.y, p.z)!!.startsWith("every_tunnel:dirt_spot"), "spot $i at $p")
            assertStandsOnTheFloor("dirt $i", p.x, p.y, p.z, ignoreOwnBlock = true)
        }
        for (zone in listOf("entrance", "lab", "main", "west").map { "speaker_$it" }) {
            assertTrue(blockOf("${zone}_src")!!.startsWith("every_tunnel:ceiling_speaker"), zone)
        }
    }

    @Test
    fun `the player stands in front of the terminal and the analyzer, in reach`() {
        for ((stand, block) in listOf("task1_terminal" to "task1_terminal_block", "task2_analyzer" to "task2_analyzer_block")) {
            val a = anchors.point(stand)!!.position
            val b = anchors.point(block)!!.position
            val reach = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y + 1.62 - b.y) * (a.y + 1.62 - b.y) + (a.z - b.z) * (a.z - b.z))
            assertTrue(reach < 3.5, "$stand is $reach blocks from $block")
        }
    }

    /** The anchors a player or a person stands on: feet on a floor of the plan, room over the head, nothing solid in the way. */
    private val standing = listOf(
        "spawn_clockin", "taquillas_exit", "task1_terminal", "task2_analyzer", "predecessor_locker", "chamber_02", "chamber_log",
        "authorization_desk", "desk_anchor", "rowan_spawn", "patrol_a_pause",
    ) + (1..6).map { "taquillas_npc_%02d".format(it) }

    private fun assertStandsOnTheFloor(what: String, x: Double, y: Double, z: Double, ignoreOwnBlock: Boolean = false) {
        val px = floor(x).toInt() - plan.origin.x
        val py = floor(y).toInt() - plan.origin.y
        val pz = floor(z).toInt() - plan.origin.z
        assertTrue(solid(px, py - 1, pz), "$what at $x, $y, $z has no floor under it")
        assertTrue((ignoreOwnBlock || !solid(px, py, pz)) && !solid(px, py + 1, pz), "$what at $x, $y, $z is inside a block")
    }

    @Test
    fun `everyone's anchor stands on the floor with room over their head`() {
        for (id in standing) {
            val p = anchors.point(id)!!.position
            assertStandsOnTheFloor(id, p.x, p.y, p.z)
        }
    }

    @Test
    fun `the patrols' points stand on the floor too`() {
        for (path in anchors.paths.filter { it.id.startsWith("patrol_") }) for ((i, p) in path.points.withIndex()) assertStandsOnTheFloor("${path.id}[$i]", p.x, p.y, p.z)
    }

    @Test
    fun `every zone has air in it and lies inside the plan`() {
        val (a, b) = plan.clear
        for (zone in anchors.zones) {
            val box = zone.box
            val x0 = box.minX - plan.origin.x
            val z0 = box.minZ - plan.origin.z
            assertTrue(x0 >= a.x && box.maxX - plan.origin.x <= b.x && z0 >= a.z && box.maxZ - plan.origin.z <= b.z, "zone ${zone.id} is outside the plan")
            val air = (box.minX..box.maxX).any { x ->
                (box.minZ..box.maxZ).any { z -> !solid(x - plan.origin.x, 0, z - plan.origin.z) && solid(x - plan.origin.x, -1, z - plan.origin.z) }
            }
            assertTrue(air, "zone ${zone.id} has no floor with air over it")
        }
    }

    @Test
    fun `a plan with another content has another fingerprint`() {
        val text = File("src/main/resources/${Ch2L1MapPlan.RESOURCE}").readText()
        assertEquals(plan.fingerprint, Ch2L1MapPlan.parse(text).fingerprint)
        assertNotEquals(plan.fingerprint, Ch2L1MapPlan.parse(text.replace("white_concrete", "gray_concrete")).fingerprint)
    }
}
