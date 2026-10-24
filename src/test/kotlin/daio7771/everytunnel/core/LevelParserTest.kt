package daio7771.everytunnel.core

import daio7771.everytunnel.core.level.Ambience
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelEntry
import daio7771.everytunnel.core.level.LevelIntro
import daio7771.everytunnel.core.level.LevelParser
import daio7771.everytunnel.core.level.RandomSound
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

    @Test
    fun `ambience is optional and broken random sounds are skipped`() {
        val zones = """"order": 1, "zones": [{ "id": "a", "from": [0, 0, 0], "to": [1, 1, 1] }]"""
        assertEquals(Ambience.NONE, LevelParser.parse("plain", "{ $zones }").level?.ambience)

        val result = LevelParser.parse(
            "sounds",
            """{ $zones, "ambience": {
                "loop": "every_tunnel:ambient.garage.drone", "loop_volume": 0.6,
                "random": [
                    { "sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5 },
                    { "sound": "every_tunnel:ambient.rumble", "interval": [20, 10], "distance": [3, 12] },
                    { "sound": "Not A Sound", "interval": [1, 2], "distance": [1, 2] },
                    { "sound": "every_tunnel:ambient.relay", "interval": [5, 9], "distance": [10, 40] }
                ] } }""",
        )
        val ambience = assertNotNull(result.level).ambience
        assertEquals("every_tunnel:ambient.garage.drone", ambience.loop)
        assertEquals(0.6, ambience.loopVolume)
        assertEquals(listOf(RandomSound("every_tunnel:ambient.drip", 6.0, 18.0, 3.0, 12.0, 0.5)), ambience.randomSounds)
        // min > max, a malformed id, and a distance beyond what Minecraft plays: each skipped.
        assertEquals(3, result.problems.count { it.endsWith("random sound skipped") }, result.problems.toString())
    }

    @Test
    fun `a spawn point's zone must be a zone of the level`() {
        val result = LevelParser.parse(
            "zones",
            """{ "order": 2, "zones": [{ "id": "hall_1", "from": [0, 0, 0], "to": [9, 4, 9] }],
                "spawn_points": [
                    { "id": "a", "pos": [1, 1, 1], "tags": ["pale_man"], "zone": "hall_1" },
                    { "id": "b", "pos": [2, 1, 2], "tags": ["pale_man"], "zone": "hall_9" },
                    { "id": "c", "pos": [3, 1, 3], "tags": ["pale_man"] }
                ] }""",
        )
        val level = assertNotNull(result.level)
        assertEquals(listOf("hall_1", null, null), level.spawnPoints.map { it.zone })
        assertEquals(1, result.problems.size, result.problems.toString())
    }

    @Test
    fun `intro is optional and its seconds default to 25`() {
        val zones = """"order": 1, "zones": [{ "id": "a", "from": [0, 0, 0], "to": [1, 1, 1] }]"""
        assertNull(LevelParser.parse("plain", "{ $zones }").level?.intro)
        val full = LevelParser.parse("full", """{ $zones, "intro": { "seconds": 12.5, "music": "every_tunnel:music.elevator" } }""")
        assertEquals(LevelIntro(12.5, "every_tunnel:music.elevator"), full.level?.intro)
        assertEquals(emptyList(), full.problems)
        assertEquals(LevelIntro(25.0, null), LevelParser.parse("bare", """{ $zones, "intro": {} }""").level?.intro)
    }

    @Test
    fun `build is optional and must be a function id`() {
        val zones = """"order": 1, "zones": [{ "id": "a", "from": [0, 0, 0], "to": [1, 1, 1] }]"""
        assertNull(LevelParser.parse("plain", "{ $zones }").level?.build)
        val full = LevelParser.parse("full", """{ $zones, "build": "garage:build" }""")
        assertEquals("garage:build", full.level?.build)
        assertEquals(emptyList(), full.problems)
        val broken = LevelParser.parse("broken", """{ $zones, "build": "Not A Function" }""")
        assertNull(assertNotNull(broken.level).build)
        assertEquals(1, broken.problems.size, broken.problems.toString())
    }

    @Test
    fun `entry is optional and its sound must be a sound id`() {
        val zones = """"order": 2, "zones": [{ "id": "a", "from": [0, 0, 0], "to": [1, 1, 1] }]"""
        assertNull(LevelParser.parse("plain", "{ $zones }").level?.entry)

        val full = LevelParser.parse(
            "full", """{ $zones, "entry": { "title": "B2", "subtitle": "Level 2", "sound": "every_tunnel:event.level_transition" } }""",
        )
        assertEquals(LevelEntry("B2", "Level 2", "every_tunnel:event.level_transition"), full.level?.entry)
        assertEquals(emptyList(), full.problems)

        val broken = LevelParser.parse("broken", """{ $zones, "entry": { "title": "B2", "sound": "Not A Sound", "music": 1 } }""")
        assertEquals(LevelEntry("B2", "", null), broken.level?.entry)
        assertEquals(2, broken.problems.size, broken.problems.toString())
    }
}
