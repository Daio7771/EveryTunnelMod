package daio7771.everytunnel.core.detector

import daio7771.everytunnel.config.DetectorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectorTest {

    private val detector = Detector(DetectorConfig())

    @Test
    fun `the icon fills as the Cloud comes nearer, and is empty out of range`() {
        assertEquals(0.0, detector.level(null))
        assertEquals(0.0, detector.level(16.5))
        assertEquals(0.0, detector.level(16.0))
        assertEquals(0.5, detector.level(8.0))
        assertEquals(1.0, detector.level(0.0))
    }

    @Test
    fun `the triangle shows within the danger range only`() {
        assertFalse(detector.danger(null))
        assertFalse(detector.danger(8.1))
        assertTrue(detector.danger(8.0))
        assertTrue(detector.danger(2.0))
    }

    @Test
    fun `beeps speed up from slow at the edge to fast next to it, and stop when nothing is near`() {
        assertNull(detector.beepTicks(0.0))
        assertEquals(24, detector.beepTicks(0.01))
        assertEquals(13, detector.beepTicks(0.5))
        assertEquals(2, detector.beepTicks(1.0))
    }
}
