package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Data.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ch2L1DataTest {

    private val anchors = """{ "anchors": [ { "id": "a", "pos": [0, 0, 0] } ] }"""
    private val otherAnchors = """{ "anchors": [ { "id": "b", "pos": [1, 1, 1] } ] }"""

    /** One usable copy of every file, as the jar has them. */
    private fun everything(anchorsText: String = anchors) =
        Ch2L1Files.ALL.associateWith { if (it == Ch2L1Files.ANCHORS) anchorsText else "{}" }

    @Test
    fun `the files in the jar are all there and parse without problems`() {
        val bundled = Ch2L1Files.readAllBundled()
        assertEquals(Ch2L1Files.ALL.toSet(), bundled.keys, "a data file is missing from src/main/resources/${Ch2L1Files.BUNDLED_FOLDER}")
        val result = Ch2L1DataParser.parse(emptyMap(), bundled)
        assertEquals(emptyList(), result.problems)
        assertTrue(result.data.sources.values.all { it == Source.BUNDLED })
    }

    @Test
    fun `the jar's anchors have every place the plan and the map's drawing name`() {
        val anchors = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.anchors
        val points = listOf(
            "spawn_clockin", "time_clock", "task1_terminal", "task2_analyzer", "predecessor_locker", "taquillas_exit",
            "aux_lab_door", "chamber_02", "chamber_log", "authorization_desk", "door_anchor", "desk_anchor", "office_keypad",
            "paper_medical", "paper_contract_14", "painting", "card_slide_start", "card_slide_end", "rowan_spawn",
            "restricted_door_east", "sealed_door",
            "task1_terminal_block", "task2_analyzer_block", "task3_broom_block", "speaker_entrance_src", "speaker_lab_src", "speaker_main_src",
            "speaker_west_src",
        ) + (1..6).map { "taquillas_npc_%02d".format(it) } + (1..3).map { "task1_tube_$it" }
        for (id in points) assertTrue(anchors.point(id) != null, "no anchor $id")
        val zones = listOf(
            "task3_sweep_area", "taquillas_trigger", "aux_lab_glass", "prologue_set", "authorized_corridor", "security_room",
            "level2_corridor", "speaker_entrance", "speaker_lab", "speaker_main", "speaker_west",
        )
        for (id in zones) assertTrue(anchors.zone(id) != null, "no zone $id")
        for (id in listOf("patrol_a_waypoints", "patrol_b_waypoints", "task3_dirt_spots")) assertTrue(anchors.path(id) != null, "no path $id")
        assertTrue(anchors.point("spawn_clockin")?.provisional == false, "the spawn is measured on the map: it is not provisional")
        assertTrue(anchors.provisionalCount() > 0, "what depends on furniture is still provisional")
    }

    @Test
    fun `the cutscene files have folders of their own`() {
        assertTrue("cutscenes/meeting" in Ch2L1Files.ALL)
        assertTrue("cutscenes/office" in Ch2L1Files.ALL)
        assertEquals("data/every_tunnel/ch2/l1/cutscenes/office.json", Ch2L1Files.bundledPath("cutscenes/office"))
    }

    @Test
    fun `an override wins over the jar's copy`() {
        val result = Ch2L1DataParser.parse(mapOf("anchors" to otherAnchors, "tasks" to """{ "x": 1 }"""), everything())
        assertEquals(emptyList(), result.problems)
        assertEquals(listOf("b"), result.data.anchors.points.map { it.id })
        assertEquals(Source.OVERRIDE, result.data.sources["anchors"])
        assertEquals(Source.OVERRIDE, result.data.sources["tasks"])
        assertEquals(1, result.data.document("tasks")["x"].asInt)
        assertEquals(Source.BUNDLED, result.data.sources["speakers"])
    }

    @Test
    fun `a broken override is reported and the jar's copy is used`() {
        val result = Ch2L1DataParser.parse(mapOf("anchors" to "{ not json", "patrols" to "[1]"), everything())
        assertEquals(listOf("a"), result.data.anchors.points.map { it.id })
        assertEquals(Source.BUNDLED, result.data.sources["anchors"])
        assertEquals(Source.BUNDLED, result.data.sources["patrols"])
        assertTrue(result.problems.any { it.startsWith("anchors.json (override)") && "invalid JSON" in it })
        assertTrue(result.problems.any { it.startsWith("patrols.json (override)") && "JSON object" in it })
        assertEquals(4, result.problems.size, result.problems.toString())
    }

    @Test
    fun `an override with some bad entries is still used, with its problems`() {
        val text = """{ "anchors": [ { "id": "b", "pos": [1, 1, 1] }, { "id": "c" } ] }"""
        val result = Ch2L1DataParser.parse(mapOf("anchors" to text), everything())
        assertEquals(Source.OVERRIDE, result.data.sources["anchors"])
        assertEquals(listOf("b"), result.data.anchors.points.map { it.id })
        assertTrue(result.problems.all { it.startsWith("anchors.json (override)") })
    }

    @Test
    fun `a file with no usable copy gives an empty one and a problem`() {
        val result = Ch2L1DataParser.parse(emptyMap(), everything() - "npcs" - "anchors")
        assertEquals(Source.MISSING, result.data.sources["npcs"])
        assertEquals(Source.MISSING, result.data.sources["anchors"])
        assertEquals(Ch2L1Anchors.EMPTY, result.data.anchors)
        assertTrue(result.data.document("npcs").entrySet().isEmpty())
        assertEquals(listOf("anchors.json: no usable copy, using an empty one", "npcs.json: no usable copy, using an empty one"), result.problems.sorted())
    }

    @Test
    fun `nothing loaded is a whole level of empty files`() {
        assertEquals(Ch2L1Files.ALL.toSet(), Ch2L1Data.EMPTY.documents.keys)
        assertEquals(Ch2L1Files.ALL.toSet(), Ch2L1Data.EMPTY.sources.keys)
        assertTrue(Ch2L1Data.EMPTY.sources.values.all { it == Source.MISSING })
        assertTrue(Ch2L1Data.EMPTY.document("not_a_file").entrySet().isEmpty())
    }
}
