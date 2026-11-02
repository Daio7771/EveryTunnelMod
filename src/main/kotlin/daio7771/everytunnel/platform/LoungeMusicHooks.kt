package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.audio.LoungeMix
import daio7771.everytunnel.core.level.LevelDefinition
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Level 4's jazz: what each player should hear ([LoungeMix], by the zones they stand in), told to their
 * client when it changes (`client.effects.LoungeMusic`). It comes out of the ceiling speakers, the level's
 * spawn points tagged [SPEAKER_TAG]. It stops for the Cinema's film, and bends in the blackout.
 */
object LoungeMusicHooks {

    const val SPEAKER_TAG = "speaker"

    /** To the client: main and far volume, whether the lights are out, then the speakers (count, x, y, z each). */
    val PACKET_ID = Identifier(EveryTunnel.MOD_ID, "lounge_music")

    private const val LEVEL_ID = "level_4"
    private const val CHECK_TICKS = 5

    private data class Sent(val mix: LoungeMix, val blackout: Boolean)

    private val sent = HashMap<UUID, Sent>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> if (server.ticks % CHECK_TICKS == 0) check(server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> sent.remove(handler.player.uuid) }
    }

    private fun check(server: MinecraftServer) {
        val level = LevelManager.get(LEVEL_ID) ?: return
        for (player in server.playerManager.playerList) {
            val next = Sent(mixFor(player, level), LoungeClosingHooks.blackout)
            if (sent[player.uuid] == next || (next.mix == LoungeMix.SILENT && player.uuid !in sent)) continue
            sent[player.uuid] = next
            send(player, level, next)
        }
    }

    private fun mixFor(player: ServerPlayerEntity, level: LevelDefinition): LoungeMix {
        if (LoungeHooks.notBuiltYet(level) || LoadingHooks.isLoading(player)) return LoungeMix.SILENT
        val dimension = player.serverWorld.registryKey.value.toString()
        val zones = LevelManager.zonesAt(dimension, player.x, player.y, player.z).filter { it.level.id == LEVEL_ID }.map { it.zone.id }.toSet()
        return LoungeMix.of(zones, LoungePuzzleHooks.filmRunning)
    }

    private fun send(player: ServerPlayerEntity, level: LevelDefinition, state: Sent) {
        if (!ServerPlayNetworking.canSend(player, PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeFloat(state.mix.main)
        buf.writeFloat(state.mix.far)
        buf.writeBoolean(state.blackout)
        val speakers = level.spawnPoints.filter { SPEAKER_TAG in it.tags }
        buf.writeVarInt(speakers.size)
        for (speaker in speakers) {
            buf.writeDouble(speaker.position.x)
            buf.writeDouble(speaker.position.y)
            buf.writeDouble(speaker.position.z)
        }
        ServerPlayNetworking.send(player, PACKET_ID, buf)
    }
}
