package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Level 2's states and encounters, and `level.json` as the jar has it: the checkpoints and the way on through the states. */
class Ch2L2LevelTest {

    private val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())
    private val level = data.level

    /** The route of `tools/map/chapter2/ch2l2/layout.json`, in order. */
    private val route = listOf(
        "sealed_entry", "collector_junction", "hunter_galleries", "pump_overlook", "vibration_chamber", "lower_catacombs", "observer_wall",
        "observer_chamber", "return_gallery", "observer_wall", "flood_collector", "rising_walkways", "upper_gallery", "pumping_station",
        "level_exit", "maw_entry", "maw_centre", "maw_escape", "reunion_player", "lead_1", "lead_2", "lead_3", "lift_player", "chapter_end",
    )

    private fun at(anchor: String): (String) -> Boolean {
        val p = data.anchors.point(anchor)!!.position
        return { zone -> data.anchors.zone(zone)?.box?.contains(p.x, p.y, p.z) == true }
    }

    @Test
    fun `the states run in order, and each encounter is a run of them with the state before it`() {
        assertEquals(Ch2L2State.ARRIVED, Ch2L2State.START)
        assertEquals(Ch2L2State.GALLERIES, Ch2L2State.ARRIVED.next())
        assertNull(Ch2L2State.DONE.next())
        assertEquals(Ch2L2State.GUARD_DEAD, Ch2L2State.parse("guard-dead"))
        for (event in Ch2L2Event.entries) {
            assertTrue(event.before < event.first && event.first < event.after, "$event")
            assertEquals(event, Ch2L2Event.parse(event.id.uppercase()))
        }
        assertEquals("ahead", Ch2L2Event.GUARD.progress(Ch2L2State.CATACOMBS))
        assertEquals("now", Ch2L2Event.GUARD.progress(Ch2L2State.GUARD_FIGHT))
        assertEquals("past", Ch2L2Event.GUARD.progress(Ch2L2State.GUARD_DEAD))
        assertEquals(listOf(0, 1, 2, 3, 3, 3, 3), listOf(Ch2L2State.GUARD_DEAD, Ch2L2State.FLOOD_1, Ch2L2State.FLOOD_2, Ch2L2State.FLOOD_3, Ch2L2State.STATION, Ch2L2State.MAW, Ch2L2State.DONE).map { it.floodStage })
    }

    @Test
    fun `the jar's level file reads without a problem and names only anchors and zones that exist`() {
        assertEquals(emptyList(), data.problems)
        assertEquals("entry", level.checkpoints.first().id)
        assertEquals("sealed_entry", level.checkpoints.first().anchor)
        assertTrue(level.flashlight)
        for (mode in level.lights.values) assertTrue(mode in Ch2L2Level.LIGHT_MODES)
    }

    @Test
    fun `the checkpoints come in the route's order, each standing in its own zone, one before every encounter`() {
        val order = level.checkpoints.map { route.indexOf(it.anchor) }
        assertTrue(order.none { it < 0 } && order == order.sorted(), "${level.checkpoints.map { it.anchor }}")
        for (c in level.checkpoints) c.zone?.let { zone -> assertTrue(at(c.anchor)(zone), "${c.id}: ${c.anchor} is not in $zone") }
        for (event in Ch2L2Event.entries) assertNotNull(level.checkpointBefore(event), "no checkpoint before ${event.id}")
    }

    @Test
    fun `walking the route only ever moves the state on, and every checkpoint replays its encounter`() {
        var state = Ch2L2State.START
        for (place in route) {
            val reached = level.stateAhead(state, at(place))
            if (reached != null) state = reached
        }
        assertEquals(Ch2L2State.MAW, state)
        // Reset (or a death) at the checkpoint before an encounter: the state before it, and standing there starts it again, or walking on does.
        for (event in Ch2L2Event.entries) {
            val checkpoint = level.checkpointBefore(event)!!
            val reached = level.stateAhead(event.before, at(checkpoint.anchor))
            assertTrue(reached == null || reached == event.first, "${event.id}: at ${checkpoint.anchor} the state jumps to $reached")
        }
    }

    @Test
    fun `a checkpoint is taken only ahead of the last one, the furthest first`() {
        val inVestibule: (String) -> Boolean = { it == "zone_observer_wall" }
        assertEquals("vestibule", level.checkpointAhead("entry", inVestibule)?.id)
        assertNull(level.checkpointAhead("vestibule", inVestibule))
        assertNull(level.checkpointAhead("collector", inVestibule))
        assertEquals("entry", level.checkpointAhead(null, { it == "zone_sealed_entry" })?.id)
        assertNull(level.stateAhead(Ch2L2State.GUARD_DEAD, inVestibule))
    }

    @Test
    fun `a broken or wrong override is reported and the jar's copy or the defaults are used`() {
        val broken = Ch2L2Data.parse(mapOf("level" to "{ not json"), Ch2L2Files.readAllBundled())
        assertTrue(broken.problems.any { it.startsWith("level.json (override)") }, "${broken.problems}")
        assertEquals(level, broken.level)
        val wrong = Ch2L2Level.parse(
            JsonParser.parseString(
                """{"start": {"battery": 2.0}, "lights": {"zone_a": "dim"}, "checkpoints": [{"id": "a", "anchor": "x", "zone": "z", "before": "boss"}],
                   "progress": [{"zone": "z", "state": "nowhere"}], "typo": 1}""",
            ).asJsonObject,
        )
        assertEquals(1.0, wrong.level.startBattery)
        assertTrue(wrong.level.lights.isEmpty())
        assertNull(wrong.level.checkpoints.single().before)
        assertTrue(wrong.level.reaches.isEmpty())
        assertEquals(5, wrong.problems.size, "${wrong.problems}")
    }
}
