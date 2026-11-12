package daio7771.everytunnel.core.progress

/**
 * What one player has done in this game, kept through deaths and between sessions: the pieces
 * picked up (per level), the notes read, the scenes seen, Level 3's books and the current
 * checkpoint. Plain data, no Minecraft types: `platform.CardinalProgress` keeps one per player,
 * saves it and sends it to the client.
 */
class PlayerProgress {

    private val piecesByLevel = linkedMapOf<String, LinkedHashSet<String>>()
    private val notesRead = linkedSetOf<String>()
    private val scenesSeen = linkedSetOf<String>()

    /** Level 3's books this player has brought back. Never negative. */
    var level3Books: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** The id of the last checkpoint reached, or null before the first one. */
    var checkpoint: String? = null

    /** Level 4 as it was left, as text (`core.save.LoungeSave`), or null. */
    var lounge: String? = null

    /** Server ticks played and deaths in this game, summed over its sessions: the congratulations window's numbers. Never negative. */
    var playTicks: Long = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    var deaths: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** The pieces of each level, as a copy (level id -> piece ids). */
    val pieces: Map<String, Set<String>>
        get() = piecesByLevel.mapValues { (_, pieces) -> pieces.toSet() }

    val notes: Set<String>
        get() = notesRead.toSet()

    val scenes: Set<String>
        get() = scenesSeen.toSet()

    fun hasPiece(levelId: String, pieceId: String): Boolean = piecesByLevel[levelId]?.contains(pieceId) == true

    fun pieceCount(levelId: String): Int = piecesByLevel[levelId]?.size ?: 0

    /** True if the piece is new, false if the player already had it. */
    fun addPiece(levelId: String, pieceId: String): Boolean = piecesByLevel.getOrPut(levelId) { linkedSetOf() }.add(pieceId)

    fun hasReadNote(noteId: String): Boolean = noteId in notesRead

    /** True if it is the first time the note is read. */
    fun markNoteRead(noteId: String): Boolean = notesRead.add(noteId)

    fun hasSeenScene(sceneId: String): Boolean = sceneId in scenesSeen

    /** True if it is the first time the scene is seen. */
    fun markSceneSeen(sceneId: String): Boolean = scenesSeen.add(sceneId)

    /** A new game: everything forgotten. */
    fun clear() {
        piecesByLevel.clear()
        notesRead.clear()
        scenesSeen.clear()
        level3Books = 0
        checkpoint = null
        lounge = null
        playTicks = 0
        deaths = 0
    }

    /** One line for the debug command. */
    fun describe(): String {
        val pieceList = piecesByLevel.entries.joinToString("; ") { (level, pieces) -> "$level: ${pieces.joinToString(", ")}" }
        return "pieces [$pieceList], notes $notesRead, scenes $scenesSeen, level 3 books $level3Books, checkpoint ${checkpoint ?: "none"}, played ${playTicks / 20} s, deaths $deaths"
    }
}
