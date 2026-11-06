package daio7771.everytunnel.entities

import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistortionTest {

    private val origin = Position(0.0, 0.0, 0.0)
    private val south = Position(0.0, 0.0, 10.0)
    private val player = UUID.randomUUID()

    @Test
    fun `in front means within 90 degrees either side, whatever the pitch`() {
        assertTrue(Gaze.inFront(origin, 0.0, south))                       // yaw 0 faces south
        assertTrue(Gaze.inFront(origin, 80.0, south))
        assertFalse(Gaze.inFront(origin, 100.0, south))
        assertFalse(Gaze.inFront(origin, 180.0, south))
        assertTrue(Gaze.inFront(origin, 90.0, Position(-5.0, 3.0, 0.0)))   // 90 faces west
    }

    @Test
    fun `the crosshair is on a target within the aim, wider close up`() {
        val eye = Position(0.0, 1.6, 0.0)
        val ahead = Position(0.0, 1.6, 8.0)
        assertTrue(Gaze.onTarget(eye, 0.0, 0.0, ahead, 0.2, 5.0))
        assertTrue(Gaze.onTarget(eye, 4.0, 0.0, ahead, 0.2, 5.0))
        assertFalse(Gaze.onTarget(eye, 10.0, 0.0, ahead, 0.2, 5.0))
        assertFalse(Gaze.onTarget(eye, 0.0, 30.0, ahead, 0.2, 5.0))        // looking down at the floor
        val close = Position(0.0, 1.6, 1.0)
        assertTrue(Gaze.onTarget(eye, 10.0, 0.0, close, 0.2, 5.0))         // 0.2 across at 1 block is about 11 degrees
    }

    @Test
    fun `yaw towards a point`() {
        assertEquals(0.0, Gaze.yawTowards(origin, south), 1e-9)
        assertEquals(90.0, Gaze.yawTowards(origin, Position(-1.0, 0.0, 0.0)), 1e-9)
        assertEquals(-170.0, Gaze.wrap(190.0), 1e-9)
    }

    @Test
    fun `a Cloud kills after the grace of looking, and looking away starts it over`() {
        val clock = CloudClock(graceTicks = 20, leaveTicks = 120)
        repeat(19) { assertTrue(clock.tick(setOf(player)).kill.isEmpty()) }
        assertEquals(0.0, clock.tick(emptySet()).stare, 1e-9)
        repeat(19) { assertTrue(clock.tick(setOf(player)).kill.isEmpty()) }
        assertEquals(setOf(player), clock.tick(setOf(player)).kill)
    }

    @Test
    fun `a Cloud goes once nobody has looked its way for a while`() {
        val clock = CloudClock(graceTicks = 20, leaveTicks = 120)
        repeat(119) { assertFalse(clock.tick(emptySet()).leave) }
        clock.tick(setOf(player))                                           // a glance starts it over
        repeat(119) { assertFalse(clock.tick(emptySet()).leave) }
        assertTrue(clock.tick(emptySet()).leave)
    }

    @Test
    fun `a Junior ends after a stare without looking away`() {
        val clock = JuniorClock(stareTicks = 80)
        repeat(79) { assertFalse(clock.tick(setOf(player)).done) }
        assertEquals(0.0, clock.tick(emptySet()).stare, 1e-9)
        repeat(79) { assertFalse(clock.tick(setOf(player)).done) }
        val last = clock.tick(setOf(player))
        assertTrue(last.done)
        assertEquals(1.0, last.stare, 1e-9)
    }

    @Test
    fun `the next Cloud comes after a wait spent in Cloud zones, with none about`() {
        val schedule = CloudSchedule(minTicks = 100, maxTicks = 200, random = { 0.5 })
        repeat(149) { assertFalse(schedule.tick(inCloudZone = true, cloudAbout = false)) }
        repeat(50) { assertFalse(schedule.tick(inCloudZone = false, cloudAbout = false)) }   // elsewhere: no count
        repeat(50) { assertFalse(schedule.tick(inCloudZone = true, cloudAbout = true)) }     // one about: no count
        assertTrue(schedule.tick(inCloudZone = true, cloudAbout = false))
        assertTrue(schedule.tick(inCloudZone = true, cloudAbout = false))                   // due until one comes
        schedule.appeared()
        assertFalse(schedule.due)
    }
}
