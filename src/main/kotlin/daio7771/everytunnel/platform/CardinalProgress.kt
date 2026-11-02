package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.progress.PlayerProgress
import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * [ProgressService] on Cardinal Components, the only class that touches that library. Each
 * player carries a [ProgressComponent] holding a [PlayerProgress]:
 *
 * - **Saved** with the player's own data in the world.
 * - **Death:** the component is copied onto the respawned player ([RespawnCopyStrategy.ALWAYS_COPY]).
 * - **Client:** every change is sent to that player alone. Cardinal Components also sends it on
 *   joining, respawning and changing dimension.
 *
 * The component is declared in `fabric.mod.json` (`custom`, "cardinal-components"), and this
 * object is its `cardinal-components-entity` entrypoint.
 */
object CardinalProgress : ProgressService, EntityComponentInitializer {

    private val KEY: ComponentKey<ProgressComponent> =
        ComponentRegistry.getOrCreate(Identifier(EveryTunnel.MOD_ID, "progress"), ProgressComponent::class.java)

    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        registry.registerForPlayers(KEY, { player: PlayerEntity -> ProgressComponent(player) }, RespawnCopyStrategy.ALWAYS_COPY)
    }

    override fun hasPiece(player: PlayerEntity, levelId: String, pieceId: String) = of(player).hasPiece(levelId, pieceId)

    override fun pieceCount(player: PlayerEntity, levelId: String) = of(player).pieceCount(levelId)

    override fun addPiece(player: PlayerEntity, levelId: String, pieceId: String) =
        change(player) { it.addPiece(levelId, pieceId) }

    override fun hasReadNote(player: PlayerEntity, noteId: String) = of(player).hasReadNote(noteId)

    override fun markNoteRead(player: PlayerEntity, noteId: String) = change(player) { it.markNoteRead(noteId) }

    override fun hasSeenScene(player: PlayerEntity, sceneId: String) = of(player).hasSeenScene(sceneId)

    override fun markSceneSeen(player: PlayerEntity, sceneId: String) = change(player) { it.markSceneSeen(sceneId) }

    override fun level3Books(player: PlayerEntity) = of(player).level3Books

    override fun setLevel3Books(player: PlayerEntity, books: Int) = change(player) { it.level3Books = books }

    override fun checkpoint(player: PlayerEntity) = of(player).checkpoint

    override fun setCheckpoint(player: PlayerEntity, checkpointId: String?) = change(player) { it.checkpoint = checkpointId }

    override fun reset(player: PlayerEntity) = change(player) { it.clear() }

    override fun describe(player: PlayerEntity) = of(player).describe()

    private fun of(player: PlayerEntity): PlayerProgress = KEY.get(player).progress

    /** Applies a change on the server and sends the result to the player's client. */
    private fun <T> change(player: PlayerEntity, action: (PlayerProgress) -> T): T {
        val result = action(of(player))
        if (!player.world.isClient) KEY.sync(player)
        return result
    }

    /** The component itself. Saved and synced as the same NBT. */
    class ProgressComponent(private val owner: PlayerEntity) : AutoSyncedComponent {

        val progress = PlayerProgress()

        override fun shouldSyncWith(player: ServerPlayerEntity): Boolean = player === owner

        override fun readFromNbt(tag: NbtCompound) {
            progress.clear()
            val pieces = tag.getCompound(PIECES)
            for (level in pieces.keys) {
                strings(pieces, level).forEach { progress.addPiece(level, it) }
            }
            strings(tag, NOTES).forEach { progress.markNoteRead(it) }
            strings(tag, SCENES).forEach { progress.markSceneSeen(it) }
            progress.level3Books = tag.getInt(BOOKS)
            progress.checkpoint = if (tag.contains(CHECKPOINT, NbtElement.STRING_TYPE.toInt())) tag.getString(CHECKPOINT) else null
        }

        override fun writeToNbt(tag: NbtCompound) {
            val pieces = NbtCompound()
            progress.pieces.forEach { (level, ids) -> pieces.put(level, list(ids)) }
            tag.put(PIECES, pieces)
            tag.put(NOTES, list(progress.notes))
            tag.put(SCENES, list(progress.scenes))
            tag.putInt(BOOKS, progress.level3Books)
            progress.checkpoint?.let { tag.putString(CHECKPOINT, it) }
        }

        private fun strings(tag: NbtCompound, key: String): List<String> {
            val list = tag.getList(key, NbtElement.STRING_TYPE.toInt())
            return (0 until list.size).map { list.getString(it) }
        }

        private fun list(values: Collection<String>): NbtList {
            val list = NbtList()
            values.forEach { list.add(NbtString.of(it)) }
            return list
        }

        private companion object {
            const val PIECES = "pieces"
            const val NOTES = "notes"
            const val SCENES = "scenes"
            const val BOOKS = "level3_books"
            const val CHECKPOINT = "checkpoint"
        }
    }
}
