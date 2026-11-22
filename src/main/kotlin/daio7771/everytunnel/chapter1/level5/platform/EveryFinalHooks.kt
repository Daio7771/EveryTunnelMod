package daio7771.everytunnel.chapter1.level5.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.PlayerLocationTracker
import daio7771.everytunnel.common.platform.TickProfiler
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * The menu logo's EVERY FINAL easter egg, the server's half: it tells each player's client, whenever it changes, whether they are in
 * Level 5 and have not yet gone into the Director's office ([DirectorSceneHooks.sceneStarted]). A client told yes that then quits to
 * the menu makes the logo read EVERY FINAL until the game is closed (`chapter1.client.EveryFinalClient`, `MenuLogoOverride`). Server thread only.
 */
object EveryFinalHooks {

    /** To the client: a boolean, whether quitting now would make the logo read EVERY FINAL. */
    val ARMED_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "every_final_armed")

    private const val CHECK_EVERY = 20

    /** What each player's client was last told. */
    private val told = HashMap<UUID, Boolean>()

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> told.remove(handler.player.uuid) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_EVERY != 0) return
        for (player in server.playerManager.playerList) {
            val inLevel5 = PlayerLocationTracker.locationOf(player.uuid).zones.any { it.levelId == DirectorSceneHooks.LEVEL_ID }
            val armed = inLevel5 && !DirectorSceneHooks.sceneStarted
            if (told[player.uuid] == armed || (told[player.uuid] == null && !armed)) {
                told[player.uuid] = armed
                continue
            }
            if (!ServerPlayNetworking.canSend(player, ARMED_PACKET_ID)) continue
            told[player.uuid] = armed
            val buf = PacketByteBufs.create()
            buf.writeBoolean(armed)
            ServerPlayNetworking.send(player, ARMED_PACKET_ID, buf)
        }
    }
}
