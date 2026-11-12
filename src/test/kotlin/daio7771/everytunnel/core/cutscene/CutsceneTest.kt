package daio7771.everytunnel.core.cutscene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CutsceneTest {

    private val eps = 1e-6

    @Test
    fun `a frame facing west puts forward at -x and his right at -z`() {
        val frame = SceneFrame(10.0, 64.0, 20.0, 90.0)
        val (fx, fy, fz) = frame.at(2.0, 0.0, 1.0)
        assertEquals(8.0, fx, eps)
        assertEquals(65.0, fy, eps)
        assertEquals(20.0, fz, eps)
        val (rx, _, rz) = frame.at(0.0, 3.0, 0.0)
        assertEquals(10.0, rx, eps)
        assertEquals(17.0, rz, eps)
    }

    @Test
    fun `looking points the yaw and pitch at the target`() {
        val south = CameraPose.looking(0.0, 0.0, 0.0, 0.0, 0.0, 5.0)
        assertEquals(0.0, south.yaw, eps)
        assertEquals(0.0, south.pitch, eps)
        val west = CameraPose.looking(0.0, 0.0, 0.0, -5.0, 0.0, 0.0)
        assertEquals(90.0, west.yaw, eps)
        val down = CameraPose.looking(0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        assertEquals(45.0, down.pitch, eps)
    }

    @Test
    fun `blending goes the short way round`() {
        val a = CameraPose(0.0, 0.0, 0.0, 170.0, 0.0)
        val b = CameraPose(0.0, 0.0, 0.0, -170.0, 0.0)
        assertEquals(180.0, CameraPose.blend(a, b, 0.5).yaw, eps)
    }

    @Test
    fun `the scene starts and ends in the player's eyes`() {
        val scene = DirectorScene.SCENE.cutscene
        val frame = SceneFrame(0.0, 64.0, 0.0, 90.0)
        val eyes = CameraPose(-15.0, 65.6, 0.0, -90.0, 5.0)
        val player = Triple(-15.0, 65.6, 0.0)
        assertEquals(eyes, scene.pose(0.0, frame, eyes, player))
        assertEquals(eyes, scene.pose(scene.shotsEnd + scene.flyOutTicks.toDouble(), frame, eyes, player))
        assertEquals(eyes, scene.pose(scene.totalTicks.toDouble(), frame, eyes, player))
    }

    @Test
    fun `shots follow the fly in and the bands slide in and out`() {
        val scene = DirectorScene.SCENE.cutscene
        assertEquals(-1, scene.shotAt(0.0))
        assertEquals(0, scene.shotAt(scene.flyInTicks.toDouble()))
        assertEquals(scene.shots.size, scene.shotAt(scene.shotsEnd.toDouble()))
        assertEquals(0.0, scene.bands(0.0), eps)
        assertEquals(1.0, scene.bands(Cutscene.BANDS_TICKS.toDouble()), eps)
        assertEquals(1.0, scene.bands(scene.shotsEnd.toDouble()), eps)
        assertEquals(0.0, scene.bands(scene.totalTicks.toDouble()), eps)
        assertEquals(scene.shotsEnd + scene.flyOutTicks + Cutscene.BANDS_TICKS, scene.totalTicks)
    }

    @Test
    fun `the first shot looks at his face`() {
        val scene = DirectorScene.SCENE.cutscene
        val frame = SceneFrame(0.0, 64.0, 0.0, 90.0)
        val eyes = CameraPose(-15.0, 65.6, 0.0, -90.0, 5.0)
        val pose = scene.pose(scene.flyInTicks.toDouble(), frame, eyes, Triple(-15.0, 65.6, 0.0))
        assertTrue(pose.x < -12.0)
        // Facing back east, at him.
        assertTrue(kotlin.math.abs(CameraPose(0.0, 0.0, 0.0, pose.yaw, 0.0).yawNear(-90.0).yaw + 90.0) < 5.0)
    }

    @Test
    fun `skipping takes a second press while the notice shows`() {
        val prompt = SkipPrompt(3000L)
        assertFalse(prompt.press(1000L))
        assertTrue(prompt.showing(2000L))
        assertEquals(1f, prompt.alpha(2000L))
        assertTrue(prompt.press(3500L))
        val late = SkipPrompt(3000L)
        assertFalse(late.press(0L))
        assertFalse(late.showing(3000L))
        assertFalse(late.press(3000L))
    }

    @Test
    fun `his lines follow one another with pauses, the mask's the longest`() {
        val timeline = DirectorScene.SCENE
        val lines = timeline.lines
        assertEquals(20, lines.size)
        assertEquals("boss_01", lines.first().line.id)
        for ((a, b) in lines.zipWithNext()) {
            assertTrue(b.start > a.end, "${b.line.id} starts after ${a.line.id} ends")
            assertEquals(DirectorScene.ticks(a.line.ms), a.end - a.start)
        }
        val mask = lines.indexOfFirst { it.line.id == DirectorScene.MASK_LINE }
        val maskGap = lines[mask].start - lines[mask - 1].end
        assertTrue(lines.zipWithNext().all { (a, b) -> b.start - a.end <= maskGap })
    }

    @Test
    fun `cuts fall in the pauses, never while he speaks`() {
        val timeline = DirectorScene.SCENE
        val scene = timeline.cutscene
        var cut = scene.flyInTicks
        for (shot in scene.shots) {
            cut += shot.ticks
            assertTrue(timeline.lines.none { cut > it.start && cut < it.end }, "a cut at $cut")
        }
        assertTrue(timeline.lines.last().end < scene.shotsEnd)
    }

    @Test
    fun `subtitles show only while their line is under way`() {
        val timeline = DirectorScene.SCENE
        assertTrue(timeline.subtitles.size >= timeline.lines.size)
        for ((a, b) in timeline.subtitles.zipWithNext()) assertTrue(a.end <= b.start)
        for (sub in timeline.subtitles) {
            val line = timeline.lines.first { sub.key.contains(it.line.id) }
            assertTrue(sub.start >= line.start && sub.start < line.end, sub.key)
        }
        assertEquals(null, timeline.subtitleAt(0.0))
        val first = timeline.lines.first()
        assertEquals("subtitle.every_tunnel.director.boss_01_1", timeline.subtitleAt(first.start + 1.0)?.key)
    }

    @Test
    fun `his mask comes off in the long pause and lands while he says it is hot`() {
        val timeline = DirectorScene.SCENE
        val lines = timeline.lines.associateBy { it.line.id }
        val mask = timeline.actions.single { it.name == "mask_off" }
        assertEquals(lines.getValue("boss_05").end, mask.start)
        val hot = lines.getValue(DirectorScene.MASK_LINE)
        assertTrue(timeline.maskDown in hot.start..hot.end)
        assertTrue(mask.start + DirectorScene.MASK_TICKS < lines.getValue("boss_07").start)
    }

    @Test
    fun `he stands, points, and sits again before his last line ends`() {
        val timeline = DirectorScene.SCENE
        val names = timeline.actions.map { it.name }
        assertEquals(listOf("stand", "point", "stand", "idle"), names.takeLast(4))
        assertEquals(timeline.actions.indices.toList(), timeline.actions.map { it.index })
        assertTrue(timeline.actions.zipWithNext().all { (a, b) -> a.start <= b.start })
        val last = timeline.lines.last()
        assertTrue(timeline.actions.last().start < last.end)
    }

    @Test
    fun `he looks at the player but where he looks away`() {
        val timeline = DirectorScene.SCENE
        val lines = timeline.lines.associateBy { it.line.id }
        fun middle(id: String) = lines.getValue(id).let { (it.start + it.end) / 2.0 }
        assertEquals(1.0, timeline.gazeAt(middle("boss_01")), eps)
        assertEquals(0.0, timeline.gazeAt(middle("boss_12")), eps)
        assertEquals(0.0, timeline.gazeAt(middle("boss_20")), eps)
        assertEquals(0.0, timeline.gazeAt(timeline.cutscene.totalTicks.toDouble()), eps)
        assertEquals(null, timeline.actionAt(0.0))
    }

    @Test
    fun `the camera flies back to where the player stands at the end`() {
        val scene = DirectorScene.SCENE.cutscene
        val frame = SceneFrame(0.0, 64.0, 0.0, 90.0)
        val eyes = CameraPose(-17.0, 65.6, 0.0, -90.0, 5.0)
        val inside = CameraPose(-14.0, 65.6, 0.5, -90.0, 0.0)
        val player = Triple(-14.0, 65.6, 0.5)
        assertEquals(eyes, scene.pose(0.0, frame, eyes, player, inside))
        assertEquals(inside, scene.pose(scene.totalTicks.toDouble(), frame, eyes, player, inside))
    }

    private fun phrases() = (1..6).map { VoiceLine("loop_$it", 0, 2000, emptyList()) }

    @Test
    fun `he repeats his phrases from the last to the first, and round again`() {
        val loop = DirectorLoop(phrases(), kotlin.random.Random(1))
        loop.start(0)
        val said = mutableListOf<String>()
        for (t in 0..20 * 60 * 20) loop.tick(t, inRoom = true)?.let { said += it.id }
        assertEquals(listOf("loop_6", "loop_5", "loop_4", "loop_3", "loop_2", "loop_1", "loop_6"), said.take(7))
    }

    @Test
    fun `he speaks every 20 to 30 seconds, only with the player in the room`() {
        val loop = DirectorLoop(phrases(), kotlin.random.Random(7))
        loop.start(0)
        val times = mutableListOf<Int>()
        for (t in 0..10 * 60 * 20) loop.tick(t, inRoom = true)?.let { times += t }
        assertTrue(times.first() in DirectorLoop.MIN_GAP..DirectorLoop.MAX_GAP)
        for ((a, b) in times.zipWithNext()) assertTrue(b - a - 40 in DirectorLoop.MIN_GAP..DirectorLoop.MAX_GAP, "${b - a}")
        val away = DirectorLoop(phrases(), kotlin.random.Random(7))
        away.start(0)
        for (t in 0..2000) assertEquals(null, away.tick(t, inRoom = false))
        assertEquals(null, away.tick(2001, inRoom = true))
        val back = (2001..2001 + DirectorLoop.BACK_IN).firstNotNullOfOrNull { t -> away.tick(t, inRoom = true)?.let { t } }
        assertTrue(back != null && back >= 2000 + DirectorLoop.BACK_IN)
    }

    @Test
    fun `he looks at the player less each time`() {
        val loop = DirectorLoop(phrases())
        assertEquals(1.0, loop.gaze(0), eps)
        assertTrue(loop.gaze(3) < loop.gaze(2))
        assertEquals(0.0, loop.gaze(20), eps)
    }

    @Test
    fun `the credits fill the song, intro, four screens, tools, thanks, Level 6, black`() {
        assertEquals(6.0, FinalCredits.screenStart(0), eps)
        assertEquals(78.0, FinalCredits.TOOLS_AT, eps)
        assertEquals(86.0, FinalCredits.THANKS_AT, eps)
        assertEquals(88.0, FinalCredits.NEXT_AT, eps)
        assertEquals(92.0, FinalCredits.BLACK_AT, eps)
        assertTrue(FinalCredits.BLACK_AT + 2.0 <= FinalCredits.LENGTH)
        assertEquals(-1, FinalCredits.screenAt(5.9))
        assertEquals(0, FinalCredits.screenAt(6.0))
        assertEquals(3, FinalCredits.screenAt(77.9))
        assertEquals(-1, FinalCredits.screenAt(78.0))
        assertEquals(3, FinalCredits.groupAt(85.9))
    }

    @Test
    fun `a screen's title types, its lines come in order before its credits, and it all fades out`() {
        assertEquals(0, FinalCredits.typed(1.0, 10))
        assertEquals(10, FinalCredits.typed(5.0, 10))
        val times = (0 until 4).map { FinalCredits.lineAt(it, 4) }
        assertEquals(2.5, times.first(), eps)
        assertTrue(times.zipWithNext().all { (a, b) -> a < b } && times.last() < 11.0)
        assertEquals(1.0, FinalCredits.inScreen(16.0, 12.0), eps)
        assertEquals(0.5, FinalCredits.inScreen(17.5, 12.0), eps)
        assertEquals(0.0, FinalCredits.inScreen(11.0, 12.0), eps)
    }
}
