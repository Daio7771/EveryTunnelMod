package daio7771.everytunnel.core.save

/**
 * A game in progress, as kept between sessions (`platform.SaveGameHooks` writes it with the
 * world): the furthest level reached and how far each level's puzzles got. The map is rebuilt
 * every time the world opens, which wipes the puzzles' blocks, so they are kept here and put
 * back after the rebuild. Plain data: no Minecraft types.
 *
 * Cells are block positions ([Cell]); lights, readers and loot spots are keyed by strings the
 * hooks that own them already use ("dimension|x|y|z", "level/point").
 */
class SavedGame {

    /** A block position. */
    data class Cell(val x: Int, val y: Int, val z: Int) {
        override fun toString(): String = "$x $y $z"

        companion object {
            /** "x y z" back into a cell, or null if it is not one. */
            fun parse(text: String): Cell? {
                val parts = text.trim().split(' ')
                if (parts.size != 3) return null
                val (x, y, z) = parts.map { it.toIntOrNull() ?: return null }
                return Cell(x, y, z)
            }
        }
    }

    /** A game is in progress (false before the first new game and after the end of the demo). */
    var active = false

    /** The furthest level reached in this game (its id and `order`), or null. */
    var levelId: String? = null
        private set
    var levelOrder = 0
        private set

    /** Level id -> its valve digits (A first), and how many turns each valve has now. */
    val valveCodes = mutableMapOf<String, List<Int>>()
    val valveTurns = mutableMapOf<String, List<Int>>()

    /** Level id -> where its boxes lie (a carried one: where it was picked up) and its shelf's filled slots. */
    val boxes = mutableMapOf<String, List<Cell>>()
    val shelfSlots = mutableMapOf<String, List<Int>>()

    /** Level id -> where its books lie (a carried one: where it was picked up) and the places on the counter with one. */
    val books = mutableMapOf<String, List<Cell>>()
    val returnedBooks = mutableMapOf<String, List<Cell>>()

    /** Lights the keycard put out ("dimension|x|y|z" -> their mode before), and card readers unlocked ("dimension|x|y|z"). */
    val lightsOut = linkedMapOf<String, String>()
    val readersUnlocked = linkedSetOf<String>()

    /** Loot spots ("level/point") picked up in this game. */
    val lootTaken = linkedSetOf<String>()

    /** Player UUID -> the achievements (ids) they earned in this game (each toast shows once a game). */
    val achievements = mutableMapOf<String, Set<String>>()

    /** A new game in [firstLevelId] (of [order]): everything from before forgotten. */
    fun start(firstLevelId: String, order: Int) {
        clear()
        active = true
        levelId = firstLevelId
        levelOrder = order
    }

    /** The game is over (the end of the demo): nothing to continue. */
    fun finish() {
        clear()
    }

    /** A player walked into [id] (of [order]): kept if it is further than any level before. True if it was. */
    fun reached(id: String, order: Int): Boolean {
        if (!active || order <= levelOrder) return false
        levelId = id
        levelOrder = order
        return true
    }

    /** For reading a save back: the furthest level as it was written. */
    fun restoreLevel(id: String?, order: Int) {
        levelId = id
        levelOrder = order
    }

    private fun clear() {
        active = false
        levelId = null
        levelOrder = 0
        valveCodes.clear()
        valveTurns.clear()
        boxes.clear()
        shelfSlots.clear()
        books.clear()
        returnedBooks.clear()
        lightsOut.clear()
        readersUnlocked.clear()
        lootTaken.clear()
        achievements.clear()
    }
}
