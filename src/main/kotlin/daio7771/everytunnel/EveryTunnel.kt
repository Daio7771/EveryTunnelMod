package daio7771.everytunnel

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.platform.AmbienceHooks
import daio7771.everytunnel.platform.DebugCommands
import daio7771.everytunnel.platform.FlashlightHooks
import daio7771.everytunnel.platform.LevelReloadListener
import daio7771.everytunnel.platform.LevelTrackingHooks
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModItems
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.ResourceHooks
import daio7771.everytunnel.platform.WorldRulesHooks
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common (server + client) entrypoint for Every Tunnel.
 *
 * Loads the gameplay config and registers the items (battery), blocks (fluorescent
 * light), sounds, the level data loader, the world rules, player level/zone tracking,
 * level ambience, stamina, thirst and the flashlight battery, and the debug commands.
 * Later gameplay systems (`systems`, `entities`, `audio`) are registered here too.
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
        ModItems.register()
        ModBlocks.register()
        ModSounds.register()
        LevelReloadListener.register()
        WorldRulesHooks.register()
        LevelTrackingHooks.register()
        AmbienceHooks.register()
        ResourceHooks.register()
        FlashlightHooks.register()
        DebugCommands.register()
        LOGGER.info("$MOD_NAME loaded")
    }
}
