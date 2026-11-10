package daio7771.everytunnel.core.detector

import daio7771.everytunnel.config.DetectorConfig
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `counts rise from slow at the edge to a crackle next to it, and stop when nothing is near`() {
        assertEquals(0.0, detector.countsPerSecond(0.0))
        assertEquals(1.5, detector.countsPerSecond(1e-9), 1e-6)
        assertEquals(sqrt(1.5 * 120.0), detector.countsPerSecond(0.5), 1e-9)
        assertEquals(120.0, detector.countsPerSecond(1.0), 1e-9)
    }

    @Test
    fun `counts fall at random a tick, as many on average as the rate says`() {
        val random = Random(14)
        assertEquals(0, detector.countsThisTick(0.0, random::nextDouble))
        for (level in listOf(0.2, 0.5, 1.0)) {
            val ticks = 20_000
            val mean = (1..ticks).sumOf { detector.countsThisTick(level, random::nextDouble) }.toDouble() / ticks
            val expected = detector.countsPerSecond(level) / 20.0
            assertTrue(abs(mean - expected) < 0.05 * expected + 0.01, "level $level: $mean counts a tick, expected $expected")
        }
    }
}
