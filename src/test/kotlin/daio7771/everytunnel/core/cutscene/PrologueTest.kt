package daio7771.everytunnel.core.cutscene

import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The prologue's timeline, its cues, its room's plan, its sound curves and the camera's moving aim. */
class PrologueTest {

    private val eps = 1e-6
    private val plan = RoomPlan.load()
    private val scene = PrologueScene.SCENE.cutscene
    private val cues = PrologueScene.PLAN.cues
    private val voice = PrologueLines.load()
    private val lines = PrologueScene.SCENE.lines

    /** The scientist's frame in the room's own coordinates: where he stands, facing the way the plan says. */
    private val frame = SceneFrame(plan.scientist.x, plan.scientist.y, plan.scientist.z, plan.scientist.yaw.toDouble())

    private fun at(tick: Double) = scene.pose(tick, frame, CameraPose(0.0, 0.0, 0.0, 0.0, 0.0), Triple(0.0, 0.0, 0.0))

    /** [degrees] turned into -180..180, so 180 and -180 (both north) compare equal. */
    private fun wrap(degrees: Double): Double = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    // ---- the camera

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

    @Test
    fun `while tracking the camera looks north at the bench, then turns onto his back`() {
        val north = 180.0
        for (tick in listOf(0.0, PrologueScene.TRAVEL / 2.0, PrologueScene.TRAVEL.toDouble())) {
            assertEquals(0.0, wrap(at(tick).yaw - north), 1e-3, "tick $tick")
        }
        val settled = at(lines[2].start.toDouble())
        val toHim = CameraPose.looking(settled.x, settled.y, settled.z, plan.scientist.x, plan.scientist.y + 1.3, plan.scientist.z + 0.0)
        assertTrue(abs(wrap(settled.yaw - toHim.yaw)) < 12.0, "settled on his back before he shakes: ${settled.yaw} vs ${toHim.yaw}")
        assertTrue(wrap(settled.yaw - north) > 5.0, "turned to its right (towards the east, where he is)")
    }

    @Test
    fun `the camera never leaves the room`() {
        // Up to the end of the last shot: after it only the bands slide out, under black, and the camera is handed back to the player.
        for (tick in 0 until scene.shotsEnd) {
            val pose = at(tick.toDouble())
            assertTrue(plan.insideInterior(pose.x, pose.y, pose.z), "tick $tick: ${pose.x}, ${pose.y}, ${pose.z}")
        }
    }

    @Test
    fun `the camera creeps in while he changes, never jumping`() {
        val first = lines[2].start
        var previous = at(first.toDouble())
        for (tick in first + 1 until scene.shotsEnd) {
            val pose = at(tick.toDouble())
            assertTrue(abs(pose.x - previous.x) < 0.05 && abs(pose.z - previous.z) < 0.05, "tick $tick: a jump")
            previous = pose
        }
    }

    // ---- the voice

    @Test
    fun `his four lines come one after the other, the first after the camera stops`() {
        assertEquals(listOf("prologue_01", "prologue_02", "prologue_03", "prologue_04"), lines.map { it.line.id })
        assertEquals(PrologueScene.TRAVEL + PrologueScene.LEAD, lines[0].start)
        assertEquals(lines[0].end + PrologueScene.LINE_GAP, lines[1].start)
        assertEquals(lines[1].end + PrologueScene.TURN_PAUSE, lines[2].start)
        assertEquals(lines[2].end + PrologueScene.REVEAL_GAP, lines[3].start)
        for (l in lines) assertEquals(DirectorScene.ticks(l.line.ms), l.end - l.start)
        assertTrue(lines.first().start > PrologueScene.TRAVEL, "the camera is at rest before he speaks")
    }

    @Test
    fun `subtitles come with the voice and never ahead of it or into the next line`() {
        val timeline = PrologueScene.SCENE
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
    fun `every cue of his four lines and the title have their text in English and in Spanish`() {
        fun keys(file: String): Set<String> {
            val stream = PrologueTest::class.java.classLoader.getResourceAsStream("assets/every_tunnel/lang/$file.json")!!
            return stream.use { JsonParser.parseString(it.reader().readText()).asJsonObject.keySet() }
        }
        val en = keys("en_us")
        val es = keys("es_es")
        for (key in voice.lines.flatMap { it.cues }.map { it.key } + PrologueScene.TITLE_KEY) {
            assertTrue(key in en, "$key in en_us")
            assertTrue(key in es, "$key in es_es")
        }
    }

    // ---- the cues

    @Test
    fun `his hands shake with the third line and he changes as it ends`() {
        assertEquals(lines[2].start, cues.trembleAt)
        assertEquals(lines[2].end, cues.swapAt)
        assertEquals(lines[3].end, cues.cutAt)
        assertEquals(cues.cutAt + PrologueScene.SILENCE, cues.titleAt)
    }

    @Test
    fun `the lights flicker faster and faster from his second cue, the last beat round the change`() {
        val beats = cues.darkBeats
        assertTrue(beats.size >= 5, "a real flicker: ${beats.size} beats")
        assertTrue(beats.first().first >= lines[2].start + voice.line("prologue_03").cues[1].atMs / 50, "not before he says what is happening")
        val gaps = beats.zipWithNext().map { (a, b) -> b.first - a.last - 1 }
        assertTrue(gaps.all { it >= 3 }, "light between beats: $gaps")
        // The last gap is only what is left before the beat of the final change, and may be longer: a held breath.
        assertTrue(gaps.dropLast(1).zipWithNext().all { (a, b) -> b <= a }, "ever shorter: $gaps")
        val change = beats.last()
        assertTrue(cues.swapAt in change, "the change happens in the dark")
        assertTrue(cues.swapAt - 2 in change && cues.swapAt + 3 in change)
        assertTrue(!cues.lightsOn(cues.swapAt) && cues.lightsOn(change.last + 1), "the lights come back with him already changed")
        assertTrue(cues.lightsOn(0) && cues.lightsOn(cues.trembleAt))
        assertTrue(beats.zipWithNext().all { (a, b) -> a.last < b.first }, "in order, apart")
    }

    @Test
    fun `he goes through the stages one at a time, each hidden in a long dark beat`() {
        assertTrue(cues.trembleAt < cues.sickAt && cues.sickAt < cues.stretchAt && cues.stretchAt < cues.swapAt)
        for (at in listOf(cues.sickAt, cues.stretchAt)) {
            val beat = cues.darkBeats.single { at in it }
            assertEquals(4, beat.last - beat.first + 1, "a long beat")
            assertEquals(beat.first + 1, at, "a tick into the dark")
            assertTrue(cues.darkBeats.last() != beat, "not the last beat")
        }
        assertEquals(
            listOf(
                PrologueStages.WORKING, PrologueStages.TREMBLING, PrologueStages.SICK, PrologueStages.STRETCHED, PrologueStages.PALE,
            ),
            listOf(0, cues.trembleAt, cues.sickAt, cues.stretchAt, cues.swapAt).map { cues.stageAt(it) },
        )
        assertEquals(PrologueStages.TREMBLING, cues.stageAt(cues.sickAt - 1))
        assertEquals(PrologueStages.PALE, cues.stageAt(cues.cutAt))
        assertEquals(listOf(1, 2, 3, 4), cues.changes.map { it.second })
        assertTrue(cues.changes.zipWithNext().all { (a, b) -> a.first < b.first })
    }

    @Test
    fun `at each stage he turns a little more from the bench towards the camera`() {
        val bench = frame.yaw
        val toCamera = PrologueScene.facing(frame, PrologueStages.PALE)
        assertEquals(bench, PrologueScene.facing(frame, PrologueStages.WORKING), eps)
        assertEquals(bench, PrologueScene.facing(frame, PrologueStages.TREMBLING), eps)
        val turned = listOf(PrologueStages.SICK, PrologueStages.STRETCHED, PrologueStages.PALE).map { abs(PrologueScene.facing(frame, it) - bench) }
        assertTrue(turned[0] > 10.0 && turned[0] < turned[1] && turned[1] < turned[2], "turning: $turned")
        assertEquals(0.0, wrap(toCamera - PrologueScene.facingCamera(frame)), 1e-6)
    }

    @Test
    fun `facing the camera, the Pale man looks at where it settled`() {
        val yaw = Math.toRadians(PrologueScene.facing(frame, PrologueStages.PALE))
        val ahead = Pair(-sin(yaw), cos(yaw))
        val settled = at(cues.swapAt.toDouble())
        val toCamera = Pair(settled.x - frame.x, settled.z - frame.z)
        val length = Math.hypot(toCamera.first, toCamera.second)
        assertTrue((ahead.first * toCamera.first + ahead.second * toCamera.second) / length > 0.95, "looks at the camera")
        assertTrue(abs(wrap(PrologueScene.facing(frame, PrologueStages.PALE) - plan.scientist.yaw)) > 90.0, "he has turned round from the bench")
    }

    @Test
    fun `the camera shakes harder at each stage and violently at the change`() {
        assertEquals(0.0, PrologueScene.shake(cues.trembleAt - 1.0, cues), eps)
        assertEquals(0.0, PrologueScene.shake(cues.cutAt + 1.0, cues), eps)
        val tremor = PrologueScene.shake(cues.trembleAt + 5.0, cues)
        assertTrue(tremor in 0.0..0.15, "a faint tremor at first: $tremor")
        val sick = PrologueScene.shake(cues.sickAt.toDouble(), cues)
        val stretched = PrologueScene.shake(cues.stretchAt.toDouble(), cues)
        val change = PrologueScene.shake(cues.swapAt.toDouble(), cues)
        assertTrue(sick > tremor && stretched > sick && change > stretched, "$tremor $sick $stretched $change")
        assertEquals(1.0, change, 0.01)
        assertTrue(PrologueScene.shake(cues.swapAt + 80.0, cues) < 0.2, "it dies away to a tremor while he unfolds")
    }

    @Test
    fun `the flash burns the change white and is gone in a moment`() {
        assertEquals(0.0, PrologueScene.flash(cues.swapAt - 1.0, cues), eps)
        assertTrue(PrologueScene.flash(cues.swapAt.toDouble(), cues) > 0.8)
        assertTrue(PrologueScene.flash(cues.swapAt + 10.0, cues) < PrologueScene.flash(cues.swapAt + 2.0, cues))
        assertEquals(0.0, PrologueScene.flash(cues.swapAt + 40.0, cues), eps)
    }

    @Test
    fun `the vignette closes in until the change and opens after it`() {
        val ticks = (cues.trembleAt..cues.swapAt + 70).map { PrologueScene.vignette(it.toDouble(), cues) }
        assertTrue(ticks.all { it in 0.0..1.0 })
        assertEquals(0.0, PrologueScene.vignette(cues.trembleAt - 1.0, cues), eps)
        assertTrue(PrologueScene.vignette(cues.swapAt.toDouble(), cues) > PrologueScene.vignette(cues.trembleAt + 20.0, cues))
        assertEquals(0.0, PrologueScene.vignette(cues.swapAt + 61.0, cues), eps)
    }

    @Test
    fun `after the change only every other light comes back`() {
        val n = plan.lights.size
        val back = cues.darkBeats.last().last + 1
        assertTrue((0 until n).all { cues.lightOn(0, it) }, "all on at the start")
        assertTrue((0 until n).all { !cues.lightOn(cues.swapAt, it) }, "all off in the change's beat")
        val after = (0 until n).count { cues.lightOn(back, it) }
        assertEquals((n + 1) / 2, after, "half of them")
        assertTrue(cues.lightOn(back, 0) && !cues.lightOn(back, 1))
    }

    // ---- black, the title, the end

    @Test
    fun `the scene opens out of black, goes black at the cut and stays so`() {
        val timeline = PrologueScene.SCENE
        assertEquals(1.0, timeline.blackAt(0.0), eps)
        assertEquals(1.0, timeline.blackAt(timeline.blackTicks - 1.0), eps)
        assertTrue(timeline.blackAt(timeline.blackTicks + timeline.fadeTicks / 2.0) in 0.01..0.99, "half way, half black")
        assertEquals(0.0, timeline.blackAt((timeline.blackTicks + timeline.fadeTicks).toDouble()), eps)
        assertEquals(0.0, timeline.blackAt(cues.cutAt - 1.0), eps, "clear to the last tick of the scream")
        assertEquals(1.0, timeline.blackAt(cues.cutAt.toDouble()), eps, "dry")
        assertEquals(1.0, timeline.blackAt(scene.totalTicks.toDouble()), eps)
    }

    @Test
    fun `the title is typed on the black, held and faded, and the scene ends after it`() {
        val title = PrologueScene.SCENE.title!!
        assertEquals(PrologueScene.TITLE_KEY, title.key)
        assertEquals(cues.titleAt, title.start)
        assertEquals(0, title.visible(title.start - 1.0, 9))
        assertEquals(9, title.visible(title.start + title.typeTicks.toDouble(), 9))
        assertEquals(5, title.visible(title.start + title.typeTicks / 2.0, 9), "half the letters half way")
        val typedWords = listOf(9, 10).map { title.visible(title.start + title.typeTicks / 2.0, it) }
        assertTrue(typedWords[1] >= typedWords[0], "a longer translation types as long")
        assertEquals(1.0, title.alpha(title.start + title.typeTicks + title.hold - 1.0), eps)
        assertTrue(title.alpha(title.end - title.fade / 2.0) in 0.4..0.6)
        assertEquals(0.0, title.alpha(title.end.toDouble()), eps)
        assertEquals(title.end + Cutscene.BANDS_TICKS, scene.totalTicks)
    }

    // ---- sound

    @Test
    fun `the hum and the drone are dead at the cut, the counter silent`() {
        val cut = cues.cutAt
        assertEquals(0.0, PrologueScene.humLevel(0.0, cut), eps)
        assertEquals(1.0, PrologueScene.humLevel(cut / 2.0, cut), eps)
        assertEquals(1.0, PrologueScene.humLevel(cut - 1.0, cut), eps)
        assertEquals(0.0, PrologueScene.humLevel(cut.toDouble(), cut), eps)
        assertEquals(0.0, PrologueScene.droneLevel(cut.toDouble(), cues.swapAt, cut), eps)
        assertEquals(0.0, PrologueScene.geigerRate(cut.toDouble(), cut), eps)
    }

    @Test
    fun `the drone rises to the change and stays there`() {
        val swap = cues.swapAt
        val cut = cues.cutAt
        assertEquals(0.0, PrologueScene.droneLevel(0.0, swap, cut), eps)
        val early = PrologueScene.droneLevel(swap * 0.2, swap, cut)
        val late = PrologueScene.droneLevel(swap * 0.8, swap, cut)
        assertTrue(early in 0.05..0.4, "a murmur at first: $early")
        assertTrue(late > 2 * early, "rising: $early then $late")
        assertEquals(1.0, PrologueScene.droneLevel(swap.toDouble(), swap, cut), 1e-6)
        assertEquals(1.0, PrologueScene.droneLevel(cut - 1.0, swap, cut), 1e-6, "full through his scream")
    }

    @Test
    fun `the counter clicks faster and faster`() {
        val cut = cues.cutAt
        val rates = (0 until cut step 20).map { PrologueScene.geigerRate(it.toDouble(), cut) }
        assertTrue(rates.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(rates.last() > 2 * rates.first())
        val clicks = GeigerClicks(7)
        fun mean(rate: Double) = (0 until 20_000).sumOf { clicks.counts(it, rate) } / 20_000.0
        assertEquals(1.0 / 20.0, mean(1.0), 0.01)
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

    // ---- the room

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
