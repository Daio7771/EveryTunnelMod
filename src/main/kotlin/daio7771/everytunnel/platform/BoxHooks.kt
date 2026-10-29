package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.WorldRules
import net.minecraft.item.Item
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.random.Random

/**
 * Level 3's box puzzle, in worlds with levels.
 *
 * - **A new game** ([newGame]) puts [BOXES] purple boxes ([PurpleBoxBlock]) at as many of the
 *   level's spawn points tagged [SPOT_TAG] (about 25: on floors and furniture of the rooms and
 *   the ring), drawn at random, and empties the shelf at the spawn point tagged [SHELF_TAG]
 *   ([BoxShelfBlock], in Storage).
 * - **Picking one up** ([pickUp]): right-click; it goes into the hand, carried by the rules of
 *   [CarryHooks]: one thing at a time, kept in the hand, never dropped, no sprinting; hiding in a
 *   locker with it is fine.
 * - **Putting it on the shelf** ([useShelf]): right-click the place pointed at; a filled one
 *   takes nothing. "3 / 10" above the hotbar, and the Observer is alerted to the shelf
 *   ([alertObserver], [ObserverHooks]; with a distant sound, `event.observer.alert`).
 *   With all ten up, everyone there earns "Master of the Boxes" and the books appear
 *   ([completed], [BookHooks]).
 * - **Dying** puts a carried box back where it was picked up ([CarryHooks]); the shelf keeps
 *   its boxes. Only a new game starts it over.
 */
object BoxHooks {

    const val SPOT_TAG = "box_spot"
    const val SHELF_TAG = "box_shelf"
    const val BOXES = BoxShelfBlock.SLOTS

    private val boxItem: Item get() = ModBlocks.PURPLE_BOX.asItem()

    /** [player] right-clicked the box at [pos]: into the hand ([CarryHooks]). */
    fun pickUp(player: ServerPlayerEntity, pos: BlockPos): Boolean {
        if (!player.serverWorld.getBlockState(pos).isOf(ModBlocks.PURPLE_BOX)) return false
        return CarryHooks.take(player, pos, boxItem, ModSounds.CARDBOARD.impact)
    }

    /** [player] right-clicked [slot] of the shelf whose foot is at [foot]. True if a box went up. */
    fun useShelf(player: ServerPlayerEntity, world: ServerWorld, foot: BlockPos, slot: Int): Boolean {
        if (!WorldRules.active || !CarryHooks.isCarrying(player, boxItem)) return false
        val shelf = world.getBlockEntity(foot) as? BoxShelfBlockEntity ?: return false
        if (shelf.isFilled(slot)) return true
        CarryHooks.release(player)
        shelf.set(slot, true)
        val at = BoxShelfBlock.slotCentre(foot, world.getBlockState(foot).get(BoxShelfBlock.FACING), slot)
        world.playSound(null, at.x, at.y, at.z, ModSounds.CARDBOARD.impact, SoundCategory.BLOCKS, 1f, 0.9f)
        player.sendMessage(Text.translatable("message.every_tunnel.boxes_placed", shelf.count, BOXES), true)
        alertObserver(world, foot)
        if (shelf.count >= BOXES) completed(world, foot)
        return true
    }

    /**
     * A box was put on the shelf at [foot]: the Observer comes running ([ObserverHooks]); far off,
     * something is heard noticing (`event.observer.alert`). He comes to the place in front of it.
     */
    private fun alertObserver(world: ServerWorld, foot: BlockPos) {
        val facing = world.getBlockState(foot).get(BoxShelfBlock.FACING)
        ObserverHooks.alert(world, foot.offset(facing, 2))
        for (player in world.players) player.playSound(ModSounds.OBSERVER_ALERT, SoundCategory.HOSTILE, 1f, 1f)
    }

    /** All ten on the shelf: the achievement, and the books appear ([BookHooks]). */
    private fun completed(world: ServerWorld, foot: BlockPos) {
        EveryTunnel.LOGGER.info("All {} boxes on the shelf at {}: the books appear", BOXES, foot)
        AchievementHooks.puzzleSolved(world, Achievement.BOXES)
        LevelManager.levelAt(world.registryKey.value.toString(), foot.x + 0.5, foot.y + 0.5, foot.z + 0.5)?.let { BookHooks.appear(world, it) }
    }

    /** All the boxes of [level] are on its shelf (false for a level without one). */
    fun shelfFull(world: ServerWorld, level: LevelDefinition): Boolean {
        val foot = shelfFoot(level) ?: return false
        return ((world.getBlockEntity(foot) as? BoxShelfBlockEntity)?.count ?: 0) >= BOXES
    }

    private fun spots(level: LevelDefinition): List<BlockPos> =
        level.spawnPoints.filter { SPOT_TAG in it.tags }.map { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun shelfFoot(level: LevelDefinition): BlockPos? =
        level.spawnPoints.firstOrNull { SHELF_TAG in it.tags }?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun world(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    /** A new game: the boxes off every spot, ten put back at random, the shelf emptied. */
    fun newGame(server: MinecraftServer) {
        for (level in LevelManager.all()) {
            val spots = spots(level)
            val foot = shelfFoot(level)
            if (spots.isEmpty() && foot == null) continue
            val world = world(server, level) ?: continue
            for (pos in spots) if (world.getBlockState(pos).isOf(ModBlocks.PURPLE_BOX)) world.removeBlock(pos, false)
            val chosen = spots.shuffled(Random.Default).take(BOXES)
            for (pos in chosen) {
                if (!world.getBlockState(pos).isAir) {
                    EveryTunnel.LOGGER.warn("Box spot {} in {} is not free: no box there", pos, level.id)
                    continue
                }
                val facing = Direction.Type.HORIZONTAL.random(world.random)
                world.setBlockState(pos, ModBlocks.PURPLE_BOX.defaultState.with(PropBlock.FACING, facing))
            }
            foot?.let { (world.getBlockEntity(it) as? BoxShelfBlockEntity)?.clear() }
            if (spots.size < BOXES) EveryTunnel.LOGGER.warn("{} has only {} box spots for {} boxes", level.id, spots.size, BOXES)
            EveryTunnel.LOGGER.info("Boxes for {}: {}", level.id, chosen.joinToString(" ") { "(${it.x} ${it.y} ${it.z})" })
        }
    }

    /** For `/everytunnel boxes`: the boxes lying about and the shelf's count, per level with a shelf. */
    fun report(server: MinecraftServer): List<String> = LevelManager.all().mapNotNull { level ->
        val foot = shelfFoot(level) ?: return@mapNotNull null
        val world = world(server, level) ?: return@mapNotNull null
        val lying = spots(level).filter { world.getBlockState(it).isOf(ModBlocks.PURPLE_BOX) }
        val count = (world.getBlockEntity(foot) as? BoxShelfBlockEntity)?.count ?: 0
        "${level.id}: $count / $BOXES on the shelf at (${foot.x} ${foot.y} ${foot.z}); lying at " +
            lying.joinToString(" ") { "(${it.x} ${it.y} ${it.z})" }.ifEmpty { "nowhere" }
    }

    /** For `/everytunnel boxes fill`: all but one box on the shelf, the rest taken away (to test the last one). */
    fun fillAllButOne(server: MinecraftServer): Int {
        var done = 0
        for (level in LevelManager.all()) {
            val foot = shelfFoot(level) ?: continue
            val world = world(server, level) ?: continue
            val shelf = world.getBlockEntity(foot) as? BoxShelfBlockEntity ?: continue
            val lying = spots(level).filter { world.getBlockState(it).isOf(ModBlocks.PURPLE_BOX) }
            for (pos in lying.drop(1)) world.removeBlock(pos, false)
            val keep = if (lying.isEmpty()) 0 else 1
            var placed = shelf.count
            for (slot in 0 until BoxShelfBlock.SLOTS) {
                if (placed >= BOXES - keep) break
                if (!shelf.isFilled(slot)) {
                    shelf.set(slot, true)
                    placed++
                }
            }
            done++
        }
        return done
    }
}
