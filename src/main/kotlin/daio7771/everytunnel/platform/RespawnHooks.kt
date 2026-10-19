package daio7771.everytunnel.platform

import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Players respawn at the start of the level they last entered, not at the world spawn
 * (which is outside, on top of the map).
 *
 * Entering a level sets the player's respawn point (like `/spawnpoint`, no bed needed)
 * to its first spawn point tagged [TAG]. Leaving the levels keeps the last one.
 */
object RespawnHooks {

    const val TAG = "player"

    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
        LevelTriggers.register { change ->
            val levelId = change.current.levelId
            if (!change.levelChanged || levelId == null) return@register
            val server = server ?: return@register
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            val level = LevelManager.get(levelId) ?: return@register
            val start = level.spawnPoints.firstOrNull { TAG in it.tags } ?: return@register
            val dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))
            val pos = BlockPos.ofFloored(start.position.x, start.position.y, start.position.z)
            player.setSpawnPoint(dimension, pos, start.yaw, true, false)
        }
    }
}
