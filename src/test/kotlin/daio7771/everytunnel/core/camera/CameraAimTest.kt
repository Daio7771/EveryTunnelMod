package daio7771.everytunnel.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraAimTest {

    @Test
    fun `it turns no faster than its servo`() {
        val aim = CameraAim(0.0, 0.0)
        val turned = aim.turnTowards(90.0, 0.0, 0.5, 60.0)
        assertEquals(30.0, turned, 1e-9)
        assertEquals(30.0, aim.yaw, 1e-9)
    }

    @Test
    fun `it stops on its target`() {
        val aim = CameraAim(10.0, -20.0)
        aim.turnTowards(20.0, -20.0, 1.0, 60.0)
        assertEquals(20.0, aim.yaw, 1e-9)
        assertEquals(0.0, aim.turnTowards(20.0, -20.0, 1.0, 60.0), 1e-9)
    }

    @Test
    fun `it turns the shorter way round`() {
        val aim = CameraAim(170.0, 0.0)
        aim.turnTowards(-170.0, 0.0, 0.1, 100.0)
        assertTrue(CameraAim.near(aim.yaw, 180.0, 1e-9), "went through 180, at ${aim.yaw}")
    }

    @Test
    fun `yaw and pitch point a north-facing head`() {
        assertEquals(0.0, CameraAim.yawTo(0.0, -1.0), 1e-9)           // north
        assertEquals(90.0, CameraAim.yawTo(-1.0, 0.0), 1e-9)          // west
        assertEquals(-90.0, CameraAim.yawTo(1.0, 0.0), 1e-9)          // east
        assertEquals(-45.0, CameraAim.pitchTo(-1.0, 1.0), 1e-9)       // down
    }
}
