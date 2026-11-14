package daio7771.everytunnel.core.cutscene

import com.google.gson.JsonParser
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
        val end = at(scene.totalTicks - Cutscene.BANDS_TICKS - 1e-6)
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
        assertEquals(timeline.lines.last().end + PrologueScene.TAIL + Cutscene.BANDS_TICKS, scene.totalTicks)
        assertEquals(0.0, scene.bands(scene.totalTicks.toDouble()), eps)
        assertEquals(1.0, scene.bands(Cutscene.BANDS_TICKS.toDouble()), eps)
    }

    private val voice = PrologueLines.load()

    @Test
    fun `his log entry begins after the camera stops, each line after the one before`() {
        val lines = PrologueScene.SCENE.lines
        assertEquals(PrologueScene.PLAYED, lines.map { it.line.id })
        assertEquals(PrologueScene.TRAVEL + PrologueScene.LEAD, lines[0].start)
        for ((a, b) in lines.zipWithNext()) assertEquals(a.end + PrologueScene.LINE_GAP, b.start)
        for (l in lines) assertEquals(DirectorScene.ticks(l.line.ms), l.end - l.start)
        assertTrue(lines.first().start > PrologueScene.TRAVEL, "the camera is at rest before he speaks")
    }

    @Test
    fun `subtitles come with the voice and never ahead of it or into the next line`() {
        val timeline = PrologueScene.SCENE
        val lines = timeline.lines
        for (sub in timeline.subtitles) {
            val line = lines.last { it.start <= sub.start }
            val next = lines.getOrNull(lines.indexOf(line) + 1)
            assertTrue(sub.start >= line.start, "${sub.key} is not ahead of its line")
            assertTrue(sub.end > sub.start, "${sub.key} is on screen")
            assertTrue(next == null || sub.end <= next.start, "${sub.key} ends before the next line")
        }
        assertEquals(lines.sumOf { it.line.cues.size }, timeline.subtitles.size)
        assertEquals(null, timeline.subtitleAt(lines.first().start - 1.0), "nothing before he speaks")
        assertEquals(lines.first().line.cues.first().key, timeline.subtitleAt(lines.first().start.toDouble())?.key)
    }

    @Test
    fun `every cue of his four lines has its text in English and in Spanish`() {
        fun keys(file: String): Set<String> {
            val stream = PrologueTest::class.java.classLoader.getResourceAsStream("assets/every_tunnel/lang/$file.json")!!
            return stream.use { JsonParser.parseString(it.reader().readText()).asJsonObject.keySet() }
        }
        val en = keys("en_us")
        val es = keys("es_es")
        assertEquals(listOf("prologue_01", "prologue_02", "prologue_03", "prologue_04"), voice.lines.map { it.id })
        for (cue in voice.lines.flatMap { it.cues }) {
            assertTrue(cue.key in en, "${cue.key} in en_us")
            assertTrue(cue.key in es, "${cue.key} in es_es")
        }
    }

    @Test
    fun `the radiation counter sounds from where the plan puts its block`() {
        val place = frame.at(PrologueScene.COUNTER.forward, PrologueScene.COUNTER.right, PrologueScene.COUNTER.up)
        assertEquals(plan.counter.x, place.first, eps)
        assertEquals(plan.counter.y, place.second, eps)
        assertEquals(plan.counter.z, place.third, eps)
        assertTrue(plan.puts.any { it.at == Cell(plan.counter.x.toInt(), 1, plan.counter.z.toInt()) && it.block.startsWith("every_tunnel:radiation_counter") })
        assertTrue(plan.puts.any { it.block.startsWith("every_tunnel:voice_recorder") })
        assertTrue(plan.puts.count { it.block.startsWith("every_tunnel:tube_rack") } >= 3)
    }

    @Test
    fun `the counter clicks faster and faster`() {
        val total = scene.totalTicks
        val rates = (0..total step 20).map { PrologueScene.geigerRate(it.toDouble(), total) }
        assertTrue(rates.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(rates.last() > 2 * rates.first())
        val clicks = GeigerClicks(7)
        fun mean(rate: Double) = (0 until 20_000).sumOf { clicks.counts(it, rate) } / 20_000.0
        assertEquals(1.0 / 20.0 * 1.0, mean(1.0), 0.01)
        assertEquals(10.0 / 20.0, mean(10.0), 0.03)
    }

    @Test
    fun `the same seed and tick always count the same`() {
        val a = GeigerClicks(3)
        val b = GeigerClicks(3)
        for (tick in 0 until 500) assertEquals(a.counts(tick, 6.0), b.counts(tick, 6.0))
        assertEquals(0, a.counts(10, 0.0))
        assertTrue((0 until 1000).all { a.counts(it, 400.0) in 0..12 }, "never more than a tick can hold")
    }

    @Test
    fun `the hum is in for the black and out at the end, the drone rises`() {
        val total = scene.totalTicks
        assertEquals(0.0, PrologueScene.humLevel(0.0, total), eps)
        assertEquals(1.0, PrologueScene.humLevel(total / 2.0, total), eps)
        assertEquals(0.0, PrologueScene.humLevel(total.toDouble(), total), eps)
        assertEquals(0.0, PrologueScene.droneLevel(0.0, total), eps)
        val early = PrologueScene.droneLevel(total * 0.2, total)
        val late = PrologueScene.droneLevel(total * 0.8, total)
        assertTrue(early in 0.05..0.4, "a murmur at first: $early")
        assertTrue(late > 2 * early, "rising: $early then $late")
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
