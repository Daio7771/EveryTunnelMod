package daio7771.everytunnel

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.platform.LevelReloadListener
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common (server + client) entrypoint for Every Tunnel.
 *
 * Loads the gameplay config and registers the level data loader. Gameplay
 * systems (`systems`, `entities`, `audio`, `world`) are registered here in
 * later phases.
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
        ConfigManager.load(FabricLoader.getInstance().configDir)
        LevelReloadListener.register()
        LOGGER.info("$MOD_NAME loaded")
    }
}
