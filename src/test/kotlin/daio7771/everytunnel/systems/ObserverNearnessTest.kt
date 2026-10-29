package daio7771.everytunnel.systems

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverNearnessTest {

    private val settings = NearnessSettings()

    private fun ObserverNearness.beats(ticks: Int, distance: Double?): List<ObserverNearness.Step> =
        List(ticks) { tick(distance) }.filter { it.beat != null }

    @Test
    fun `out of range the clock is silent`() {
        val nearness = ObserverNearness(settings)
        assertTrue(nearness.beats(200, settings.clockRange + 1).isEmpty())
        assertTrue(nearness.beats(200, null).isEmpty())
    }

    @Test
    fun `the clock ticks and tocks in turn, faster and louder the nearer he is`() {
        val far = ObserverNearness(settings).beats(200, settings.clockRange - 0.5)
        val near = ObserverNearness(settings).beats(200, settings.closeRange)
        assertTrue(near.size > far.size * 2, "near ${near.size} beats, far ${far.size}")
        assertTrue(near.first().volume > far.first().volume)
        assertEquals(ObserverNearness.Beat.TICK, near[0].beat)
        assertEquals(ObserverNearness.Beat.TOCK, near[1].beat)
        assertEquals(ObserverNearness.Beat.TICK, near[2].beat)
    }

    @Test
    fun `coming into range it ticks at once`() {
        val nearness = ObserverNearness(settings)
        nearness.tick(null)
        assertEquals(ObserverNearness.Beat.TICK, nearness.tick(settings.clockRange - 1).beat)
    }

    @Test
    fun `the warning comes once, and again only after he has gone far`() {
        val nearness = ObserverNearness(settings)
        assertFalse(nearness.tick(settings.warnRange + 1).warn)
        assertTrue(nearness.tick(settings.warnRange - 1).warn)
        assertFalse(nearness.tick(settings.warnRange - 2).warn)
        assertFalse(nearness.tick(settings.rearmRange - 1).warn)
        assertFalse(nearness.tick(settings.warnRange - 1).warn, "not far enough away in between")
        nearness.tick(settings.rearmRange + 1)
        assertTrue(nearness.tick(settings.warnRange - 1).warn)
    }

    @Test
    fun `no warning while he is already after the player`() {
        val nearness = ObserverNearness(settings)
        assertFalse(nearness.tick(settings.warnRange - 1, hunting = true).warn)
        assertFalse(nearness.tick(settings.warnRange - 1).warn)
        assertNull(nearness.tick(settings.clockRange + 1).beat)
    }
}
