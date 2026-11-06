package daio7771.everytunnel.core.door

/**
 * A sliding door of two leaves meeting in the middle, each half of its columns wide, sliding into the walls
 * at its two ends a quarter of a block at a time (Level 5's airlock: `platform.LabHooks`). Columns are
 * counted along the door from one end; a leaf's column nearest its wall is its outer one.
 */
object SlidingDoor {

    /** Quarters in a block. */
    const val QUARTERS = 4

    /** Quarter steps each leaf travels from shut to open: across its own columns. */
    fun steps(columns: Int): Int = columns / 2 * QUARTERS

    /**
     * How many quarters of each column, in order along the door, a door [open] quarters open still fills
     * (0 to [QUARTERS]; 0 is an empty column). The rest of each leaf is in its wall.
     */
    fun widths(columns: Int, open: Int): List<Int> = List(columns) { i ->
        val fromWall = minOf(i, columns - 1 - i)
        (steps(columns) - open - fromWall * QUARTERS).coerceIn(0, QUARTERS)
    }
}
