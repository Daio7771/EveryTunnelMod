package daio7771.everytunnel

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common (server + client) entrypoint for Every Tunnel.
 *
 * Phase 0: only logs that the mod has loaded. Gameplay systems
 * (see the `core`, `systems`, `entities`, `audio` and `world` packages)
 * are registered here in later phases.
 */
object EveryTunnel : ModInitializer {

    /** Mod id, must match `id` in fabric.mod.json. */
    const val MOD_ID: String = "every_tunnel"

    /** Human readable mod name. */
    const val MOD_NAME: String = "Every Tunnel"

    /** Shared logger for the whole mod. */
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_NAME)

    override fun onInitialize() {
        LOGGER.info("$MOD_NAME loaded")
    }
}
