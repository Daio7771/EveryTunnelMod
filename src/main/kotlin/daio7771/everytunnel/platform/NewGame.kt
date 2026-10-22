package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.world.GameMode

/**
 * Starts the game over for a player, in the same world (`/everytunnel newgame`):
 *
 * - The world: every shutter opens ([LevelEntryHooks.openAll]), all the loot is back
 *   ([LootSpawner.restock]) and every Pale man goes back to his spawn point (also the ones in unloaded chunks, when they load:
 *   [PaleManSpawner.startNewGame]).
 * - The player: empty inventory, full health and resources, Survival, and back at the
 *   start of the first level with an intro, which plays again ([IntroHooks.replay]); without
 *   one, at the start of the first level.
 */
object NewGame {

    /** The level the new game starts in, or null if the world has no level with a start. */
    fun start(player: ServerPlayerEntity): LevelDefinition? {
        val server = player.server
        LevelEntryHooks.openAll(server)
        LootSpawner.restock(server)
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

        val level = IntroHooks.replay(player) ?: firstLevelStart(player)
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
