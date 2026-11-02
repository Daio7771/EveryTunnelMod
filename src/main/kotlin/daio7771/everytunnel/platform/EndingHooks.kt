package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * The end of the demo: walking into a zone tagged [END_TAG] (past Level 4's big door) while the
 * world rules apply. Not while loading
 * ([LoadingHooks.isLoading]): a game saved after the end opens with the player still in that
 * zone, until the new game takes them to the elevator.
 *
 * The screen fades to black, and the client shows "End of demo" with the credits
 * (`client.screen.EndOfDemoScreen`), then saves and goes back to the title screen. Opening
 * the world again starts a new game, as it always does ([LoadingHooks]).
 */
object EndingHooks {

    const val END_TAG = "end"

    /** To the client: show the end screen (no payload). */
    val ENDING_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ending")

    private const val FADE_SECONDS = 2.5f
    private const val SCREEN_TICK = 60

    private class Ending(var ticks: Int = 0)

    // Server thread only.
    private val endings = HashMap<UUID, Ending>()
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            endings.clear()
        }
        LevelTriggers.register { change ->
            val reachedEnd = change.enteredZones.any { key ->
                LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags?.contains(END_TAG) == true
            }
            if (!reachedEnd || change.playerId in endings) return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            if (!WorldRules.appliesTo(player.isCreative, player.isSpectator) || LoadingHooks.isLoading(player)) return@register
            endings[player.uuid] = Ending()
            EveryTunnel.LOGGER.info("{} reached the end of the demo", player.name.string)
            PlaytestLog.endOfDemo(player)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> endings.remove(handler.player.uuid) }
    }

    private fun tick(server: MinecraftServer) {
        val iterator = endings.entries.iterator()
        while (iterator.hasNext()) {
            val (id, ending) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null) {
                iterator.remove()
                continue
            }
            val t = ending.ticks++
            if (t == 0) DeathHooks.sendFade(player, 1f, FADE_SECONDS)
            if (t >= SCREEN_TICK) {
                iterator.remove()
                if (ServerPlayNetworking.canSend(player, ENDING_PACKET_ID)) {
                    ServerPlayNetworking.send(player, ENDING_PACKET_ID, PacketByteBufs.empty())
                }
            }
        }
    }
}
