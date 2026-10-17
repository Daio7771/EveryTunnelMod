package daio7771.everytunnel.core

import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelParserTest {

    private val exampleDir = Path.of("examples/dev_flat_datapack/every_tunnel_levels/data/every_tunnel/levels")

    @Test
    fun `example level files parse without problems`() {
        for (id in listOf("level_1", "level_2")) {
            val result = LevelParser.parse(id, Files.readString(exampleDir.resolve("$id.json")))
            assertEquals(emptyList(), result.problems, id)
            assertNotNull(result.level, id)
        }
    }

    @Test
    fun `zone corners in any order are normalized and inclusive`() {
        val level = LevelParser.parse(
            "test",
            """{ "order": 1, "zones": [ { "id": "a", "from": [10, 5, 10], "to": [0, 0, 0] } ] }""",
        ).level!!
        assertEquals(BlockBox(0, 0, 0, 10, 5, 10), level.zones.single().box)
        assertTrue(level.zones.single().box.contains(10.9, 5.5, 0.0))
        assertFalse(level.zones.single().box.contains(11.0, 0.0, 0.0))
        assertEquals("minecraft:overworld", level.dimension)
        assertEquals("test", level.name)
    }

    @Test
    fun `broken entries are skipped but the level is kept`() {
        val result = LevelParser.parse(
            "test",
            """{ "order": 1, "zones": [
                 { "id": "ok", "from": [0, 0, 0], "to": [1, 1, 1] },
                 { "id": "ok", "from": [0, 0, 0], "to": [1, 1, 1] },
                 { "id": "Bad-Id", "from": [0, 0, 0], "to": [1, 1, 1] },
                 { "id": "half", "from": [0.5, 0, 0], "to": [1, 1, 1] },
                 { "id": "short", "from": [0, 0], "to": [1, 1, 1] }
               ],
               "spawn_points": [ { "id": "nowhere" } ] }""",
        )
        assertEquals(listOf("ok"), result.level!!.zones.map { it.id })
        assertEquals(emptyList(), result.level.spawnPoints)
        // Each broken entry reports its cause, plus a "skipped" line when the id was readable.
        assertEquals(8, result.problems.size, result.problems.joinToString("\n"))
    }

    @Test
    fun `level without order or zones is rejected`() {
        assertNull(LevelParser.parse("test", """{ "zones": [ { "id": "a", "from": [0,0,0], "to": [1,1,1] } ] }""").level)
        assertNull(LevelParser.parse("test", """{ "order": 1, "zones": [] }""").level)
        assertNull(LevelParser.parse("test", "[]").level)
    }

    @Test
    fun `level manager finds zones by position and sorts by order`() {
        val l2 = LevelParser.parse("level_2", """{ "order": 2, "zones": [ { "id": "b", "from": [10,0,0], "to": [19,9,9] } ] }""").level!!
        val l1 = LevelParser.parse("level_1", """{ "order": 1, "zones": [ { "id": "a", "from": [0,0,0], "to": [9,9,9] } ] }""").level!!
        assertEquals(emptyList(), LevelManager.replaceAll(listOf(l2, l1)))
        assertEquals(listOf("level_1", "level_2"), LevelManager.all().map { it.id })
        assertEquals("b", LevelManager.zoneAt("minecraft:overworld", 15.0, 1.0, 1.0)?.zone?.id)
        assertNull(LevelManager.zoneAt("minecraft:the_nether", 15.0, 1.0, 1.0))
        assertNull(LevelManager.levelAt("minecraft:overworld", 25.0, 1.0, 1.0))
        LevelManager.replaceAll(emptyList())
    }
}
