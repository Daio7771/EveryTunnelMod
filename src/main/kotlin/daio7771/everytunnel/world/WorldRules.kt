package daio7771.everytunnel.world

import daio7771.everytunnel.core.LevelManager

/**
 * The map's world rules: no breaking or placing blocks, no vanilla mob spawning.
 *
 * They are active only in worlds whose datapacks define Every Tunnel levels, so
 * other worlds stay vanilla. Creative and Spectator players are exempt, so the map
 * can be built and edited in Creative. Enforcement lives in `platform.WorldRulesHooks`.
 */
object WorldRules {

    /** True while the loaded world defines at least one level. */
    val active: Boolean
        get() = LevelManager.all().isNotEmpty()

    /** True if a player in this game mode may not break or place blocks right now. */
    fun blocksRestrictedFor(isCreative: Boolean, isSpectator: Boolean): Boolean =
        active && !isCreative && !isSpectator
}
