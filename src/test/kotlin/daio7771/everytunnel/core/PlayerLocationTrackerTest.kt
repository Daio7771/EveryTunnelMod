package daio7771.everytunnel.core

import daio7771.everytunnel.core.level.LevelParser
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerLocationTrackerTest {

    private val player = UUID.randomUUID()
    private val overworld = "minecraft:overworld"

    @BeforeTest
    fun setUp() {
        val level1 = LevelParser.parse(
            "level_1",
            """{ "order": 1, "zones": [
                 { "id": "garage", "from": [0, 0, 0], "to": [31, 9, 31] },
                 { "id": "ramp_down", "from": [28, 0, 28], "to": [31, 3, 31], "tags": ["exit"] } ] }""",
        ).level!!
        val level2 = LevelParser.parse(
            "level_2",
            """{ "order": 2, "zones": [ { "id": "garage", "from": [32, 0, 0], "to": [63, 9, 31] } ] }""",
        ).level!!
        LevelManager.replaceAll(listOf(level1, level2))
        PlayerLocationTracker.clear()
    }

    @AfterTest
    fun tearDown() {
        LevelManager.replaceAll(emptyList())
        PlayerLocationTracker.clear()
    }

    @Test
    fun `entering, moving inside and leaving levels and overlapping zones`() {
        val enter = PlayerLocationTracker.update(player, overworld, 5.0, 1.0, 5.0)!!
        assertTrue(enter.levelChanged)
        assertEquals(setOf(ZoneKey("level_1", "garage")), enter.enteredZones)

        assertNull(PlayerLocationTracker.update(player, overworld, 6.0, 1.0, 6.0), "moving inside the same zone is not a change")

        val ramp = PlayerLocationTracker.update(player, overworld, 29.5, 1.0, 29.5)!!
        assertEquals(false, ramp.levelChanged)
        assertEquals(setOf(ZoneKey("level_1", "ramp_down")), ramp.enteredZones)
        assertEquals(2, PlayerLocationTracker.locationOf(player).zones.size)

        val next = PlayerLocationTracker.update(player, overworld, 40.0, 1.0, 5.0)!!
        assertEquals("level_2", next.current.levelId)
        assertEquals(setOf(ZoneKey("level_1", "garage"), ZoneKey("level_1", "ramp_down")), next.exitedZones)

        val outside = PlayerLocationTracker.update(player, overworld, 100.0, 1.0, 5.0)!!
        assertNull(outside.current.levelId)
        assertEquals(PlayerLocation.NOWHERE, PlayerLocationTracker.locationOf(player))
    }

    @Test
    fun `other dimensions are outside every level`() {
        assertNull(PlayerLocationTracker.update(player, "minecraft:the_nether", 5.0, 1.0, 5.0))
        assertEquals(PlayerLocation.NOWHERE, PlayerLocationTracker.locationOf(player))
    }
}
