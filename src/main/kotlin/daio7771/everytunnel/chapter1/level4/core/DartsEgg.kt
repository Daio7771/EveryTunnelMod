package daio7771.everytunnel.chapter1.level4.core

/**
 * Level 4's darts easter egg (`chapter1.level4.platform.DartHooks`): a player who has taken the other three pieces of
 * the master card (bar, cinema, spa) and opened every safe of the Suites, and only then comes back to the board,
 * finds the numbers to throw are [SEQUENCE], the last three guards, instead of the three drawn for the game.
 * Plain Kotlin, unit tested.
 */
object DartsEgg {

    /** The numbers asked for, in order. */
    val SEQUENCE = listOf(12, 13, 14)

    /** The other pieces of the master card (bar, cinema, spa) that must be taken. */
    const val OTHER_PIECES = 3

    /** [piecesTaken] of the other pieces, [safesOpen] of [safesTotal] safes. A level without safes never qualifies. */
    fun applies(piecesTaken: Int, safesOpen: Int, safesTotal: Int): Boolean =
        piecesTaken >= OTHER_PIECES && safesTotal > 0 && safesOpen >= safesTotal
}
