package daio7771.everytunnel.core.cutscene

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The prologue's timeline, its room's plan and the camera's moving aim. */
class PrologueTest {

    private val eps = 1e-6
    private val plan = RoomPlan.load()
    private val scene = PrologueScene.SCENE.cutscene

    /** The scientist's frame in the room's own coordinates: where he stands, facing the way the plan says. */
    private val frame = SceneFrame(plan.scientist.x, plan.scientist.y, plan.scientist.z, plan.scientist.yaw.toDouble())

    private fun at(tick: Double) = scene.pose(tick, frame, CameraPose(0.0, 0.0, 0.0, 0.0, 0.0), Triple(0.0, 0.0, 0.0))

    @Test
    fun `a shot whose aim moves with the camera keeps its heading`() {
        val shot = Shot(100, Local(-3.0, -8.0, 1.5), Local(-3.0, -2.0, 1.5), Look.At(Local(3.0, -8.0, 1.2)), Look.At(Local(3.0, -2.0, 1.2)))
        val cutscene = Cutscene(listOf(shot), 0, 0)
        val eyes = CameraPose(0.0, 0.0, 0.0, 0.0, 0.0)
        val start = cutscene.pose(0.0, frame, eyes, Triple(0.0, 0.0, 0.0))
        val middle = cutscene.pose(50.0, frame, eyes, Triple(0.0, 0.0, 0.0))
        val end = cutscene.pose(100.0 - 1e-9, frame, eyes, Triple(0.0, 0.0, 0.0))
        assertEquals(start.yaw, middle.yaw, 1e-6)
        assertEquals(start.yaw, end.yaw, 1e-6)
        assertEquals(start.pitch, end.pitch, 1e-6)
    }

    @Test
    fun `a shot with one aim is unchanged`() {
        val shot = Shot(100, Local(-3.0, -8.0, 1.5), Local(-3.0, -2.0, 1.5), Look.At(Local(0.0, 0.0, 1.3)))
        val cutscene = Cutscene(listOf(shot), 0, 0)
        val eyes = CameraPose(0.0, 0.0, 0.0, 0.0, 0.0)
        val a = cutscene.pose(10.0, frame, eyes, Triple(0.0, 0.0, 0.0))
        val b = cutscene.pose(90.0, frame, eyes, Triple(0.0, 0.0, 0.0))
        assertTrue(abs(a.yaw - b.yaw) > 1.0, "the camera turns to keep looking at the same place")
    }

    @Test
    fun `the camera tracks along +x behind him and comes to a stop`() {
        val start = at(0.0)
        val middle = at(PrologueScene.TRAVEL / 2.0)
        val stop = at(PrologueScene.TRAVEL.toDouble())
        assertTrue(start.x < middle.x && middle.x < stop.x, "left to right, towards +x")
        assertEquals(start.z, stop.z, eps)
        assertTrue(stop.z > plan.scientist.z, "south of him, behind his back (he faces north)")
        assertTrue(stop.x < plan.scientist.x, "short of him, to his left")
        assertTrue(abs(at(PrologueScene.TRAVEL - 1.0).x - stop.x) < 0.02, "slow as it arrives")
        assertTrue(abs(at(PrologueScene.TRAVEL + 1.0).x - stop.x) < 0.02, "and slow as it holds")
    }

    /** [degrees] turned into -180..180, so 180 and -180 (both north) compare equal. */
    private fun wrap(degrees: Double): Double = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    @Test
    fun `while tracking the camera looks north at the bench, then turns onto his back`() {
        val north = 180.0
        for (tick in listOf(0.0, PrologueScene.TRAVEL / 2.0, PrologueScene.TRAVEL.toDouble())) {
            assertEquals(0.0, wrap(at(tick).yaw - north), 1e-3, "tick $tick")
        }
        val end = at(PrologueScene.TRAVEL + PrologueScene.HOLD - 1e-6)
        val toHim = CameraPose.looking(end.x, end.y, end.z, plan.scientist.x, plan.scientist.y + 1.3, plan.scientist.z + 0.0)
        assertTrue(abs(wrap(end.yaw - toHim.yaw)) < 12.0, "ends looking at his back: ${end.yaw} vs ${toHim.yaw}")
        assertTrue(wrap(end.yaw - north) > 5.0, "turned to its right (towards the east, where he is)")
    }

    @Test
    fun `the camera never leaves the room`() {
        for (tick in 0..scene.totalTicks) {
            val pose = at(tick.toDouble())
            assertTrue(plan.insideInterior(pose.x, pose.y, pose.z), "tick $tick: ${pose.x}, ${pose.y}, ${pose.z}")
        }
    }

    @Test
    fun `the scene opens out of black and ends with the bands out`() {
        val timeline = PrologueScene.SCENE
        assertEquals(1.0, timeline.blackAt(0.0), eps)
        assertEquals(1.0, timeline.blackAt(timeline.blackTicks - 1.0), eps)
        assertTrue(timeline.blackAt(timeline.blackTicks + timeline.fadeTicks / 2.0) in 0.01..0.99, "half way, half black")
        assertEquals(0.0, timeline.blackAt((timeline.blackTicks + timeline.fadeTicks).toDouble()), eps)
        assertEquals(PrologueScene.TRAVEL + PrologueScene.HOLD + Cutscene.BANDS_TICKS, scene.totalTicks)
        assertEquals(0.0, scene.bands(scene.totalTicks.toDouble()), eps)
        assertEquals(1.0, scene.bands(Cutscene.BANDS_TICKS.toDouble()), eps)
    }

    @Test
    fun `a longer hold is only longer`() {
        val longer = PrologueScene.build(PrologueScene.HOLD + 100).cutscene
        assertEquals(scene.totalTicks + 100, longer.totalTicks)
    }

    @Test
    fun `the room's plan has what the shots need`() {
        // A bench along the north wall, and nothing where the scientist stands.
        val benches = plan.puts.filter { it.block.startsWith("every_tunnel:lab_bench") }
        assertTrue(benches.size >= 30, "a long bench")
        assertTrue(benches.all { it.at.z in 0..1 && it.at.y == 0 })
        val cell = Cell(plan.scientist.x.toInt(), 0, plan.scientist.z.toInt())
        assertTrue(plan.puts.none { it.at == cell || it.at == Cell(cell.x, 1, cell.z) }, "he stands on the floor, not in a block")
        assertTrue(plan.insideInterior(plan.scientist.x, plan.scientist.y + 0.5, plan.scientist.z))
        assertTrue(plan.insideInterior(plan.viewer.x, plan.viewer.y + 0.5, plan.viewer.z))
        assertEquals(180f, plan.scientist.yaw, 0f)
    }

    @Test
    fun `every block of the plan is inside its shell and set once`() {
        val (from, to) = plan.shell
        fun inside(c: Cell) = c.x in from.x..to.x && c.y in from.y..to.y && c.z in from.z..to.z
        assertTrue(plan.puts.all { inside(it.at) })
        assertTrue(plan.fills.all { inside(it.from) && inside(it.to) })
        assertEquals(plan.puts.size, plan.puts.map { it.at }.toSet().size, "no cell twice")
    }

    @Test
    fun `the lights are lamps in the ceiling`() {
        val roof = plan.interior.second.y + 1
        assertTrue(plan.lights.isNotEmpty())
        assertTrue(plan.lights.all { it.y == roof })
        assertTrue(plan.lights.all { light -> plan.puts.any { it.at == light && it.block == "every_tunnel:lab_ceiling_light" } })
    }

    @Test
    fun `every block of the plan parses`() {
        for (block in plan.fills.map { it.block } + plan.puts.map { it.block }) {
            val spec = BlockSpec.parse(block)
            assertTrue(spec.id.contains(':'), "$block has a namespace")
        }
    }

    @Test
    fun `block specs read their states`() {
        assertEquals(BlockSpec("minecraft:air", emptyMap()), BlockSpec.parse("minecraft:air"))
        val bench = BlockSpec.parse("every_tunnel:lab_bench[facing=south,left=false,right=true]")
        assertEquals("every_tunnel:lab_bench", bench.id)
        assertEquals(mapOf("facing" to "south", "left" to "false", "right" to "true"), bench.states)
        assertFailsWith<IllegalArgumentException> { BlockSpec.parse("every_tunnel:lab_bench[facing=south") }
        assertFailsWith<IllegalArgumentException> { BlockSpec.parse("every_tunnel:lab_bench[facing]") }
    }
}
