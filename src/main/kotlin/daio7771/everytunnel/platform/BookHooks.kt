package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.TrapdoorBlock
import net.minecraft.item.Item
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.random.Random

/**
 * Level 3's book puzzle, after the boxes ([BoxHooks]), in worlds with levels.
 *
 * - **The books appear** ([appear]) once the ten boxes are on Storage's shelf: five office
 *   books ([OfficeBookBlock]), in silence, at spots picked at random among the level's spawn
 *   points tagged [SPOT_TAG] (in the library: on book carts, on the floor of the aisles, on the
 *   card catalogue).
 * - **Carried** like the boxes ([CarryHooks]): one thing at a time, kept in the hand, never
 *   dropped, no sprinting; dying puts it back where it was found. They alert no one.
 * - **Handed in** ([deliver]) by right-clicking the librarian's desk round the hatch (library
 *   counters; or a book already on it): the book lies at the next free one of the five places
 *   on it (spawn points tagged [RETURN_TAG]), and "2 / 5" shows above the hotbar.
 * - **The fifth** opens the bunker hatch by itself: the steel trapdoors in the zones tagged
 *   [HATCH_TAG] swing open with a heavy latch (`block.hatch.open`), and everyone there earns
 *   "Shhh!".
 *
 * Only a new game ([newGame]) takes the books away and shuts the hatch again (as the map's
 * rebuild also builds it).
 */
object BookHooks {

    const val SPOT_TAG = "book_spot"
    const val RETURN_TAG = "book_return"
    const val HATCH_TAG = "book_hatch"

    private val bookItem: Item get() = ModBlocks.OFFICE_BOOK.asItem()

    fun register() {
        // The counter itself is plain furniture: a click on it with a book hands the book in.
        UseBlockCallback.EVENT.register { player, world, _, hit ->
            if (world.isClient || player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!world.getBlockState(hit.blockPos).isOf(ModBlocks.LIBRARY_COUNTER)) return@register ActionResult.PASS
            if (deliver(player, world as ServerWorld, hit.blockPos)) ActionResult.SUCCESS else ActionResult.PASS
        }
    }

    private fun positions(level: LevelDefinition, tag: String): List<BlockPos> =
        level.spawnPoints.filter { tag in it.tags }.sortedBy { it.id }.map { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun world(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    private fun levelAt(world: ServerWorld, pos: BlockPos): LevelDefinition? =
        LevelManager.levelAt(world.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)

    /** The boxes are done: as many books as the desk has places, at spots picked at random; where they lie. */
    fun appear(world: ServerWorld, level: LevelDefinition): List<BlockPos> {
        val count = positions(level, RETURN_TAG).size
        val chosen = positions(level, SPOT_TAG).filter { world.getBlockState(it).isAir }.shuffled(Random.Default).take(count)
        if (chosen.size < count) EveryTunnel.LOGGER.warn("{} has only {} free book spots for {} books", level.id, chosen.size, count)
        for (pos in chosen) {
            world.setBlockState(pos, ModBlocks.OFFICE_BOOK.defaultState.with(PropBlock.FACING, Direction.Type.HORIZONTAL.random(world.random)))
        }
        EveryTunnel.LOGGER.info("The books of {} appear: {}", level.id, chosen.joinToString(" ") { "(${it.x} ${it.y} ${it.z})" })
        return chosen
    }

    /** [player] right-clicked the book lying at [pos]: into the hand. */
    fun pickUp(player: ServerPlayerEntity, pos: BlockPos): Boolean {
        val state = player.serverWorld.getBlockState(pos)
        if (!state.isOf(ModBlocks.OFFICE_BOOK) || state.get(OfficeBookBlock.DELIVERED)) return false
        return CarryHooks.take(player, pos, bookItem, SoundEvents.ITEM_BOOK_PAGE_TURN)
    }

    /** [player] right-clicked the counter (or a book on it) at [pos]: a carried book is handed in. */
    fun deliver(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): Boolean {
        if (!WorldRules.active || !CarryHooks.isCarrying(player, bookItem)) return false
        val level = levelAt(world, pos) ?: return false
        val places = positions(level, RETURN_TAG)
        if (places.none { it.isWithinDistance(pos, 8.0) }) return false
        val free = places.firstOrNull { world.getBlockState(it).isAir } ?: return true
        CarryHooks.release(player)
        val facing = world.getBlockState(free.down()).takeIf { it.contains(PropBlock.FACING) }?.get(PropBlock.FACING) ?: Direction.NORTH
        world.setBlockState(free, ModBlocks.OFFICE_BOOK.defaultState.with(PropBlock.FACING, facing).with(OfficeBookBlock.DELIVERED, true))
        world.playSound(null, free, SoundEvents.ITEM_BOOK_PUT, SoundCategory.BLOCKS, 1f, 1f)
        val returned = places.count { world.getBlockState(it).isOf(ModBlocks.OFFICE_BOOK) }
        player.sendMessage(Text.translatable("message.every_tunnel.books_returned", returned, places.size), true)
        if (returned >= places.size) completed(world, level)
        return true
    }

    /** The book phase of [level]: the boxes are done and not every book is back yet. */
    fun booksOut(world: ServerWorld, level: LevelDefinition): Boolean {
        val returns = positions(level, RETURN_TAG)
        if (returns.isEmpty() || !BoxHooks.shelfFull(world, level)) return false
        return returns.count { world.getBlockState(it).isOf(ModBlocks.OFFICE_BOOK) } < returns.size
    }

    /** All five back: the hatch opens, and the achievement. */
    private fun completed(world: ServerWorld, level: LevelDefinition) {
        setHatch(world, level, open = true)
        AchievementHooks.puzzleSolved(world, Achievement.BOOKS)
        EveryTunnel.LOGGER.info("All the books of {} are back: the hatch opens", level.id)
    }

    private fun setHatch(world: ServerWorld, level: LevelDefinition, open: Boolean) {
        val zones = level.zones.filter { HATCH_TAG in it.tags }
        for (zone in zones) {
            val box = zone.box
            for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                val state = world.getBlockState(pos)
                if (state.block is TrapdoorBlock && state.get(TrapdoorBlock.OPEN) != open) {
                    world.setBlockState(pos, state.with(TrapdoorBlock.OPEN, open))
                }
            }
            if (open) {
                world.playSound(null, (box.minX + box.maxX + 1) / 2.0, box.minY + 0.5, (box.minZ + box.maxZ + 1) / 2.0,
                    ModSounds.HATCH_OPEN, SoundCategory.BLOCKS, 1f, 1f)
            }
        }
    }

    /** A new game: no book on the cabinets or the counter, the hatch shut. */
    fun newGame(server: MinecraftServer) {
        for (level in LevelManager.all()) {
            if (positions(level, SPOT_TAG).isEmpty() && positions(level, RETURN_TAG).isEmpty()) continue
            val world = world(server, level) ?: continue
            clear(world, level)
        }
    }

    /** For `/everytunnel books`: where the books are, per level with a counter. */
    fun report(server: MinecraftServer): List<String> = LevelManager.all().mapNotNull { level ->
        val returns = positions(level, RETURN_TAG)
        if (returns.isEmpty()) return@mapNotNull null
        val world = world(server, level) ?: return@mapNotNull null
        val back = returns.count { world.getBlockState(it).isOf(ModBlocks.OFFICE_BOOK) }
        val lying = positions(level, SPOT_TAG).filter { world.getBlockState(it).isOf(ModBlocks.OFFICE_BOOK) }
        "${level.id}: $back / ${returns.size} on the desk; lying at " +
            lying.joinToString(" ") { "(${it.x} ${it.y} ${it.z})" }.ifEmpty { "nowhere" }
    }

    /** For `/everytunnel books fill`: the boxes done, the books out, all but one already on the desk (to test the last). */
    fun fillAllButOne(server: MinecraftServer): Int {
        var done = 0
        for (level in LevelManager.all()) {
            val returns = positions(level, RETURN_TAG)
            if (returns.isEmpty()) continue
            val world = world(server, level) ?: continue
            clear(world, level)
            BoxHooks.fillShelf(world, level)
            val lying = appear(world, level)
            for ((i, place) in returns.dropLast(1).withIndex()) {
                lying.getOrNull(i)?.let { world.removeBlock(it, false) }
                world.setBlockState(place, ModBlocks.OFFICE_BOOK.defaultState.with(OfficeBookBlock.DELIVERED, true))
            }
            done++
        }
        return done
    }

    private fun clear(world: ServerWorld, level: LevelDefinition) {
        for (pos in positions(level, SPOT_TAG) + positions(level, RETURN_TAG)) {
            if (world.getBlockState(pos).isOf(ModBlocks.OFFICE_BOOK)) world.removeBlock(pos, false)
        }
        setHatch(world, level, open = false)
    }
}
