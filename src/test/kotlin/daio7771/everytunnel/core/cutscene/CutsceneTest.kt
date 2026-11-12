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
        val scene = DirectorScene.SCENE
        val frame = SceneFrame(0.0, 64.0, 0.0, 90.0)
        val eyes = CameraPose(-15.0, 65.6, 0.0, -90.0, 5.0)
        val player = Triple(-15.0, 65.6, 0.0)
        assertEquals(eyes, scene.pose(0.0, frame, eyes, player))
        assertEquals(eyes, scene.pose(scene.shotsEnd + scene.flyOutTicks.toDouble(), frame, eyes, player))
        assertEquals(eyes, scene.pose(scene.totalTicks.toDouble(), frame, eyes, player))
    }

    @Test
    fun `shots follow the fly in and the bands slide in and out`() {
        val scene = DirectorScene.SCENE
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
        val scene = DirectorScene.SCENE
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
}
