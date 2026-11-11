package daio7771.everytunnel.core.death

/**
 * What the death screen says (`client.hud.DeathScreen`): what killed the player, and a tip, without humour.
 * Shown on the black of dying (`platform.DeathHooks`), in every level but Levels 1 and 4, which have no
 * enemies: there only thirst is named.
 *
 * Each cause has its text under `death.every_tunnel.<id>` (the name, after "Killed by") and
 * `death.every_tunnel.<id>.tip`; [OTHER] says only "You died".
 */
enum class DeathCause(val id: String) {
    PALE_MAN("pale_man"),
    OBSERVER("observer"),
    HUNTERS("hunters"),
    CLOUD("distortion_cloud"),
    THIRST("thirst"),
    FALL("fall"),
    DEEP_WATER("deep_water"),
    OTHER("other");

    companion object {
        /** Levels (by `order`) without enemies: only thirst is named there. */
        val QUIET_LEVELS = setOf(1, 4)

        private val KILLERS = mapOf(
            "every_tunnel:pale_man" to PALE_MAN,
            "every_tunnel:observer" to OBSERVER,
            "every_tunnel:hunter" to HUNTERS,
            "every_tunnel:distortion_cloud" to CLOUD,
        )

        /**
         * What to show for a death in the level of [levelOrder], dealt by an entity of type [killer] (its id,
         * or null) with damage of type [damage] (its id); null to show nothing.
         */
        fun of(levelOrder: Int, killer: String?, damage: String): DeathCause? {
            val cause = killer?.let(KILLERS::get) ?: when (damage) {
                "minecraft:dry_out" -> THIRST
                "minecraft:fall" -> FALL
                "minecraft:drown" -> DEEP_WATER
                else -> OTHER
            }
            return if (levelOrder in QUIET_LEVELS && cause != THIRST) null else cause
        }

        fun byId(id: String): DeathCause? = entries.firstOrNull { it.id == id }
    }
}
