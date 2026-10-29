package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.world.GameMode

/**
 * Starts the game over for a player, in the same world: when the world opens with no game
 * saved ([LoadingHooks], [SaveGameHooks]; NEW GAME in the main menu deletes the save) and with
 * `/everytunnel newgame`. The save starts over from here ([SaveGameHooks.started]).
 *
 * - The world: everyone out of the lockers ([HidingHooks]), nothing carried any more
 *   ([CarryHooks]), Level 3's boxes put out at random and their shelf emptied ([BoxHooks]), its
 *   books off the cabinets and the counter and its hatch shut ([BookHooks]), every shutter opens ([LevelEntryHooks.openAll]), all the loot is back
 *   ([LootSpawner.restock]), the lights the keycard put out are back and the card readers
 *   locked ([KeycardHooks]), Level 3's valves back on 0 with new digits and their door shut
 *   ([ValveHooks]), and every Pale man goes back to his spawn point (also the ones in unloaded chunks, when they load:
 *   [PaleManSpawner.startNewGame]).
 * - The player: every achievement can be earned again ([AchievementHooks]), empty inventory, full health and resources, Survival, and back at the
 *   start of the first level with an intro, which plays again ([IntroHooks.replay]); without
 *   one, at the start of the first level.
 */
object NewGame {

    /**
     * The levels' blocks were just rebuilt (`/everytunnel rebuilt`, run by the map's
     * `garage:build`): the rebuild wiped the loot and put the lights back, so the world's
     * memory of them starts over. The loot is put back at once, the lights the keycard put
     * out are forgotten (they are on again), and shutters closing or waiting are dropped.
     * Players are not touched.
     */
    fun levelsRebuilt(server: MinecraftServer) {
        LevelEntryHooks.openAll(server)
        KeycardHooks.forgetLights(server)
        ValveHooks.levelsRebuilt()
        CarryHooks.forgetAll()
        LootSpawner.restock(server)
        CollapseHooks.resetAll(server)
        PaleManSpawner.startNewGame(server)
        LoadingHooks.levelsRebuilt(server)
        EveryTunnel.LOGGER.info("Levels rebuilt: loot back, keycard lights forgotten")
    }

    /** The level the new game starts in, or null if the world has no level with a start. */
    fun start(player: ServerPlayerEntity): LevelDefinition? {
        val server = player.server
        HidingHooks.newGame(server)
        AchievementHooks.newGame(player)
        CarryHooks.forgetAll()
        BoxHooks.newGame(server)
        BookHooks.newGame(server)
        CollapseHooks.resetAll(server)
        LevelEntryHooks.openAll(server)
        LootSpawner.restock(server)
        KeycardHooks.restoreLights(server)
        KeycardHooks.resetReaders(server)
        ValveHooks.newGame(server)
        PaleManSpawner.startNewGame(server)

        player.inventory.clear()
        player.clearStatusEffects()
        player.extinguish()
        player.fallDistance = 0f
        player.health = player.maxHealth
        player.hungerManager.foodLevel = 20
        player.hungerManager.saturationLevel = 5f
        ResourceSystem.reset(player.uuid)
        player.changeGameMode(GameMode.SURVIVAL)

        PlaytestLog.newGame(player)
        val level = IntroHooks.replay(player) ?: firstLevelStart(player)
        SaveGameHooks.started(server, level)
        EveryTunnel.LOGGER.info("{}: new game in {}", player.name.string, level?.id)
        return level
    }

    private fun firstLevelStart(player: ServerPlayerEntity): LevelDefinition? {
        for (level in LevelManager.all().sortedBy { it.order }) {
            val start = level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags } ?: continue
            val world = player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: continue
            player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
            return level
        }
        return null
    }
}
