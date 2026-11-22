package daio7771.everytunnel.chapter2.level2.core

/**
 * Watches the players for the easter egg of [Ch2L2NoSupplies]: a player seen in the kit's room and then out of it, short of supplies,
 * fires it, once until [reset] (the level starting again for them: a death, CONTINUE). [K] identifies a player. Plain Kotlin, unit tested.
 */
class Ch2L2SupplyWatch<K>(private val rules: Ch2L2NoSupplies, private val lootIds: List<String>) {

    private val inside = HashSet<K>()
    private val fired = HashSet<K>()

    /** The level starts again for [player]: they are no longer seen in the room, and the egg can fire again. */
    fun reset(player: K) {
        inside -= player
        fired -= player
    }

    /** Where [player] is now ([inKit]) and what they have taken ([taken], by loot id). True if the Hunters are to be put out now. */
    fun update(player: K, inKit: Boolean, taken: (String) -> Boolean): Boolean {
        if (inKit) {
            inside += player
            return false
        }
        if (!inside.remove(player) || player in fired) return false
        if (!rules.missing(lootIds, taken)) return false
        fired += player
        return true
    }
}
