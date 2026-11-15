package daio7771.everytunnel.core.ch2l1

import daio7771.everytunnel.core.ch2l1.Ch2L1IntroScene.toLocal
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.level.Position
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1IntroSceneTest {

    private val bundled = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val anchors = bundled.anchors
    private val spec = Ch2L1SpeakersParser.parse(bundled.document("speakers")).speakers.intro!!
    private val start = anchors.point("spawn_clockin")!!
    private val frame = SceneFrame(start.position.x, start.position.y, start.position.z, start.yaw.toDouble())

    @Test
    fun `a place seen from the frame comes back to its world coordinates`() {
        for (yaw in listOf(0.0, 90.0, 180.0, -37.0)) {
            val f = SceneFrame(10.0, 64.0, -5.0, yaw)
            val local = f.toLocal(Position(14.5, 70.0, 3.25))
            val (x, y, z) = f.at(local.forward, local.right, local.up)
            assertTrue(abs(x - 14.5) < 1e-9 && abs(y - 70.0) < 1e-9 && abs(z - 3.25) < 1e-9, "yaw $yaw gives $x $y $z")
        }
    }

    @Test
    fun `the scene is the shots, the chime as the first begins and the voice after it`() {
        val result = Ch2L1IntroScene.build(spec, anchors, frame, lineMs = 4830, textKey = "k", dingTicks = 30)
        assertNull(result.problem)
        val built = result.built!!
        assertEquals(spec.flyInTicks, built.dingTick)
        assertEquals(spec.flyInTicks + 30, built.lineStart)
        val cutscene = built.timeline.cutscene
        assertEquals(spec.shots.size, cutscene.shots.size)
        assertEquals(spec.flyInTicks + spec.shots.sumOf { it.ticks } + spec.flyOutTicks + 20, cutscene.totalTicks)
        val line = built.timeline.lines.single()
        assertEquals("rowan_loyal_a1", line.line.id)
        assertEquals(built.lineStart, line.start)
        assertTrue(line.end <= cutscene.shotsEnd + spec.flyOutTicks, "the voice ends before the bands are gone")
        assertEquals("k", built.timeline.subtitles.single().key)
    }

    @Test
    fun `an anchor the scene names that is missing is told, and there is no scene`() {
        val broken = spec.copy(shots = listOf(Ch2L1IntroShot("no_such_camera", null, "a1_look_lab", 10)))
        val result = Ch2L1IntroScene.build(broken, anchors, frame, 1000, "k", 30)
        assertNull(result.built)
        assertNotNull(result.problem)
        assertTrue("no_such_camera" in result.problem!!)
    }
}
