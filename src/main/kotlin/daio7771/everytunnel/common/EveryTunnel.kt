package daio7771.everytunnel.common

import daio7771.everytunnel.common.config.ConfigManager
import daio7771.everytunnel.common.platform.AchievementHooks
import daio7771.everytunnel.common.platform.AmbienceHooks
import daio7771.everytunnel.common.platform.CollapseHooks
import daio7771.everytunnel.common.platform.ContinueGame
import daio7771.everytunnel.common.platform.DeathHooks
import daio7771.everytunnel.common.platform.DebugCommands
import daio7771.everytunnel.common.platform.DevWorlds
import daio7771.everytunnel.common.platform.DistortionHooks
import daio7771.everytunnel.common.platform.FlashlightHooks
import daio7771.everytunnel.common.platform.IntroHooks
import daio7771.everytunnel.common.platform.KeptEntities
import daio7771.everytunnel.common.platform.LevelEntryHooks
import daio7771.everytunnel.common.platform.LevelReloadListener
import daio7771.everytunnel.common.platform.LevelTrackingHooks
import daio7771.everytunnel.common.platform.LoadingHooks
import daio7771.everytunnel.common.platform.LootSpawner
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.ModEntities
import daio7771.everytunnel.common.platform.ModItems
import daio7771.everytunnel.common.platform.ModSounds
import daio7771.everytunnel.common.platform.NoiseHooks
import daio7771.everytunnel.common.platform.PlaytestLog
import daio7771.everytunnel.common.platform.ResourceHooks
import daio7771.everytunnel.common.platform.RespawnHooks
import daio7771.everytunnel.common.platform.SafeLevelHooks
import daio7771.everytunnel.common.platform.TickProfiler
import daio7771.everytunnel.common.platform.WorldRulesHooks
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common (server + client) entrypoint for Every Tunnel.
 *
 * Loads the gameplay config and registers the items (battery), blocks (fluorescent
 * light, props), entities (the Pale man, and his spawning at level spawn points), sounds, the level data loader, the world rules, player level/zone tracking,
 * level ambience, stamina, thirst and the flashlight battery, player noise (what
 * enemies can hear), opening a world (rebuild and new game behind the loading screen), the
 * debug commands and the playtest log.
 * Later gameplay systems (`systems`, `entities`, `audio`) are registered here too. This is Chapter 1 and the base game every
 * chapter shares; a later chapter has its own entrypoint, after this one in
 * `fabric.mod.json`, and is never named here (`ChapterBoundaryTest`).
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
        TickProfiler.register()
        ModItems.register()
        ModBlocks.register()
        ModEntities.register()
        ModSounds.register()
        LevelReloadListener.register()
        WorldRulesHooks.register()
        LevelTrackingHooks.register()
        AmbienceHooks.register()
        ResourceHooks.register()
        FlashlightHooks.register()
        NoiseHooks.register()
        LootSpawner.register()
        CollapseHooks.register()
        DistortionHooks.register()
        KeptEntities.register()
        ContinueGame.register()
        SafeLevelHooks.register()
        AchievementHooks.register()
        RespawnHooks.register()
        DeathHooks.register()
        LevelEntryHooks.register()
        IntroHooks.register()
        LoadingHooks.register()
        DebugCommands.register()
        DevWorlds.register()
        PlaytestLog.register()
        LOGGER.info("$MOD_NAME loaded")
    }
}
