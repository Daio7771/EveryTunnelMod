package daio7771.everytunnel.core.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedGameTest {

    @Test
    fun `only a further level is kept`() {
        val game = SavedGame()
        game.start("level_1", 1)
        assertTrue(game.reached("level_3", 3))
        assertFalse(game.reached("level_2", 2))
        assertFalse(game.reached("level_3", 3))
        assertEquals("level_3", game.levelId)
        assertEquals(3, game.levelOrder)
    }

    @Test
    fun `nothing is reached without a game, and the end forgets it all`() {
        val game = SavedGame()
        assertFalse(game.reached("level_2", 2))
        game.start("level_1", 1)
        game.boxes["level_3"] = listOf(SavedGame.Cell(1, 2, 3))
        game.lootTaken += "level_1/battery_1"
        game.finish()
        assertFalse(game.active)
        assertNull(game.levelId)
        assertTrue(game.boxes.isEmpty())
        assertTrue(game.lootTaken.isEmpty())
    }

    @Test
    fun `a new game starts from nothing`() {
        val game = SavedGame()
        game.start("level_1", 1)
        game.reached("level_2", 2)
        game.valveCodes["level_3"] = listOf(1, 2, 3, 4, 5)
        game.start("level_1", 1)
        assertEquals("level_1", game.levelId)
        assertTrue(game.valveCodes.isEmpty())
    }

    @Test
    fun `cells read back as written`() {
        val cell = SavedGame.Cell(-13, 18, 118)
        assertEquals(cell, SavedGame.Cell.parse(cell.toString()))
        assertNull(SavedGame.Cell.parse("1 2"))
        assertNull(SavedGame.Cell.parse("a b c"))
    }
}
