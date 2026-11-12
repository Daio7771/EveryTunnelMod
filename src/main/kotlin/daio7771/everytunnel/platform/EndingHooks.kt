package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * The end of the demo: walking into a zone tagged [END_TAG] (past Level 4's big door) while the
 * world rules apply. Not while loading
 * ([LoadingHooks.isLoading]): a game saved after the end opens with the player still in that
 * zone, until the new game takes them to the elevator.
 *
 * The screen fades to black, and the client rolls the final credits
 * (`client.screen.FinalCreditsScreen`), then saves and goes back to the title screen. Opening
 * the world again starts a new game, as it always does ([LoadingHooks]). No level has such a zone
 * now: the game ends down Level 5's false exit ([theEnd], from [FalseExitHooks]).
 */
object EndingHooks {

    const val END_TAG = "end"

    /** To the client: show the end screen. Payload: the time played in ticks (long) and the deaths (var int) of the game. */
    val ENDING_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ending")

    /** To the client, from `/everytunnel congrats`: open the congratulations window at once, with the same payload. */
    val CONGRATS_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "congrats")

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
            ContinueGame.finished(player)
            EveryTunnel.LOGGER.info("{} reached the end of the demo", player.name.string)
            PlaytestLog.endOfDemo(player)
        }
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> endings.remove(handler.player.uuid) }
    }

    /**
     * The end of the game (Level 5's fall, [FalseExitHooks]), the screen already black: the saved game is erased and
     * the client rolls the final credits at once.
     */
    fun theEnd(player: ServerPlayerEntity) {
        ContinueGame.finished(player)
        EveryTunnel.LOGGER.info("{} reached the end", player.name.string)
        PlaytestLog.endOfDemo(player)
        send(player, ENDING_PACKET_ID)
    }

    /** The congratulations window with this game's numbers so far, without the ending (development). */
    fun previewCongrats(player: ServerPlayerEntity) = send(player, CONGRATS_PACKET_ID)

    private fun send(player: ServerPlayerEntity, id: Identifier) {
        if (!ServerPlayNetworking.canSend(player, id)) return
        val summary = ProgressService.get().runSummary(player)
        val buf = PacketByteBufs.create()
        buf.writeLong(summary.playTicks)
        buf.writeVarInt(summary.deaths)
        ServerPlayNetworking.send(player, id, buf)
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
                send(player, ENDING_PACKET_ID)
            }
        }
    }
}
