package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Feeds player positions into [PlayerLocationTracker] every server tick and
 * forwards changes to [LevelTriggers]. Every change is also logged, which helps
 * when placing zones while building the map.
 */
object LevelTrackingHooks {

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerManager.playerList) update(player)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            PlayerLocationTracker.remove(handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { PlayerLocationTracker.clear() }
    }

    /** Dimension id of the player's world, e.g. `minecraft:overworld`. */
    fun dimensionOf(player: ServerPlayerEntity): String =
        player.serverWorld.registryKey.value.toString()

    private fun update(player: ServerPlayerEntity) {
        val change = PlayerLocationTracker.update(player.uuid, dimensionOf(player), player.x, player.y, player.z)
            ?: return
        val parts = mutableListOf<String>()
        if (change.levelChanged) parts += "level ${change.previous.levelId ?: "none"} -> ${change.current.levelId ?: "none"}"
        if (change.enteredZones.isNotEmpty()) parts += "entered ${change.enteredZones.joinToString { "${it.levelId}/${it.zoneId}" }}"
        if (change.exitedZones.isNotEmpty()) parts += "left ${change.exitedZones.joinToString { "${it.levelId}/${it.zoneId}" }}"
        EveryTunnel.LOGGER.info("{}: {}", player.name.string, parts.joinToString("; "))
        LevelTriggers.fire(change)
    }
}
