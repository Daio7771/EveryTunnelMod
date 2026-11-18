package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `level.json`'s Hunters, loot and notes: as the jar has them, and how the parser takes a bad file. */
class Ch2L2PlacementsTest {

    private val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())
    private val level = data.level

    private fun parse(json: String) = Ch2L2Level.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `the jar's file puts one group of Hunters in the galleries and the survival kit at the entry`() {
        assertEquals(emptyList(), data.problems)
        assertEquals(listOf("hunters_post"), level.hunters.map { it.anchor })
        assertEquals(Ch2L2State.PUMP_ROOM, level.hunters.single().until)
        val kit = level.loot.filter { it.anchor == "survival_kit" }
        assertEquals(3, kit.count { it.item == "battery" })
        assertEquals(2, kit.count { it.item == "water" })
        assertEquals(4, kit.count { it.item == "food_snack" })
        assertEquals(1, kit.count { it.item == "wrench" })
        assertEquals(kit.size, kit.map { Triple(it.dx, it.dy, it.dz) }.toSet().size, "two pieces of the kit in one place")
    }

    @Test
    fun `the later stretches carry the plan's loot and the three documents are on boards`() {
        fun count(anchor: String, item: String) = level.loot.count { it.anchor == anchor && it.item == item }
        assertEquals(1, count("loot_galleries", "water"))
        assertEquals(1, count("loot_galleries", "battery"))
        assertEquals(0, count("loot_galleries", "food_snack"))
        for (item in listOf("water", "food_snack", "battery")) assertEquals(1, count("loot_catacombs", item), item)
        assertEquals(1, count("loot_station", "water"))
        assertEquals(1, count("loot_station", "battery"))
        assertEquals(listOf("ch2_l2_old_drainage", "ch2_l2_maintenance_observation", "ch2_l2_withdrawal_order"), level.notes.map { it.noteId })
        assertEquals(level.loot.size, level.loot.map { it.id }.toSet().size)
    }

    @Test
    fun `a piece of loot is taken by its own counter, a note read by another`() {
        assertEquals("loot.kit_wrench", level.loot.first { it.item == "wrench" }.takenKey)
        assertEquals("read.old_drainage", level.notes.first().readKey)
    }

    @Test
    fun `a bad file is reported, its entries dropped`() {
        val result = parse(
            """{"hunters": [{"id": "a", "anchor": "x", "until": "nowhere"}, {"id": "b", "anchor": "x"}],
                "loot": [{"id": "l", "item": "battery", "anchor": "x", "offset": [1, 2]}, {"id": "m", "item": "water", "anchor": "x"},
                         {"id": "m", "item": "water", "anchor": "y"}],
                "notes": [{"id": "n"}]}""",
        )
        assertEquals(listOf("b"), result.level.hunters.map { it.id })
        assertEquals(listOf("m", "m"), result.level.loot.map { it.id })
        assertTrue(result.problems.any { "until" in it }, result.problems.toString())
        assertTrue(result.problems.any { "offset" in it }, result.problems.toString())
        assertTrue(result.problems.any { "two have the id \"m\"" in it }, result.problems.toString())
        assertTrue(result.problems.any { "notes[0].anchor" in it }, result.problems.toString())
        assertEquals(setOf("x", "y"), result.level.anchorNames().toSet() - "sealed_entry" - Ch2L2Creature.DEFAULT.anchorNames().toSet() - Ch2L2Guard.DEFAULT.anchorNames().toSet())
    }
}
