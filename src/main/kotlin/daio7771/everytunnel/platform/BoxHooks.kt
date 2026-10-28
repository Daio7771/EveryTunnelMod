package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
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
import java.util.UUID
import kotlin.random.Random

/**
 * Level 3's box puzzle, in worlds with levels.
 *
 * - **A new game** ([newGame]) puts [BOXES] purple boxes ([PurpleBoxBlock]) at as many of the
 *   level's spawn points tagged [SPOT_TAG] (about 25: on floors and furniture of the rooms and
 *   the ring), drawn at random, and empties the shelf at the spawn point tagged [SHELF_TAG]
 *   ([BoxShelfBlock], in Storage).
 * - **Picking one up** ([pickUp]): right-click; it goes into the hand (the selected hotbar slot,
 *   whatever was there moves aside). One at a time. While carried it stays in the hand
 *   ([tick] moves it back if the slot changes), it cannot be dropped (thrown out, it comes
 *   straight back) and the player cannot sprint (here and `client.ClientResources`). Hiding
 *   in a locker with it is fine.
 * - **Putting it on the shelf** ([useShelf]): right-click the place pointed at; a filled one
 *   takes nothing. "3 / 10" above the hotbar, and the observant guard is alerted to the shelf
 *   ([alertObserver]: until he exists, a log line and a distant sound, `event.observer.alert`).
 *   With all ten up, everyone there earns "Master of the Boxes" and the books will appear
 *   (step 3, [completed]).
 * - **Dying** ([died]) puts a carried box back where it was picked up; the shelf keeps its
 *   boxes. Only a new game starts it over.
 */
object BoxHooks {

    const val SPOT_TAG = "box_spot"
    const val SHELF_TAG = "box_shelf"
    const val BOXES = BoxShelfBlock.SLOTS

    /** A box in someone's hands, and where it was picked up from. */
    private class Carried(val dimension: String, val pos: BlockPos, val facing: Direction)

    // Server thread only.
    private val carried = mutableMapOf<UUID, Carried>()

    private val boxItem: Item get() = ModBlocks.PURPLE_BOX.asItem()

    fun register() {
        ServerLifecycleEvents.SERVER_STOPPED.register { carried.clear() }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        // A carried box thrown out (Q, or dragged out of the inventory) comes straight back.
        // A box never lies about as a dropped item: it goes back to whoever carried it (the
        // thrower, or the nearest carrier who no longer has it), or else [tick] puts it back
        // where it was picked up.
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            val item = entity as? ItemEntity ?: return@register
            if (!WorldRules.active || !item.stack.isOf(boxItem)) return@register
            val stack = item.stack.copy()
            item.discard()
            val carrier = (item.owner as? ServerPlayerEntity)?.takeIf { it.uuid in carried && !isCarrying(it) }
                ?: world.players.filter { it.uuid in carried && !isCarrying(it) }.minByOrNull { it.squaredDistanceTo(item) }
                ?: return@register
            val inventory = carrier.inventory
            if (inventory.main[inventory.selectedSlot].isEmpty) inventory.main[inventory.selectedSlot] = stack else inventory.insertStack(stack)
            inventory.markDirty()
        }
    }

    fun isCarrying(player: ServerPlayerEntity): Boolean = player.inventory.containsAny { it.isOf(boxItem) }

    private fun tick(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            if (!isCarrying(player)) {
                // The box left the inventory some other way (a command, Creative): back to its place.
                if (player.uuid in carried && !DeathHooks.isDying(player)) putBack(server, player)
                continue
            }
            if (player.isSprinting) player.isSprinting = false
            keepInHand(player)
        }
    }

    /** The box always in the selected hotbar slot: if the slot changed, the box moves to it. */
    private fun keepInHand(player: ServerPlayerEntity) {
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        if (inventory.main[selected].isOf(boxItem)) return
        val offHand = inventory.offHand
        if (offHand[0].isOf(boxItem)) {
            val box = offHand[0]
            offHand[0] = inventory.main[selected]
            inventory.main[selected] = box
        } else {
            val at = inventory.main.indexOfFirst { it.isOf(boxItem) }
            if (at < 0) return
            val box = inventory.main[at]
            inventory.main[at] = inventory.main[selected]
            inventory.main[selected] = box
        }
        inventory.markDirty()
    }

    /** [player] right-clicked the box at [pos]. True if it was taken (or the click was used). */
    fun pickUp(player: ServerPlayerEntity, pos: BlockPos): Boolean {
        if (!WorldRules.active || player.isSpectator || DeathHooks.isDying(player)) return false
        if (isCarrying(player)) {
            player.sendMessage(Text.translatable("message.every_tunnel.box_hands_full"), true)
            return true
        }
        val world = player.serverWorld
        val state = world.getBlockState(pos)
        if (!state.isOf(ModBlocks.PURPLE_BOX)) return false
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        if (!inventory.main[selected].isEmpty) {
            val free = inventory.emptySlot
            if (free < 0) {
                player.sendMessage(Text.translatable("message.every_tunnel.box_no_room"), true)
                return true
            }
            inventory.main[free] = inventory.main[selected]
        }
        inventory.main[selected] = ItemStack(boxItem)
        inventory.markDirty()
        world.removeBlock(pos, false)
        world.playSound(null, pos, ModSounds.CARDBOARD.impact, SoundCategory.BLOCKS, 0.8f, 1.1f)
        carried[player.uuid] = Carried(world.registryKey.value.toString(), pos.toImmutable(), state.get(PropBlock.FACING))
        return true
    }

    /** [player] right-clicked [slot] of the shelf whose foot is at [foot]. True if a box went up. */
    fun useShelf(player: ServerPlayerEntity, world: ServerWorld, foot: BlockPos, slot: Int): Boolean {
        if (!WorldRules.active || !isCarrying(player)) return false
        val shelf = world.getBlockEntity(foot) as? BoxShelfBlockEntity ?: return false
        if (shelf.isFilled(slot)) return true
        removeBoxes(player)
        carried.remove(player.uuid)
        shelf.set(slot, true)
        val at = BoxShelfBlock.slotCentre(foot, world.getBlockState(foot).get(BoxShelfBlock.FACING), slot)
        world.playSound(null, at.x, at.y, at.z, ModSounds.CARDBOARD.impact, SoundCategory.BLOCKS, 1f, 0.9f)
        player.sendMessage(Text.translatable("message.every_tunnel.boxes_placed", shelf.count, BOXES), true)
        alertObserver(world, foot)
        if (shelf.count >= BOXES) completed(world, foot)
        return true
    }

    /**
     * A box was put on the shelf at [foot]: the observant guard hears it and walks there from
     * wherever he is. He does not exist yet: a log line, and a distant sound for the players.
     */
    private fun alertObserver(world: ServerWorld, foot: BlockPos) {
        EveryTunnel.LOGGER.info("[observer] a box went on the shelf at {}: the observant guard would walk there", foot)
        for (player in world.players) player.playSound(ModSounds.OBSERVER_ALERT, SoundCategory.HOSTILE, 1f, 1f)
    }

    /** All ten on the shelf: the achievement, and (step 3) the books appear. */
    private fun completed(world: ServerWorld, foot: BlockPos) {
        EveryTunnel.LOGGER.info("All {} boxes on the shelf at {}: the books would appear now", BOXES, foot)
        AchievementHooks.puzzleSolved(world, Achievement.BOXES)
    }

    /** [player] is dying: a carried box goes back where it was picked up. */
    fun died(player: ServerPlayerEntity) {
        if (player.uuid !in carried && !isCarrying(player)) return
        removeBoxes(player)
        putBack(player.server, player)
    }

    private fun removeBoxes(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (list in listOf(inventory.main, inventory.offHand)) {
            for (i in list.indices) if (list[i].isOf(boxItem)) list[i] = ItemStack.EMPTY
        }
        inventory.markDirty()
    }

    private fun putBack(server: MinecraftServer, player: ServerPlayerEntity) {
        val box = carried.remove(player.uuid) ?: return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(box.dimension))) ?: return
        if (world.getBlockState(box.pos).isAir) {
            world.setBlockState(box.pos, ModBlocks.PURPLE_BOX.defaultState.with(PropBlock.FACING, box.facing))
        }
    }

    private fun spots(level: LevelDefinition): List<BlockPos> =
        level.spawnPoints.filter { SPOT_TAG in it.tags }.map { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun shelfFoot(level: LevelDefinition): BlockPos? =
        level.spawnPoints.firstOrNull { SHELF_TAG in it.tags }?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun world(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    /** A new game: the boxes off every spot, ten put back at random, the shelf emptied. */
    fun newGame(server: MinecraftServer) {
        carried.clear()
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

    /** The map was rebuilt: no box is in anyone's hands any more (a new game follows). */
    fun levelsRebuilt() {
        carried.clear()
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
