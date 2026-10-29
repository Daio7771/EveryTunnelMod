package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Reading a note in a level, pinned on a board ([NoteBoardBlock]) or a loose sheet
 * ([NotePaperBlock]): the note is the level's spawn point tagged [NOTE_TAG] at that block, and
 * its id names the text (`assets/every_tunnel/notes/<lang>/<id>.txt`).
 * The server sends the id and the values for the text's placeholders (the valve digits of this
 * game, [ValveHooks.values]); the client shows it on paper (`client.screen.NoteScreen`).
 */
object NoteHooks {

    const val NOTE_TAG = "note"

    /** To the client: a note to show. Payload: id, then the values (count, then name and value each). */
    val NOTE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "note")

    fun read(player: ServerPlayerEntity, pos: BlockPos) {
        val level = LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) ?: return
        val note = level.spawnPoints.firstOrNull {
            NOTE_TAG in it.tags && BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) == pos
        } ?: return
        val values = ValveHooks.values(level)
        val buf = PacketByteBufs.create()
        buf.writeString(note.id)
        buf.writeVarInt(values.size)
        for ((name, value) in values) {
            buf.writeString(name)
            buf.writeVarInt(value)
        }
        if (ServerPlayNetworking.canSend(player, NOTE_PACKET_ID)) ServerPlayNetworking.send(player, NOTE_PACKET_ID, buf)
    }
}
