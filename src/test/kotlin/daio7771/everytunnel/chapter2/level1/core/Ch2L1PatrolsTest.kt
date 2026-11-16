package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonParser
import daio7771.everytunnel.core.level.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1PatrolsTest {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val result = Ch2L1PatrolsParser.parse(data.document("patrols"))
    private val watch = result.patrols.watch

    @Test
    fun `the jar's patrols file reads without a problem and says what the plan says`() {
        assertEquals(emptyList(), result.problems)
        assertEquals(listOf("patrol_a", "patrol_b", "patrol_c"), result.patrols.patrols.map { it.id })
        assertEquals(listOf("guard_1", "guard_2", "guard_1"), result.patrols.patrols.map { it.skin })
        assertEquals(listOf(null, null, Ch2L1State.ACT3_OVERTIME), result.patrols.patrols.map { it.from })
        assertEquals(8.0, watch.range)
        assertEquals(4.0, watch.crouchRange)
        assertEquals(2.0, watch.proximity)
        assertEquals(200, watch.stareTicks)
        assertEquals(100, result.patrols.patrols[0].pauses.single().ticks)
        val anchors = data.anchors
        assertTrue(anchors.path("patrol_a_waypoints")!!.closed, "patrol A goes round")
        assertFalse(anchors.path("patrol_b_waypoints")!!.closed, "patrol B goes there and back")
        assertTrue(anchors.path("patrol_c_waypoints")!!.closed, "patrol C goes round the LAB")
    }

    @Test
    fun `everything it names is in the anchors`() {
        val tasks = Ch2L1TasksParser.parse(data.document("tasks")).tasks
        val speakers = Ch2L1SpeakersParser.parse(data.document("speakers")).speakers
        assertEquals(emptyList(), Ch2L1Wiring.problems(data.anchors, tasks, speakers, patrols = result.patrols))
    }

    @Test
    fun `a bad file is told`() {
        val bad = Ch2L1PatrolsParser.parse(JsonParser.parseString(
            """{ "watch": { "range": -1, "forbidden": [ { "zone": "x" } ] }, "patrols": [ { "id": "a", "skin": "nobody", "path": "p" }, { "id": "a", "path": "q" } ], "typo": 0 }""",
        ).asJsonObject)
        assertTrue(bad.problems.any { it.startsWith("watch.range") })
        assertTrue(bad.problems.any { "forbidden[0].back" in it })
        assertTrue(bad.problems.any { "skin" in it })
        assertTrue(bad.problems.any { "same id" in it })
        assertTrue(bad.problems.any { it.startsWith("typo") })
    }

    private fun p(x: Double, z: Double) = Position(x, 0.0, z)

    @Test
    fun `a round goes round, stopping where it says`() {
        val runner = Ch2L1PatrolRunner(listOf(p(0.0, 0.0), p(10.0, 0.0), p(10.0, 10.0)), closed = true, pauses = mapOf(1 to 3))
        assertEquals(Ch2L1PatrolRunner.Command.Walk(10.0, 0.0, 0.0, brake = true), runner.tick(0.0, 0.0))
        repeat(3) { assertIs<Ch2L1PatrolRunner.Command.Stand>(runner.tick(10.0, 0.0)) }
        assertEquals(Ch2L1PatrolRunner.Command.Walk(10.0, 0.0, 10.0, brake = false), runner.tick(10.0, 0.0))
        assertEquals(Ch2L1PatrolRunner.Command.Walk(0.0, 0.0, 0.0, brake = false), runner.tick(10.0, 10.0))
        assertEquals(Ch2L1PatrolRunner.Command.Walk(10.0, 0.0, 0.0, brake = true), runner.tick(0.0, 0.0))
    }

    @Test
    fun `an open path goes there and back`() {
        val runner = Ch2L1PatrolRunner(listOf(p(0.0, 0.0), p(5.0, 0.0), p(9.0, 0.0)), closed = false, pauses = emptyMap())
        assertEquals(5.0, (runner.tick(0.0, 0.0) as Ch2L1PatrolRunner.Command.Walk).x)
        assertEquals(9.0, (runner.tick(5.0, 0.0) as Ch2L1PatrolRunner.Command.Walk).x)
        assertEquals(5.0, (runner.tick(9.0, 0.0) as Ch2L1PatrolRunner.Command.Walk).x)
        assertEquals(0.0, (runner.tick(5.0, 0.0) as Ch2L1PatrolRunner.Command.Walk).x)
        assertEquals(5.0, (runner.tick(0.0, 0.0) as Ch2L1PatrolRunner.Command.Walk).x)
    }

    @Test
    fun `a guard sees in front of him within range, and close behind`() {
        // Yaw 0 looks south (+z).
        assertTrue(Ch2L1Watch.inView(0.0, 0.0, 0f, 0.0, 6.0, 8.0, 70.0, 2.0))
        assertFalse(Ch2L1Watch.inView(0.0, 0.0, 0f, 0.0, 9.0, 8.0, 70.0, 2.0), "too far")
        assertFalse(Ch2L1Watch.inView(0.0, 0.0, 0f, 5.0, 2.0, 8.0, 70.0, 2.0), "too far to a side")
        assertFalse(Ch2L1Watch.inView(0.0, 0.0, 0f, 0.0, -4.0, 8.0, 70.0, 2.0), "behind him")
        assertTrue(Ch2L1Watch.inView(0.0, 0.0, 0f, 0.0, -1.5, 8.0, 70.0, 2.0), "behind him but close")
        assertTrue(Ch2L1Watch.inView(0.0, 0.0, 90f, -6.0, 0.0, 8.0, 70.0, 2.0), "yaw 90 looks west")
    }

    @Test
    fun `suspicion fills the question mark, a warning, a chase and a catch`() {
        val w = Ch2L1Watch(watch)
        assertEquals(Ch2L1Watch.Event.NOTICED, w.tick(suspicious = true, seen = true, distance = 6.0))
        repeat(watch.noticeTicks - 2) { assertNull(w.tick(true, true, 6.0)) }
        assertEquals(Ch2L1Watch.Event.WARNED, w.tick(true, true, 6.0))
        assertEquals(Ch2L1Watch.Phase.WARNED, w.phase)
        repeat(watch.graceTicks - 1) { assertNull(w.tick(true, true, 6.0)) }
        assertEquals(Ch2L1Watch.Event.CHASED, w.tick(true, true, 6.0))
        assertNull(w.tick(true, true, 4.0))
        assertEquals(Ch2L1Watch.Event.CAUGHT, w.tick(true, true, 1.0))
        assertEquals(Ch2L1Watch.Phase.CALM, w.phase)
    }

    @Test
    fun `stopping in time calms him, and out of sight he gives up`() {
        val w = Ch2L1Watch(watch)
        repeat(10) { w.tick(true, true, 6.0) }
        var event: Ch2L1Watch.Event? = null
        repeat(watch.decayTicks) { event = w.tick(false, false, 6.0) ?: event }
        assertEquals(Ch2L1Watch.Event.CALMED, event)
        repeat(watch.noticeTicks + watch.graceTicks) { w.tick(true, true, 6.0) }
        assertEquals(Ch2L1Watch.Phase.CHASING, w.phase)
        event = null
        repeat(watch.giveUpTicks) { event = w.tick(false, false, 6.0) ?: event }
        assertEquals(Ch2L1Watch.Event.GAVE_UP, event)
    }

    @Test
    fun `the LAB is forbidden only from the overtime on, and dark, seen close or far with the flashlight on`() {
        val lab = watch.forbidden.single { it.zone == "lab" }
        assertFalse(lab.applies(Ch2L1State.ACT3_EXPLORE))
        assertTrue(lab.applies(Ch2L1State.ACT3_OVERTIME))
        assertTrue(watch.forbidden.single { it.zone == "office" }.applies(Ch2L1State.ACT1_TASK1))
        assertEquals(watch.range, watch.sightRange(crouching = false, inDark = false, flashlightOn = false))
        assertEquals(watch.crouchRange, watch.sightRange(crouching = true, inDark = false, flashlightOn = true))
        assertEquals(watch.darkRange, watch.sightRange(crouching = false, inDark = true, flashlightOn = false))
        assertEquals(watch.flashlightRange, watch.sightRange(crouching = true, inDark = true, flashlightOn = true))
        assertTrue(watch.flashlightRange > watch.range && watch.darkRange < watch.crouchRange)
    }

    @Test
    fun `a stare counts only while it lasts`() {
        val stare = Ch2L1Stare(watch.stareTicks)
        repeat(watch.stareTicks) { stare.tick(true) }
        assertFalse(stare.over)
        stare.tick(true)
        assertTrue(stare.over)
        stare.tick(false)
        assertFalse(stare.over)
    }
}
