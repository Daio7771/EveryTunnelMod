package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Loot
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Note
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.DistortionHooks
import daio7771.everytunnel.platform.LootBlock
import daio7771.everytunnel.platform.LootSpawner
import daio7771.everytunnel.platform.NoteHooks
import net.minecraft.block.Blocks
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * What lies about in Chapter 2's Level 2, as `level.json` lists it (`hunters`, `loot`, `notes`): the Hunters, the survival kit and the
 * rest of the loot, and the notes on the boards of the map. Hung on [Ch2L2]'s start (entering, a death, CONTINUE, `reset`):
 *
 * - **Hunters:** every group of the level's own ([LEVEL_ID], so the base game's posts never touch them) is taken away and put at its
 *   anchor again, whole, for a player whose state is no later than the group's `until`. They are Level 5's ([DistortionHooks.putIn]).
 * - **Loot:** each piece not yet taken lies at its place (`LootSpawner.LOOT`'s blocks); one taken (the player's counter `loot.<id>`,
 *   set on picking it up) does not. A death keeps the pockets, so keeps the counters; CONTINUE gives back the pockets and counters of
 *   the checkpoint, so what was picked up since lies there again.
 * - **Notes:** a board of the map at a note's anchor opens that note on paper ([NoteHooks.Source]); the counter `read.<id>` remembers it.
 *
 * Server thread only.
 */
object Ch2L2Spawns {

    /** The id the level's Hunters carry (`HunterEntity.levelId`), apart from the base game's levels. */
    const val LEVEL_ID = "chapter2_level2"

    private val data get() = Ch2L2DataLoader.data

    fun register() {
        Ch2L2.onStart { player, state -> started(player, state) }
        LootSpawner.addTakenListener { player, pos, _ -> taken(player, pos) }
        NoteHooks.addSource { player, pos -> noteAt(player, pos) }
    }

    private fun started(player: ServerPlayerEntity, state: Ch2L2State) {
        val world = levelWorld(player.server) ?: return
        putHunters(world, state)
        placeLoot(player, world)
    }

    // --- Hunters -----------------------------------------------------------------------------------

    /** The level's Hunters put back at their anchors, those groups whose `until` is not before [state] (every one if it is null). */
    fun putHunters(world: ServerWorld, state: Ch2L2State?) {
        DistortionHooks.discard(world, LEVEL_ID)
        for (group in data.level.hunters) {
            if (state != null && state > group.until) continue
            val anchor = data.anchors.point(group.anchor) ?: continue
            val yaw = group.yaw ?: anchor.yaw
            DistortionHooks.putIn(world, DistortionHooks.HUNTERS_TAG, Position(anchor.position.x, anchor.position.y, anchor.position.z), yaw, LEVEL_ID)
        }
    }

    /** The level's Hunters gone (the level is being taken out). */
    fun clearHunters(server: MinecraftServer) {
        levelWorld(server)?.let { DistortionHooks.discard(it, LEVEL_ID) }
    }

    // --- loot --------------------------------------------------------------------------------------

    /** Every piece of loot the player has not taken at its place, and none that they have. */
    fun placeLoot(player: ServerPlayerEntity, world: ServerWorld) {
        for (loot in data.level.loot) {
            val pos = cellOf(loot) ?: continue
            val there = world.getBlockState(pos)
            if (Ch2Progress.ch2L2Counter(player, loot.takenKey) > 0) {
                if (there.block is LootBlock) world.setBlockState(pos, Blocks.AIR.defaultState)
                continue
            }
            val block = LootSpawner.LOOT[loot.item]?.invoke()
            if (block == null) {
                EveryTunnel.LOGGER.warn("Chapter 2 Level 2: loot {} is of the unknown kind \"{}\" ({})", loot.id, loot.item, LootSpawner.LOOT.keys.sorted())
                continue
            }
            if (there.block is LootBlock || there.isAir || there.isReplaceable) {
                world.setBlockState(
                    pos,
                    block.defaultState.with(LootBlock.FACING, Direction.fromRotation(loot.yaw.toDouble())).with(LootBlock.WATERLOGGED, false),
                )
            } else {
                EveryTunnel.LOGGER.warn("Chapter 2 Level 2: loot {} not placed: {} is taken by {}", loot.id, pos, there)
            }
        }
    }

    /** All the loot as if none had been taken: the player's counters forgotten, every piece back. Null if it worked, else why not. */
    fun restock(player: ServerPlayerEntity): String? {
        val world = levelWorld(player.server) ?: return "The anchors' dimension ${data.anchors.dimension} is not there."
        Ch2Progress.replaceCh2L2Counters(player, Ch2Progress.ch2L2Counters(player).filterKeys { !it.startsWith(Ch2L2Loot.TAKEN_PREFIX) })
        placeLoot(player, world)
        return null
    }

    private fun taken(player: ServerPlayerEntity, pos: BlockPos) {
        if (!Ch2L2.active(player) || player.serverWorld.registryKey.value.toString() != data.anchors.dimension) return
        val loot = data.level.loot.firstOrNull { cellOf(it) == pos } ?: return
        Ch2Progress.setCh2L2Counter(player, loot.takenKey, 1)
    }

    // --- notes -------------------------------------------------------------------------------------

    private fun noteAt(player: ServerPlayerEntity, pos: BlockPos): String? {
        if (player.serverWorld.registryKey.value.toString() != data.anchors.dimension) return null
        val note: Ch2L2Note = data.level.notes.firstOrNull { anchorCell(it.anchor) == pos } ?: return null
        if (Ch2L2.active(player)) Ch2Progress.setCh2L2Counter(player, note.readKey, 1)
        return note.noteId
    }

    // --- helpers -----------------------------------------------------------------------------------

    private fun anchorCell(id: String): BlockPos? =
        data.anchors.point(id)?.position?.let { BlockPos.ofFloored(it.x, it.y, it.z) }

    private fun cellOf(loot: Ch2L2Loot): BlockPos? = anchorCell(loot.anchor)?.add(loot.dx, loot.dy, loot.dz)

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
}
