package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.level.Ambience
import daio7771.everytunnel.core.level.RandomSound
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Sends each player the ambience of the level they are in (see `core.level.Ambience`):
 * when they change level (including joining) and after `/reload`, since that can change
 * the level files. Outside every level they get [Ambience.NONE]. The client plays it
 * (`client.effects.AmbiencePlayer`), because looping and random positioned sounds are
 * per player and need no server work.
 */
object AmbienceHooks {

    val PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ambience")

    @Volatile
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
        LevelTriggers.register { change ->
            if (change.levelChanged) {
                server?.playerManager?.getPlayer(change.playerId)?.let { send(it, change.current.levelId) }
            }
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) {
                for (player in server.playerManager.playerList) {
                    send(player, PlayerLocationTracker.locationOf(player.uuid).levelId)
                }
            }
        }
    }

    private fun send(player: ServerPlayerEntity, levelId: String?) {
        if (!ServerPlayNetworking.canSend(player, PACKET_ID)) return
        val ambience = levelId?.let { LevelManager.get(it)?.ambience } ?: Ambience.NONE
        val buf = PacketByteBufs.create()
        write(buf, ambience)
        ServerPlayNetworking.send(player, PACKET_ID, buf)
    }

    fun write(buf: PacketByteBuf, ambience: Ambience) {
        buf.writeString(ambience.loop ?: "")
        buf.writeDouble(ambience.loopVolume)
        buf.writeVarInt(ambience.randomSounds.size)
        for (sound in ambience.randomSounds) {
            buf.writeString(sound.sound)
            buf.writeDouble(sound.minInterval)
            buf.writeDouble(sound.maxInterval)
            buf.writeDouble(sound.minDistance)
            buf.writeDouble(sound.maxDistance)
            buf.writeDouble(sound.volume)
        }
    }

    fun read(buf: PacketByteBuf): Ambience {
        val loop = buf.readString().ifEmpty { null }
        val loopVolume = buf.readDouble()
        val sounds = List(buf.readVarInt()) {
            RandomSound(buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
        }
        return Ambience(loop, loopVolume, sounds)
    }
}
