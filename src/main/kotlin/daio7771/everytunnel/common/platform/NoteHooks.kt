package daio7771.everytunnel.common.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.LevelManager
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import daio7771.everytunnel.common.core.level.LevelDefinition

/**
 * Reading a note in a level, pinned on a board ([NoteBoardBlock]) or a loose sheet
 * ([NotePaperBlock]): the note is the level's spawn point tagged [NOTE_TAG] at that block, and
 * its id names the text (`assets/every_tunnel/notes/<lang>/<id>.txt`).
 * The server sends the id and the values for the text's placeholders (the valve digits of this
 * game, [ValveHooks.values]); the client shows it on paper (`common.client.screen.NoteScreen`).
 *
 * A level that is not a [LevelDefinition] (a chapter's) names its boards' notes through a [Source]: asked, when no spawn point
 * of a level holds the block, which note the board at a position is (and so the place to remember that it was read).
 */
object NoteHooks {

    const val NOTE_TAG = "note"

    /** To the client: a note to show. Payload: id, then the values (count, then name and value each). */
    val NOTE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "note")

    /** A chapter's way to name the note a player reads on a board: the note's id (its text file), or null if the board is not one of its own. */
    fun interface Source {
        fun noteAt(player: ServerPlayerEntity, pos: BlockPos): String?
    }

    private val sources = java.util.concurrent.CopyOnWriteArrayList<Source>()

    fun addSource(source: Source) {
        sources += source
    }

    /** Which text a level's note shows: a chapter may change it by what the player has done (the id of the text file, from the note's). */
    private val shown = java.util.concurrent.CopyOnWriteArrayList<(String) -> String>()

    fun addShownRule(rule: (String) -> String) {
        shown += rule
    }

    /** The numbers a level's notes tell (a puzzle's digits: name and value), by chapter. */
    private val values = java.util.concurrent.CopyOnWriteArrayList<(LevelDefinition) -> Map<String, Int>>()

    fun addValues(source: (LevelDefinition) -> Map<String, Int>) {
        values += source
    }

    fun read(player: ServerPlayerEntity, pos: BlockPos) {
        val level = LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val note = level?.spawnPoints?.firstOrNull {
            NOTE_TAG in it.tags && BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) == pos
        }
        if (level == null || note == null) {
            val id = sources.firstNotNullOfOrNull { it.noteAt(player, pos) } ?: return
            send(player, id, emptyMap())
            return
        }
        ProgressService.get().markNoteRead(player, note.id)
        send(player, shown.fold(note.id) { id, rule -> rule(id) }, values.fold(emptyMap()) { all, source -> all + source(level) })
    }

    private fun send(player: ServerPlayerEntity, id: String, values: Map<String, Int>) {
        val buf = PacketByteBufs.create()
        buf.writeString(id)
        buf.writeVarInt(values.size)
        for ((name, value) in values) {
            buf.writeString(name)
            buf.writeVarInt(value)
        }
        if (ServerPlayNetworking.canSend(player, NOTE_PACKET_ID)) ServerPlayNetworking.send(player, NOTE_PACKET_ID, buf)
    }
}
