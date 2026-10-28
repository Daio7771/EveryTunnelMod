package daio7771.everytunnel.core.hiding

import kotlin.test.Test
import kotlin.test.assertEquals

class LookLimitTest {

    private fun limited(yaw: Float, centre: Float) = yaw + LookLimit.yawCorrection(yaw, centre)

    @Test
    fun `a look within the limit is left alone`() {
        assertEquals(0f, LookLimit.yawCorrection(100f, 90f))
        assertEquals(0f, LookLimit.yawCorrection(60f, 90f))
    }

    @Test
    fun `a look past the limit stops at it, on either side`() {
        assertEquals(120f, limited(150f, 90f))
        assertEquals(60f, limited(10f, 90f))
    }

    @Test
    fun `the limit works across the 180 line and on unwrapped yaws`() {
        assertEquals(210f, limited(240f, 180f))
        assertEquals(-150f, limited(-120f, -180f))
        assertEquals(780f, limited(785f, 30f))       // two turns round: stays near 780, no jump of 360
        assertEquals(-170f, limited(-170f, 170f))    // only 20 degrees apart, across the 180 line
    }

    @Test
    fun `the pitch keeps to its limit`() {
        assertEquals(15f, LookLimit.pitch(60f))
        assertEquals(-15f, LookLimit.pitch(-90f))
        assertEquals(5f, LookLimit.pitch(5f))
    }
}
