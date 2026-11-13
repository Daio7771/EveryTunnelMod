package daio7771.everytunnel.core.progress

/**
 * What a player no longer needs once they reach a level, and so does not keep (plain Kotlin, unit tested):
 * an item that opened something in an earlier level and opens nothing from here on. `platform.ContinueGame`
 * takes these out of the inventory when a level's start is reached, before the inventory is saved for the
 * level's checkpoint (so a death or CONTINUE does not bring them back), and again when a saved game is
 * continued (a game saved before they were taken). Silently: nothing is said to the player.
 *
 * Batteries, water and food are never here: they are of use in every level. A level's own puzzle things that the
 * puzzle already takes (the records and reels, the Master Key) need no entry either.
 *
 * Items are by id without the namespace (`every_tunnel:keycard` is `keycard`), and [AFTER] is by level order.
 */
object LeftBehind {

    /** Items that are useless from the level of this order on. */
    private val AFTER: Map<Int, Set<String>> = mapOf(
        // The Employee of the Month's keycard opens Level 2's exit and nothing else.
        3 to setOf("keycard"),
        // The four corners of Level 4's golden master card, and the card they make: spent at its reader, which the
        // Master Key follows.
        5 to setOf("master_card_nw", "master_card_ne", "master_card_sw", "master_card_se", "master_card"),
    )

    /** The ids of the items that are useless from the level of order [order] on (also those of earlier levels). */
    fun uselessFrom(order: Int): Set<String> = AFTER.filterKeys { it <= order }.values.flatten().toSet()
}
