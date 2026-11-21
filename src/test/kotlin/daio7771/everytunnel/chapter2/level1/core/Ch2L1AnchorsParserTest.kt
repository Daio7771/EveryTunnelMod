package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.common.core.level.BlockBox
import daio7771.everytunnel.common.core.level.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1AnchorsParserTest {

    private fun parse(text: String) = Ch2L1AnchorsParser.parse(text)

    @Test
    fun `a point and a zone parse with their defaults`() {
        val result = parse(
            """
            {
              "anchors": [ { "id": "spawn_clockin", "pos": [1.5, 64, -2.5] } ],
              "zones": [ { "id": "speaker_lab", "from": [0, 64, 0], "to": [9, 68, 9] } ]
            }
            """,
        )
        assertEquals(emptyList(), result.problems)
        val anchors = result.anchors!!
        assertEquals("minecraft:overworld", anchors.dimension)
        assertEquals(Anchor("spawn_clockin", Position(1.5, 64.0, -2.5), 0f, 0f, false), anchors.point("spawn_clockin"))
        assertEquals(AnchorZone("speaker_lab", BlockBox(0, 64, 0, 9, 68, 9), false), anchors.zone("speaker_lab"))
        assertEquals(listOf("spawn_clockin", "speaker_lab"), anchors.names())
    }

    @Test
    fun `facing, dimension and the provisional mark are read`() {
        val anchors = parse(
            """
            {
              "dimension": "minecraft:the_nether",
              "anchors": [ { "id": "door_anchor", "pos": [0, 0, 0], "yaw": -90, "pitch": 15, "provisional": true } ]
            }
            """,
        ).anchors!!
        assertEquals("minecraft:the_nether", anchors.dimension)
        val door = anchors.point("door_anchor")!!
        assertEquals(-90f, door.yaw)
        assertEquals(15f, door.pitch)
        assertTrue(door.provisional)
        assertEquals(1, anchors.provisionalCount())
    }

    @Test
    fun `zone corners in any order are normalized`() {
        val zone = parse("""{ "zones": [ { "id": "z", "from": [9, 68, 9], "to": [0, 64, 0] } ] }""").anchors!!.zone("z")!!
        assertEquals(BlockBox(0, 64, 0, 9, 68, 9), zone.box)
    }

    @Test
    fun `a zone is stood in at its middle, one block over its floor`() {
        val zone = AnchorZone("z", BlockBox(0, 64, 0, 9, 68, 9), false)
        assertEquals(Position(5.0, 65.0, 5.0), zone.standingPoint())
    }

    @Test
    fun `both lists are optional`() {
        val result = parse("{}")
        assertEquals(emptyList(), result.problems)
        assertEquals(Ch2L1Anchors.EMPTY, result.anchors)
    }

    @Test
    fun `an id is unique across points and zones`() {
        val result = parse(
            """
            {
              "anchors": [ { "id": "a", "pos": [0, 0, 0] }, { "id": "a", "pos": [1, 1, 1] } ],
              "zones": [ { "id": "a", "from": [0, 0, 0], "to": [1, 1, 1] } ]
            }
            """,
        )
        val anchors = result.anchors!!
        assertEquals(1, anchors.points.size)
        assertEquals(0, anchors.zones.size)
        assertEquals(2, result.problems.count { "duplicate anchor id" in it }, result.problems.toString())
    }

    @Test
    fun `broken entries are skipped with a problem and the rest still load`() {
        val result = parse(
            """
            {
              "anchors": [
                { "id": "Bad Id", "pos": [0, 0, 0] },
                { "id": "no_pos" },
                { "id": "two_numbers", "pos": [1, 2] },
                { "pos": [0, 0, 0] },
                "not an object",
                { "id": "good", "pos": [0, 0, 0] }
              ],
              "zones": [
                { "id": "fractional", "from": [0.5, 0, 0], "to": [1, 1, 1] },
                { "id": "good_zone", "from": [0, 0, 0], "to": [1, 1, 1] }
              ]
            }
            """,
        )
        val anchors = result.anchors!!
        assertEquals(listOf("good"), anchors.points.map { it.id })
        assertEquals(listOf("good_zone"), anchors.zones.map { it.id })
        assertEquals(9, result.problems.size, result.problems.toString())
        assertTrue(result.problems.any { it.startsWith("anchors[0].id") })
        assertTrue(result.problems.any { it.startsWith("anchors[1].pos") })
        assertTrue(result.problems.any { it.startsWith("zones[0].from") })
    }

    @Test
    fun `a skipped entry does not take its id`() {
        val anchors = parse(
            """{ "anchors": [ { "id": "a" }, { "id": "a", "pos": [0, 0, 0] } ] }""",
        ).anchors!!
        assertEquals(listOf("a"), anchors.points.map { it.id })
    }

    @Test
    fun `values out of range and unknown keys are reported`() {
        val result = parse(
            """
            {
              "dimension": "not a dimension",
              "anchors": [ { "id": "a", "pos": [0, 0, 0], "yaw": 999, "provisonal": true } ],
              "colour": "red"
            }
            """,
        )
        val anchors = result.anchors!!
        assertEquals("minecraft:overworld", anchors.dimension)
        assertEquals(0f, anchors.point("a")!!.yaw)
        assertFalse(anchors.point("a")!!.provisional)
        assertTrue(result.problems.any { it.startsWith("dimension") })
        assertTrue(result.problems.any { it.startsWith("anchors[0].yaw") })
        assertTrue(result.problems.any { it.contains("provisonal") && "unknown key" in it })
        assertTrue(result.problems.any { it.startsWith("colour") && "unknown key" in it })
    }

    @Test
    fun `a file that is not an object is rejected`() {
        assertNull(parse("[1, 2]").anchors)
        assertNull(parse("{ not json").anchors)
        assertNotNull(parse("[1, 2]").problems.singleOrNull())
        assertNotNull(parse("{ not json").problems.singleOrNull())
    }

    @Test
    fun `a path has its points, whether it closes and the provisional mark`() {
        val result = parse(
            """
            {
              "paths": [
                { "id": "patrol_a_waypoints", "closed": true, "provisional": true, "points": [[1.5, 64, 2.5], [9.5, 64, 2.5], [9.5, 64, 8.5]] },
                { "id": "patrol_b_waypoints", "points": [[0, 0, 0], [5, 0, 0]] }
              ]
            }
            """,
        )
        assertEquals(emptyList(), result.problems)
        val anchors = result.anchors!!
        val a = anchors.path("patrol_a_waypoints")!!
        assertEquals(listOf(Position(1.5, 64.0, 2.5), Position(9.5, 64.0, 2.5), Position(9.5, 64.0, 8.5)), a.points)
        assertTrue(a.closed)
        assertTrue(a.provisional)
        assertFalse(anchors.path("patrol_b_waypoints")!!.closed)
        assertEquals(listOf("patrol_a_waypoints", "patrol_b_waypoints"), anchors.names())
        assertEquals(1, anchors.provisionalCount())
    }

    @Test
    fun `a broken point is skipped and a path with fewer than two points is rejected`() {
        val result = parse(
            """
            {
              "paths": [
                { "id": "ok", "points": [[0, 0, 0], [1, 2], [3, 0, 0], "x"] },
                { "id": "one_point", "points": [[0, 0, 0]] },
                { "id": "none" }
              ]
            }
            """,
        )
        val anchors = result.anchors!!
        assertEquals(listOf("ok"), anchors.paths.map { it.id })
        assertEquals(2, anchors.paths.single().points.size)
        assertEquals(2, result.problems.count { it.startsWith("paths[0].points[") }, result.problems.toString())
        assertTrue(result.problems.any { it.startsWith("paths[1].id") && "at least two points" in it })
        assertTrue(result.problems.any { it.startsWith("paths[2].id") && "at least two points" in it })
    }

    @Test
    fun `a path id is unique across points, zones and paths`() {
        val result = parse(
            """{ "anchors": [ { "id": "a", "pos": [0, 0, 0] } ], "paths": [ { "id": "a", "points": [[0, 0, 0], [1, 0, 0]] } ] }""",
        )
        assertEquals(emptyList(), result.anchors!!.paths)
        assertTrue(result.problems.any { "duplicate anchor id" in it })
    }
}
